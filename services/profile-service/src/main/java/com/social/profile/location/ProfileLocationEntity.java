package com.social.profile.location;

import com.social.profile.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.domain.Persistable;

/**
 * 프로필 위치 (프로필당 1:1). PK = profileId (profile.profile_id 와 공유).
 *
 * <p>실시간 반경검색의 <b>권위</b>는 Redis {@code geo:users}, 이 테이블은 <b>마지막 위치 백업</b>(source of truth)이다.
 * 초고빈도 쓰기라 profile 본체와 분리된 전용 테이블을 쓴다.
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "profile_location")
@Entity
public class ProfileLocationEntity extends BaseEntity implements Persistable<UUID> {

    @Id
    @Column(name = "profile_id", columnDefinition = "BINARY(16)")
    private UUID profileId;

    // 위도/경도 · DECIMAL(9,6) (정수부 최대 3자리 → 위도 90 / 경도 180 수용)
    @Column(name = "lat", precision = 9, scale = 6)
    private BigDecimal lat;

    @Column(name = "lng", precision = 9, scale = 6)
    private BigDecimal lng;

    private ProfileLocationEntity(UUID profileId, BigDecimal lat, BigDecimal lng) {
        this.profileId = profileId;
        this.lat = lat;
        this.lng = lng;
    }

    public static ProfileLocationEntity of(UUID profileId, double lat, double lng) {
        return new ProfileLocationEntity(profileId, BigDecimal.valueOf(lat), BigDecimal.valueOf(lng));
    }

    /** UPSERT 의 update 경로 — 기존 행의 좌표만 갱신. */
    public void updateCoordinates(double lat, double lng) {
        this.lat = BigDecimal.valueOf(lat);
        this.lng = BigDecimal.valueOf(lng);
    }

    @Override
    public UUID getId() {
        return profileId;
    }

    @Override
    public boolean isNew() {
        // 영속 전이면 createdAt 아직 null → 새 엔티티(INSERT). 조회로 불러온 기존 행은 UPDATE.
        return getCreatedAt() == null;
    }
}
