package com.social.message.service.saga;

import com.social.message.domain.ChannelEntity;
import com.social.message.domain.ChannelMemberEntity;
import com.social.message.kafka.message.in.MatchCreatedRequest;
import com.social.message.kafka.message.in.MatchUnmatchedRequest;
import com.social.message.outbox.OutboxEventWriter;
import com.social.message.repository.channel.ChannelRepository;
import com.social.message.repository.channelmember.ChannelMemberRepository;
import com.social.message.service.channelmember.ChannelMemberCacheService;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 매칭 Saga 의 message-service 쪽 담당 — 채널의 소유자는 여기이므로 채널 생성/폐쇄는 이 서비스만 한다.
 *
 * <pre>
 * match-created   → DIRECT 채널 + 멤버 생성 → outbox(CHANNEL_CREATED) → channel-created
 * match-unmatched → 해당 matchId 채널 status=CLOSED
 * </pre>
 *
 * 두 흐름 모두 at-least-once 라서 <b>멱등</b>이어야 한다. 멱등 키는 {@code channel.match_id}(UNIQUE).
 */
@Slf4j
@RequiredArgsConstructor
@Service
public class MatchSagaService {

    private final ChannelRepository channelRepository;
    private final ChannelMemberRepository channelMemberRepository;
    private final ChannelMemberCacheService channelMemberCacheService;
    private final OutboxEventWriter outboxEventWriter;

    /**
     * 매칭 성사 → 두 사람 전용 DIRECT 채널 1개 생성 + CHANNEL_CREATED 발행 (한 트랜잭션).
     *
     * <p>멱등 2중 방어:
     * <ol>
     *   <li>선조회 — 이미 이 matchId 채널이 있으면 no-op (재수신의 대부분)</li>
     *   <li>UNIQUE 충돌 — 선조회와 INSERT 사이 동시 재수신(두 파티션 스레드)은 DB 가 막고, 여기서 삼킨다</li>
     * </ol>
     * 1번만 있으면 조회-후-삽입 race 에 채널이 2개 생길 수 있다.
     */
    @Transactional
    public void onMatchCreated(MatchCreatedRequest request) {
        UUID matchId = request.matchId();

        if (channelRepository.findByMatchId(matchId).isPresent()) {
            log.info("[Saga] DIRECT 채널 이미 존재 — 재수신 무시: matchId={}", matchId);
            return;
        }

        List<UUID> members = resolveMembers(request);

        try {
            ChannelEntity channel = channelRepository.saveAndFlush(ChannelEntity.direct(matchId));

            members.forEach(userId ->
                channelMemberRepository.save(ChannelMemberEntity.create(channel.getChannelId(), userId)));

            // 채널 멤버 캐시는 여기서 만들지 않는다 — 첫 fanout 때 DB 에서 채워진다(rebuild-on-miss).
            // 다만 (있을 수 없지만) 낡은 키가 남아 있으면 유령 멤버가 되므로 무효화만 해 둔다.
            channelMemberCacheService.invalidate(channel.getChannelId());

            outboxEventWriter.writeChannelCreated(matchId, channel.getChannelId(), members);

            log.info("[Saga] DIRECT 채널 생성: matchId={}, channelId={}, members={}",
                matchId, channel.getChannelId(), members.size());
        } catch (DataIntegrityViolationException e) {
            // uk_channel_match_id 충돌 = 다른 스레드가 방금 만들었다(그쪽이 CHANNEL_CREATED 를 발행한다).
            // 삼키지 않고 다시 던지는 이유: 제약 위반 flush 로 Hibernate 세션이 이미 rollback-only 라
            // 정상 리턴하면 커밋 시점에 UnexpectedRollbackException 이 난다.
            // 던지면 이 트랜잭션만 롤백되고, 재배달 때 위 선조회에서 no-op 으로 걸러진다.
            log.info("[Saga] DIRECT 채널 동시 생성 충돌 — 재배달 시 멱등 처리됨: matchId={}", matchId);
            throw e;
        }
    }

    /**
     * 언매치 → 그 매칭의 DIRECT 채널을 닫는다(멱등). CLOSED 채널은 신규 메시지 전송이 거부된다.
     * 채널이 아직 없으면(백필 전 언매치 같은 경계) 경고만 남기고 넘어간다 — 채널이 없으니 닫을 것도 없다.
     */
    @Transactional
    public void onMatchUnmatched(MatchUnmatchedRequest request) {
        Optional<ChannelEntity> found = channelRepository.findByMatchId(request.matchId());
        if (found.isEmpty()) {
            log.warn("[Saga] 언매치 대상 채널 없음(무시): matchId={}", request.matchId());
            return;
        }

        ChannelEntity channel = found.get();
        if (channel.isClosed()) {
            return;   // 이미 닫힘 — no-op
        }
        channel.close();
        log.info("[Saga] 채널 CLOSED: matchId={}, channelId={}", request.matchId(), channel.getChannelId());
    }

    /** recipients 가 비어 있어도 lo/hi 로 멤버를 복원한다(페이로드 방어). */
    private List<UUID> resolveMembers(MatchCreatedRequest request) {
        if (request.recipients() != null && !request.recipients().isEmpty()) {
            return request.recipients().stream().distinct().toList();
        }
        return List.of(request.lo(), request.hi());
    }
}
