package com.social.repository.match;

import com.social.domain.match.MatchEntity;
import com.social.domain.match.MatchStatus;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface MatchRepository extends JpaRepository<MatchEntity, UUID> {

    Optional<MatchEntity> findByUserLoIdAndUserHiId(UUID lo, UUID hi);

    /**
     * 내 매칭 목록 — lo/hi 어느 쪽이든 나면 내 매칭이다(정렬 저장이라 위치가 고정되지 않음).
     * OR 양쪽 모두 인덱스가 있다: idx_matches_my_matching1(lo,status) / 2(hi,status).
     *
     * <p>정렬은 match_id DESC = 최신순. matchId 가 UUID v7(시간 정렬)이고 BINARY(16) 로
     * 빅엔디언 저장되므로 바이트 순서 = 생성 시간 순서다. createdAt 정렬과 결과가 같고 유일성까지 보장된다.
     */
    @Query("""
        SELECT M FROM MatchEntity M
        WHERE (M.userLoId = :userId OR M.userHiId = :userId)
          AND M.status = :status
        ORDER BY M.matchId DESC
    """)
    List<MatchEntity> findMyMatches(UUID userId, MatchStatus status);

    /**
     * 상태 무관하게 나와 매칭 이력이 있는 쌍 — "받은 좋아요"에서 제외할 상대를 뽑는다.
     * UNMATCHED 도 제외하는 이유: 판정 로직이 이미 매칭 이력이 있는 쌍을 재매칭하지 않으므로,
     * 목록에 남겨두면 눌러도 아무 일이 안 일어나는 카드가 된다.
     */
    @Query("""
        SELECT M FROM MatchEntity M
        WHERE M.userLoId = :userId OR M.userHiId = :userId
    """)
    List<MatchEntity> findAllByParticipant(UUID userId);
}
