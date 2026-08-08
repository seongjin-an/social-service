package com.social.service.like;

import static com.social.domain.like.LikeType.LIKE;
import static com.social.domain.like.LikeType.SUPER;

import com.social.common.ProfileCard;
import com.social.domain.like.LikeEntity;
import com.social.domain.match.MatchEntity;
import com.social.repository.card.ProfileCardRedisRepository;
import com.social.repository.like.LikeRepository;
import com.social.repository.match.MatchRepository;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 받은 좋아요 (F-M6) — 나를 LIKE/SUPER 한 사람 중 <b>아직 매칭 안 된</b> 사람들의 카드.
 *
 * <p>PASS 는 제외(판정 대상이 아니므로 보여줄 이유가 없다). 이미 매칭된 상대도 제외 —
 * 그건 매칭 목록(F-M4)에 이미 있고, 여기서 또 누르게 하면 아무 일도 일어나지 않는다.
 */
@Slf4j
@RequiredArgsConstructor
@Transactional(readOnly = true)
@Service
public class ReceivedLikeQueryService {

    private final LikeRepository likeRepository;
    private final MatchRepository matchRepository;
    private final ProfileCardRedisRepository profileCardRedisRepository;

    public List<ReceivedLikeView> getReceivedLikes(UUID userId) {
        List<LikeEntity> likes =
            likeRepository.findByToUserIdAndTypeInOrderByCreatedAtDesc(userId, List.of(LIKE, SUPER));
        if (likes.isEmpty()) {
            return List.of();
        }

        Set<UUID> alreadyMatched = matchRepository.findAllByParticipant(userId).stream()
            .map(match -> partnerOf(match, userId))
            .collect(Collectors.toSet());

        List<LikeEntity> pending = likes.stream()
            .filter(like -> !alreadyMatched.contains(like.getFromUserId()))
            .toList();
        if (pending.isEmpty()) {
            return List.of();
        }

        List<UUID> senderIds = pending.stream().map(LikeEntity::getFromUserId).toList();
        Map<UUID, ProfileCard> cards = profileCardRedisRepository.findAllByUserIds(senderIds);

        return pending.stream()
            .map(like -> new ReceivedLikeView(
                cards.getOrDefault(like.getFromUserId(), ProfileCard.minimal(like.getFromUserId())),
                like.getType(),
                like.getCreatedAt()))
            .toList();
    }

    private UUID partnerOf(MatchEntity match, UUID userId) {
        return match.getUserLoId().equals(userId) ? match.getUserHiId() : match.getUserLoId();
    }
}
