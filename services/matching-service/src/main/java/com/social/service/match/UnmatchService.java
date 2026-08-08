package com.social.service.match;

import com.social.common.exception.BusinessException;
import com.social.domain.match.MatchEntity;
import com.social.repository.match.MatchRepository;
import com.social.service.outbox.OutboxEventWriter;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 언매치 (F-M5) — 매칭을 UNMATCHED 로 내리고, 채널을 닫으라는 이벤트를 같은 트랜잭션에서 적재한다.
 *
 * <p>채널은 message-service 소유라 여기서 직접 못 고친다 → {@code match-unmatched} 이벤트로 위임.
 * 상태 변경과 이벤트가 한 트랜잭션이라 "매칭은 풀렸는데 방은 열려 있는" 상태가 남지 않는다.
 */
@Slf4j
@RequiredArgsConstructor
@Service
public class UnmatchService {

    private final MatchRepository matchRepository;
    private final OutboxEventWriter outboxEventWriter;

    @Transactional
    public void unmatch(UUID userId, UUID matchId) {
        MatchEntity match = matchRepository.findById(matchId)
            .orElseThrow(() -> BusinessException.notFound("매칭을 찾을 수 없습니다: " + matchId));

        if (!match.isParticipant(userId)) {
            throw BusinessException.forbidden("본인의 매칭만 해제할 수 있습니다");
        }

        if (!match.isActive()) {
            return;   // 이미 UNMATCHED — 멱등(200)
        }

        match.unmatch();
        outboxEventWriter.writeMatchUnmatched(matchId, match.getChannelId(), match.participants());

        log.info("[Unmatch] matchId={}, by={}, channelId={}", matchId, userId, match.getChannelId());
    }
}
