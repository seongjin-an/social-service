package com.social.recommendation.service;

import com.social.common.ProfileCard;
import com.social.common.ProfilePreference;
import com.social.recommendation.repository.FeedSnapshotRedisRepository;
import com.social.recommendation.repository.FeedSnapshotRedisRepository.FeedEntry;
import com.social.recommendation.repository.GeoSearchRedisRepository;
import com.social.recommendation.repository.GeoSearchRedisRepository.Candidate;
import com.social.recommendation.repository.ProfileCardRedisRepository;
import com.social.recommendation.repository.ProfilePreferenceRedisRepository;
import com.social.recommendation.repository.SeenRedisRepository;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * 추천 후보 스냅샷 계산 (F2-1 의 1~4단계).
 *
 * <pre>
 * GEOSEARCH(선호 반경) → 본인 제외 → seen 제외 → 카드 MGET → 성별/나이 필터 → 점수 정렬 → 스냅샷 적재
 * </pre>
 *
 * <p><b>Redis 왕복은 후보 수와 무관하게 고정 6회다</b>(pref GET · geo GEOSEARCH · seen SMISMEMBER ·
 * 내 카드 GET · 후보 카드 MGET · 스냅샷 쓰기). 후보 1명이든 500명이든 같다.
 *
 * <p><b>순서가 중요하다</b>: seen 제외를 카드 MGET <b>앞에</b> 둔다. 이미 스와이프한 사람의 카드를
 * 가져올 이유가 없고, 오래 쓴 유저일수록 그 비중이 크다.
 *
 * <p>이 서비스는 DB 를 보지 않는다 — 필터 입력(나이·성별)과 랭킹 입력(태그)이 모두 카드 캐시에
 * 들어 있기 때문이다. features.md 가 제안한 {@code SINTERCARD tags:{me} tags:{cand}} 는 쓰지 않는다:
 * 카드를 이미 MGET 했으므로 겹침은 메모리에서 계산되고, SINTERCARD 는 후보 수만큼 왕복이 늘어난다.
 */
@Slf4j
@Service
public class FeedSnapshotService {

    private final double defaultRadiusKm;
    private final double maxRadiusKm;
    private final int candidateLimit;
    private final double distanceWeight;
    private final double tagWeight;
    private final int tagSaturation;

    private final GeoSearchRedisRepository geoSearchRedisRepository;
    private final ProfileCardRedisRepository profileCardRedisRepository;
    private final ProfilePreferenceRedisRepository profilePreferenceRedisRepository;
    private final SeenRedisRepository seenRedisRepository;
    private final FeedSnapshotRedisRepository feedSnapshotRedisRepository;

    public FeedSnapshotService(
        @Value("${feed.default-radius-km}") double defaultRadiusKm,
        @Value("${feed.max-radius-km}") double maxRadiusKm,
        @Value("${feed.candidate-limit}") int candidateLimit,
        @Value("${feed.ranking.distance-weight}") double distanceWeight,
        @Value("${feed.ranking.tag-weight}") double tagWeight,
        @Value("${feed.ranking.tag-saturation}") int tagSaturation,
        GeoSearchRedisRepository geoSearchRedisRepository,
        ProfileCardRedisRepository profileCardRedisRepository,
        ProfilePreferenceRedisRepository profilePreferenceRedisRepository,
        SeenRedisRepository seenRedisRepository,
        FeedSnapshotRedisRepository feedSnapshotRedisRepository
    ) {
        this.defaultRadiusKm = defaultRadiusKm;
        this.maxRadiusKm = maxRadiusKm;
        this.candidateLimit = candidateLimit;
        this.distanceWeight = distanceWeight;
        this.tagWeight = tagWeight;
        this.tagSaturation = tagSaturation;
        this.geoSearchRedisRepository = geoSearchRedisRepository;
        this.profileCardRedisRepository = profileCardRedisRepository;
        this.profilePreferenceRedisRepository = profilePreferenceRedisRepository;
        this.seenRedisRepository = seenRedisRepository;
        this.feedSnapshotRedisRepository = feedSnapshotRedisRepository;
    }

