package com.social.recommendation.service;

import com.social.common.ProfileCard;
import com.social.recommendation.repository.FeedSnapshotRedisRepository;
import com.social.recommendation.repository.FeedSnapshotRedisRepository.FeedEntry;
import com.social.recommendation.repository.ProfileCardRedisRepository;
import com.social.recommendation.repository.SeenRedisRepository;
import com.social.recommendation.service.FeedView.FeedItem;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * 피드 서빙 (F2-1 의 5단계) — 스냅샷을 커서로 훑어 카드를 붙여 내려준다.
 *
 * <p><b>커서 없음 = 처음부터</b>: 스냅샷을 새로 계산한다. 클라이언트가 "새로고침"하면 최신 후보를
 * 받는다는 뜻이다. 커서가 있으면 그 스냅샷 안에서만 이어 읽는다(순서 고정 → 중복/누락 없음).
 *
 * <p><b>카드는 스냅샷에 담지 않고 서빙 때 다시 읽는다</b>. 스냅샷은 "누구를 어떤 순서로"만 고정하고,
 * 보여줄 내용은 항상 최신이어야 한다(사진을 바꾼 사람이 옛 사진으로 노출되면 안 된다).
 */
@Slf4j
@Service
public class FeedQueryService {

    /** 필터로 페이지가 통째로 비었을 때 다음 구간을 훑는 최대 횟수 — 무한 루프 방지. */
    private static final int MAX_FILL_ATTEMPTS = 5;

    private final int defaultSize;
    private final int maxSize;

    private final FeedSnapshotService feedSnapshotService;
    private final FeedSnapshotRedisRepository feedSnapshotRedisRepository;
    private final ProfileCardRedisRepository profileCardRedisRepository;
    private final SeenRedisRepository seenRedisRepository;

    public FeedQueryService(
        @Value("${feed.default-size}") int defaultSize,
        @Value("${feed.max-size}") int maxSize,
        FeedSnapshotService feedSnapshotService,
        FeedSnapshotRedisRepository feedSnapshotRedisRepository,
        ProfileCardRedisRepository profileCardRedisRepository,
        SeenRedisRepository seenRedisRepository
    ) {
        this.defaultSize = defaultSize;
        this.maxSize = maxSize;
        this.feedSnapshotService = feedSnapshotService;
        this.feedSnapshotRedisRepository = feedSnapshotRedisRepository;
        this.profileCardRedisRepository = profileCardRedisRepository;
        this.seenRedisRepository = seenRedisRepository;
    }

    public FeedView getFeed(UUID userId, String cursor, Integer requestedSize) {
        int size = resolveSize(requestedSize);
        Position position = resolvePosition(userId, cursor);

        long total = feedSnapshotRedisRepository.size(userId);
        if (total == 0) {
            return FeedView.empty();
        }

        Set<String> myTags = feedSnapshotService.myTags(userId);

        // 서빙 시점 필터로 페이지가 다 날아갈 수 있다(스냅샷 계산 뒤에 좋아요를 누른 경우).
        // 그때 빈 배열 + 커서를 주면 클라이언트는 "끝"으로 읽는다 → 다음 구간까지 훑어서 채운다.
        List<FeedItem> items = List.of();
        int offset = position.offset();
        int attempts = 0;

        while (items.isEmpty() && offset < total && attempts < MAX_FILL_ATTEMPTS) {
            List<FeedEntry> entries = feedSnapshotRedisRepository.page(userId, offset, size);
            if (entries.isEmpty()) {
                break;
            }
            items = toItems(userId, entries, myTags);
            offset += entries.size();
            attempts++;
        }

        String nextCursor = offset >= total
            ? null
            : new FeedCursor(position.version(), offset).encode();

        log.debug("[Feed] 서빙: userId={}, offset={}→{}, total={}, items={}",
            userId, position.offset(), offset, total, items.size());

        return FeedView.of(items, nextCursor);
    }

    //-------------------------------------------------------------------------------------------------
    // private
    //-------------------------------------------------------------------------------------------------

    /**
     * 읽기 시작 지점 결정. 커서가 없거나 <b>버전이 어긋나면</b>(스냅샷이 만료돼 재계산된 경우)
     * 스냅샷을 새로 만들고 처음부터 준다 — 어긋난 offset 으로 엉뚱한 사람을 주는 것보다 낫다.
     */
    private Position resolvePosition(UUID userId, String cursor) {
        Optional<FeedCursor> decoded = FeedCursor.decode(cursor);
        if (decoded.isEmpty()) {
            return new Position(feedSnapshotService.rebuild(userId), 0);
        }

        String currentVersion = feedSnapshotRedisRepository.currentVersion(userId);
        if (currentVersion == null || !currentVersion.equals(decoded.get().version())) {
            log.info("[Feed] 커서 무효(스냅샷 교체됨) — 처음부터 다시: userId={}", userId);
            return new Position(feedSnapshotService.rebuild(userId), 0);
        }

        return new Position(currentVersion, decoded.get().offset());
    }

    /** 스냅샷 원소 → 응답 카드. 카드 미스와 서빙 시점 seen 은 여기서 빠진다. */
    private List<FeedItem> toItems(UUID userId, List<FeedEntry> entries, Set<String> myTags) {
        List<UUID> ids = entries.stream().map(FeedEntry::userId).toList();

        Set<UUID> seen = seenRedisRepository.filterSeen(userId, ids);
        Map<UUID, ProfileCard> cards = profileCardRedisRepository.findAllByUserIds(ids);

        List<FeedItem> items = new ArrayList<>();
        for (FeedEntry entry : entries) {
            if (seen.contains(entry.userId())) {
                continue;
            }
            ProfileCard card = cards.get(entry.userId());
            if (card == null) {
                // 스냅샷 계산 후 프로필이 삭제됐거나 카드 TTL 이 만료됐다 → 보여줄 내용이 없다.
                continue;
            }
            items.add(new FeedItem(card, entry.distanceKm(), feedSnapshotService.sharedTags(myTags, card)));
        }
        return items;
    }

    private int resolveSize(Integer requestedSize) {
        if (requestedSize == null || requestedSize <= 0) {
            return defaultSize;
        }
        return Math.min(requestedSize, maxSize);
    }

    private record Position(String version, int offset) {}
}
