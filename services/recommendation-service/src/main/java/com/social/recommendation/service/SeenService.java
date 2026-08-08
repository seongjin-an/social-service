package com.social.recommendation.service;

import com.social.recommendation.repository.SeenRedisRepository;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 본 사람 기록 (F2-3) — 다음 피드에서 제외될 명단에 추가한다.
 *
 * <p>진입점이 둘이다.
 * <ul>
 *   <li><b>좋아요/패스</b>: {@code like-relay} 구독으로 자동(클라이언트가 뭘 하지 않아도 성립)</li>
 *   <li><b>카드 노출</b>: {@code POST /api/feed/seen} — 클라이언트가 실제로 보여준 카드를 알려준다</li>
 * </ul>
 *
 * <p>SADD 라 두 경로가 겹쳐도 무해하다(멱등).
 */
@Slf4j
@RequiredArgsConstructor
@Service
public class SeenService {

    private final SeenRedisRepository seenRedisRepository;

    public void markSeen(UUID userId, List<UUID> targetIds) {
        List<UUID> targets = targetIds.stream()
            .filter(target -> target != null && !target.equals(userId))   // 자기 자신은 애초에 후보가 아니다
            .distinct()
            .toList();

        if (targets.isEmpty()) {
            return;
        }

        seenRedisRepository.add(userId, targets);
        log.debug("[Seen] {}건 추가: userId={}", targets.size(), userId);
    }
}
