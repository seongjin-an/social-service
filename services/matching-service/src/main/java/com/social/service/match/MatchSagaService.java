package com.social.service.match;

import com.social.domain.match.MatchEntity;
import com.social.kafka.message.in.ChannelCreatedRequest;
import com.social.repository.match.MatchRepository;
import com.social.service.outbox.OutboxEventWriter;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 매칭 Saga 의 matching 쪽 마지막 단계 — 채널 id 를 매칭에 이어 붙이고, 그때서야 사용자 알림을 발사한다.
 *
 * <pre>
 * channel-created 수신 → matches.channel_id 백필 → outbox(MATCH_FANOUT) → match-fanout
 * </pre>
 *
 * <p><b>멱등</b>: channel_id 가 이미 채워져 있으면 no-op. 이게 없으면 재배달마다 알림이 중복 발사된다.
 */
@Slf4j
@RequiredArgsConstructor
@Service
public class MatchSagaService {

    private final MatchRepository matchRepository;
    private final OutboxEventWriter outboxEventWriter;

    @Transactional
    public void backfillChannel(ChannelCreatedRequest request) {
        UUID matchId = request.matchId();

        Optional<MatchEntity> found = matchRepository.findById(matchId);
        if (found.isEmpty()) {
            // matching 의 matches 가 없는데 채널이 생겼다? = 데이터 정합성 사고. 재시도해도 안 낫는다.
            // 예외로 올려 컨슈머를 무한 재시도에 빠뜨리는 대신 로그로 남기고 흘린다(ack).
            log.error("[Saga] 백필 대상 매칭 없음(스킵): matchId={}, channelId={}",
                matchId, request.channelId());
            return;
        }

        MatchEntity match = found.get();
        if (match.getChannelId() != null) {
            log.info("[Saga] channel_id 이미 백필됨 — 재수신 무시: matchId={}", matchId);
            return;   // 알림 중복 발사 방지
        }

        match.backfillChannel(request.channelId());

        List<UUID> recipients = resolveRecipients(request, match);
        outboxEventWriter.writeMatchFanout(matchId, request.channelId(), recipients);

        log.info("[Saga] channel_id 백필 + 알림 발사: matchId={}, channelId={}",
            matchId, request.channelId());
    }

    /** recipients 가 비어 있으면 매칭의 두 당사자로 복원(페이로드 방어). */
    private List<UUID> resolveRecipients(ChannelCreatedRequest request, MatchEntity match) {
        if (request.recipients() != null && !request.recipients().isEmpty()) {
            return request.recipients().stream().distinct().toList();
        }
        return List.of(match.getUserLoId(), match.getUserHiId());
    }
}
