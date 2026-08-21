package com.social.profile.repository;

import com.social.profile.domain.TagEntity;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TagRepository extends JpaRepository<TagEntity, UUID> {
    Optional<TagEntity> findByName(String name);

    Optional<TagEntity> findByNormalizedName(String normalizedName);

    /** 피커: 정규화 키워드 부분일치, 인기순 상위 20. (q 는 호출측에서 normalize 해서 넘긴다) */
    List<TagEntity> findTop20ByNormalizedNameContainingOrderByUsageCountDesc(String normalizedKeyword);

    /** 피커 기본값: 키워드 없을 때 인기 태그 상위 20. */
    List<TagEntity> findTop20ByOrderByUsageCountDesc();

    /** 부착 횟수 원자적 증가 — 동시성에서도 lost update 없음(DB 레벨 +1). */
    @Modifying
    @Query("update TagEntity t set t.usageCount = t.usageCount + 1 where t.tagId = :tagId")
    void incrementUsage(@Param("tagId") UUID tagId);

    /** 부착 해제 시 원자적 감소. 음수 방지 가드(> 0). */
    @Modifying
    @Query("update TagEntity t set t.usageCount = t.usageCount - 1 where t.tagId = :tagId and t.usageCount > 0")
    void decrementUsage(@Param("tagId") UUID tagId);

    /**
     * 태그 확보 — 없으면 넣고, 이미 있으면 아무 일도 안 한다.
     *
     * <p>없는 행을 SELECT ... FOR UPDATE 로 잠그려 하면 갭락이 걸리는데, 갭락끼리는 서로 호환돼서
     * 두 트랜잭션이 둘 다 잡은 다음 INSERT 에서 서로를 막아 데드락(1213)이 난다. 잠글 행이 아직
     * 없으니 락으로 풀 문제가 아니고, 유니크 인덱스가 해주는 직렬화에 맡기는 게 맞다.
     *
     * <p>충돌 시 UPDATE 값은 의미가 없다(어차피 뒤에서 잠금 조회로 다시 읽는다).
     * ON DUPLICATE KEY 절이 있어야 중복이 예외가 아니라 정상 흐름이 된다는 게 핵심이다.
     */
    @Modifying
    @Query(value = """
        insert into tag (tag_id, name, normalized_name, usage_count, created_at, updated_at)
        values (unhex(replace(:tagId, '-', '')), :name, :normalizedName, 0, now(), now())
        on duplicate key update updated_at = updated_at
        """, nativeQuery = true)
    void upsertByNormalizedName(
        @Param("tagId") String tagId,
        @Param("name") String name,
        @Param("normalizedName") String normalizedName
    );

    /**
     * 확보한 태그를 잠금 조회로 읽는다 — 여기가 비관락이 실제로 쓸모 있는 자리다.
     *
     * <p>이 시점엔 행이 반드시 있으니 갭락이 아니라 진짜 행 락이고, 잠금 조회는 스냅샷을 무시하고
     * 최신 커밋을 읽는다. 남이 방금 만든 태그도 그래서 보인다. 일반 SELECT 로 읽으면
     * REPEATABLE READ 스냅샷 때문에 못 보고 넘어가는 경우가 생긴다.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from TagEntity t where t.normalizedName = :normalizedName")
    Optional<TagEntity> findByNormalizedNameForUpdate(@Param("normalizedName") String normalizedName);
}
