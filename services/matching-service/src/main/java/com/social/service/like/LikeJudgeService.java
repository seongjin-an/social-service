package com.social.service.like;

import static com.social.domain.like.LikeType.LIKE;
import static com.social.domain.like.LikeType.SUPER;

import com.social.common.StringUtils;
import com.social.common.UuidV7Generator;
import com.social.domain.like.LikeEntity;
import com.social.domain.like.LikeType;
import com.social.domain.match.MatchEntity;
import com.social.kafka.message.out.LikeRelayPayload;
import com.social.repository.like.LikeRepository;
import com.social.repository.match.MatchRepository;
import com.social.service.outbox.OutboxEventWriter;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@RequiredArgsConstructor
@Service
public class LikeJudgeService {

    private final LikeRepository likeRepository;
    private final MatchRepository matchRepository;
    private final OutboxEventWriter outboxEventWriter;

    @Transactional
    public void judge(LikeRelayPayload payload) {

        UUID from = payload.fromUserId();
        UUID to = payload.toUserId();

        // 1. UPSERT likes
        likeRepository.save(LikeEntity.of(from, to, payload.type()));

        // 2. PASS 는 판정 제외
        if (payload.type() == LikeType.PASS) return;

        // 3. 역방향: 상대가 이미 나를 LIKE/SUPER 했는지?
        Boolean reciprocated = likeRepository.existsByFromUserIdAndToUserIdAndTypeIn(to, from,
            List.of(LIKE, SUPER));
        if (!reciprocated) return; // 안했으면 종료

        // 4. 정렬 + 이미 매칭인지 확인(멱등)
        UUID lo = StringUtils.minUuid(from, to);
        UUID hi = StringUtils.maxUuid(from, to);
        if (matchRepository.findByUserLoIdAndUserHiId(lo, hi).isPresent()) return; // 이미 매칭된 상태면 종료

        // 5. 매칭 생성 + outbox(같은 트랜잭션)
        UUID matchId = UuidV7Generator.generate();
        matchRepository.save(MatchEntity.of(matchId, lo, hi));
        outboxEventWriter.writeMatchCreated(matchId, lo, hi, List.of(from, to));
    }
}
