package com.social.service.match;

import com.social.common.ProfileCard;
import com.social.domain.match.MatchEntity;
import com.social.domain.match.MatchStatus;
import com.social.repository.card.ProfileCardRedisRepository;
import com.social.repository.match.MatchRepository;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 매칭 목록 조회 (F-M4) — ACTIVE 매칭 + 상대 카드 + channelId 를 최신순으로.
 *
 * <p>쿼리 2번으로 끝난다: matches 1회 + Redis MGET 1회. 상대 수가 늘어도 N+1 이 없다.
 */
@Slf4j
@RequiredArgsConstructor
@Transactional(readOnly = true)
@Service
public class MatchQueryService {

    private final MatchRepository matchRepository;
    private final ProfileCardRedisRepository profileCardRedisRepository;

    public List<MatchView> getMyMatches(UUID userId) {
        List<MatchEntity> matches = matchRepository.findMyMatches(userId, MatchStatus.ACTIVE);
        if (matches.isEmpty()) {
            return List.of();
        }

        List<UUID> partnerIds = matches.stream().map(m -> m.partnerOf(userId)).toList();
        Map<UUID, ProfileCard> cards = profileCardRedisRepository.findAllByUserIds(partnerIds);

        return matches.stream()
            .map(match -> {
                UUID partnerId = match.partnerOf(userId);
                return MatchView.of(match, cards.getOrDefault(partnerId, ProfileCard.minimal(partnerId)));
            })
            .toList();
    }
}