    /**
     * 후보를 다시 계산해 스냅샷을 교체한다.
     *
     * @return 새 스냅샷 버전(커서에 실린다). 후보가 0명이어도 버전은 발급한다 —
     *         "계산했더니 없었다"와 "아직 계산 전"을 구분해야 커서 판정이 흔들리지 않는다.
     */
    public String rebuild(UUID userId) {
        // 위치를 한 번도 올리지 않은 유저는 반경검색의 기준점이 없다(FROMMEMBER 가 성립하지 않는다).
        if (!geoSearchRedisRepository.hasLocation(userId)) {
            log.info("[Feed] 위치 없음 — 빈 피드: userId={}", userId);
            return feedSnapshotRedisRepository.replace(userId, List.of());
        }

        ProfilePreference preference = profilePreferenceRedisRepository.findByUserId(userId);
        double radiusKm = resolveRadiusKm(preference);

        List<Candidate> nearby = geoSearchRedisRepository.searchNearby(userId, radiusKm, candidateLimit);

        // 자기 자신은 항상 반경 0으로 잡히므로 반드시 뺀다.
        List<Candidate> candidates = nearby.stream()
            .filter(candidate -> !candidate.userId().equals(userId))
            .toList();
        if (candidates.isEmpty()) {
            log.info("[Feed] 반경 {}km 내 후보 없음: userId={}", radiusKm, userId);
            return feedSnapshotRedisRepository.replace(userId, List.of());
        }

        // 이미 좋아요/패스한 상대 제외 — 카드 조회 전에 걸러 MGET 크기를 줄인다.
        Set<UUID> seen = seenRedisRepository.filterSeen(userId,
            candidates.stream().map(Candidate::userId).toList());
        List<Candidate> unseen = candidates.stream()
            .filter(candidate -> !seen.contains(candidate.userId()))
            .toList();
        if (unseen.isEmpty()) {
            log.info("[Feed] 후보 {}명 전원 seen — 빈 피드: userId={}", candidates.size(), userId);
            return feedSnapshotRedisRepository.replace(userId, List.of());
        }

        Map<UUID, ProfileCard> cards = profileCardRedisRepository.findAllByUserIds(
            unseen.stream().map(Candidate::userId).toList());
        Set<String> myTags = myTags(userId);

        List<FeedEntry> ranked = unseen.stream()
            .filter(candidate -> cards.containsKey(candidate.userId()))
            .filter(candidate -> matchesPreference(preference, cards.get(candidate.userId())))
            .map(candidate -> new Scored(
                candidate,
                score(candidate.distanceKm(), radiusKm, myTags, cards.get(candidate.userId()))))
            .sorted(Comparator
                .comparingDouble((Scored scored) -> scored.score).reversed()
                .thenComparingDouble(scored -> scored.candidate.distanceKm())
                .thenComparing(scored -> scored.candidate.userId()))
            .map(scored -> new FeedEntry(scored.candidate.userId(), scored.candidate.distanceKm()))
            .toList();

        String version = feedSnapshotRedisRepository.replace(userId, ranked);
        log.info("[Feed] 스냅샷 재계산: userId={}, 반경={}km, 검색={}, seen제외={}, 최종={}, ver={}",
            userId, radiusKm, candidates.size(), unseen.size(), ranked.size(), version);

        return version;
    }

    /** 내 관심사 태그 — 카드가 없으면 겹침 점수는 0이 되고 거리 점수만으로 랭킹된다. */
    public Set<String> myTags(UUID userId) {
        return profileCardRedisRepository.findByUserId(userId)
            .map(card -> normalizedTags(card))
            .orElseGet(Set::of);
    }

    /** 나와 겹치는 태그 — 응답의 추천 근거로도 쓰인다(원본 표기 유지). */
    public List<String> sharedTags(Set<String> myTags, ProfileCard card) {
        if (myTags.isEmpty() || card.tags() == null) {
            return List.of();
        }
        return card.tags().stream()
            .filter(tag -> tag != null && myTags.contains(tag.trim().toLowerCase()))
            .toList();
    }

    //-------------------------------------------------------------------------------------------------
    // private
    //-------------------------------------------------------------------------------------------------

    /**
     * 선호 반경 보정. 반경 없는 GEOSEARCH 는 불가능하므로 미설정이면 기본값을 쓰고,
     * 터무니없이 큰 값은 상한으로 자른다(반경이 곧 후보 수이고 후보 수가 곧 계산량이다).
     */
    private double resolveRadiusKm(ProfilePreference preference) {
        Integer preferred = preference.prefDistanceKm();
        if (preferred == null || preferred <= 0) {
            return defaultRadiusKm;
        }
        return Math.min(preferred, maxRadiusKm);
    }

    /**
     * 성별·나이 필터. <b>선호가 설정돼 있는데 카드에 그 값이 없으면 제외한다</b> —
     * 확인 못 한 후보를 조건에 맞는 척 보여주면 필터가 있으나 마나가 된다.
     */
    private boolean matchesPreference(ProfilePreference preference, ProfileCard card) {
        if (preference.prefGender() != null
            && !preference.prefGender().equalsIgnoreCase(card.gender())) {
            return false;
        }

        boolean hasAgeBound = preference.prefAgeMin() != null || preference.prefAgeMax() != null;
        if (!hasAgeBound) {
            return true;
        }
        if (card.age() == null) {
            return false;
        }
        if (preference.prefAgeMin() != null && card.age() < preference.prefAgeMin()) {
            return false;
        }
        return preference.prefAgeMax() == null || card.age() <= preference.prefAgeMax();
    }

    /**
     * 점수 = 거리가중 × (1 - 거리/반경) + 태그가중 × min(겹침수/포화값, 1).
     * 가까울수록·겹칠수록 높다. 절대값은 의미 없고 상대 순서만 쓴다.
     */
    private double score(double distanceKm, double radiusKm, Set<String> myTags, ProfileCard card) {
        double distanceScore = radiusKm <= 0 ? 0 : Math.max(0, 1 - (distanceKm / radiusKm));

        long overlap = card.tags() == null ? 0 : card.tags().stream()
            .filter(tag -> tag != null && myTags.contains(tag.trim().toLowerCase()))
            .count();
        double tagScore = tagSaturation <= 0 ? 0 : Math.min(1.0, (double) overlap / tagSaturation);

        return distanceWeight * distanceScore + tagWeight * tagScore;
    }

    /** 대소문자·공백 차이로 겹침을 놓치지 않도록 정규화해 비교한다. */
    private Set<String> normalizedTags(ProfileCard card) {
        if (card.tags() == null) {
            return Set.of();
        }
        Set<String> tags = new HashSet<>();
        for (String tag : card.tags()) {
            if (tag != null && !tag.isBlank()) {
                tags.add(tag.trim().toLowerCase());
            }
        }
        return tags;
    }

    private record Scored(Candidate candidate, double score) {}
}
