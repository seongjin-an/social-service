package com.social.profile.domain;


import com.social.common.Gender;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.domain.Persistable;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "profile")
@Entity
public class ProfileEntity extends BaseEntity implements Persistable<UUID> {
    @Id
    @Column(name = "profile_id", columnDefinition = "BINARY(16)")
    private UUID profileId;

    @Column(name = "user_id")
    private UUID userId;

    @Column(name = "gender")
    @Enumerated(EnumType.STRING)
    private Gender gender;

    @Column(name = "birthday")
    private LocalDate birthday;

    @Column(name = "bio")
    private String bio;

    @OneToMany(mappedBy = "profile")
    private List<ProfileTagEntity> profileTagEntities = new ArrayList<>();

    @OneToMany(mappedBy = "profile")
    private List<ProfileImageEntity> profileImageEntities = new ArrayList<>();

    @Column(name = "pref_gender")
    @Enumerated(EnumType.STRING)
    private Gender prefGender;

    @Column(name = "pref_age_min")
    private Integer prefAgeMin;

    @Column(name = "pref_age_max")
    private Integer prefAgeMax;

    @Column(name = "pref_distance_km")
    private Integer prefDistanceKm;

    private ProfileEntity(UUID profileId, UUID userId, Gender gender, LocalDate birthday, String bio,
        Gender prefGender, Integer prefAgeMin, Integer prefAgeMax, Integer prefDistanceKm
    ) {
        this.profileId = profileId;
        this.userId = userId;
        this.gender = gender;
        this.birthday = birthday;
        this.bio = bio;
        this.prefGender = prefGender;
        this.prefAgeMin = prefAgeMin;
        this.prefAgeMax = prefAgeMax;
        this.prefDistanceKm = prefDistanceKm;
    }

    public static ProfileEntity of(UUID profileId, UUID userId, Gender gender, LocalDate birthday, String bio,
        Gender prefGender, Integer prefAgeMin, Integer prefAgeMax, Integer prefDistanceKm
    ) {
        return new ProfileEntity(profileId, userId, gender, birthday, bio, prefGender, prefAgeMin, prefAgeMax, prefDistanceKm);
    }

    public void add(List<ProfileTagEntity> profileTags) {
        this.profileTagEntities.addAll(profileTags);
        for (ProfileTagEntity profileTag : profileTags) {
            profileTag.setProfile(this);
        }
    }

    @Override
    public UUID getId() {
        return profileId;
    }

    @Override
    public boolean isNew() {
        return getCreatedAt() == null;
    }
}

/*
CREATE TABLE profiles (
    profile_id       BINARY(16)   NOT NULL COMMENT 'PK',
    user_id          BINARY(16)   NOT NULL COMMENT 'FK · users.id',
    gender           VARCHAR(10)  NULL COMMENT '내 성별 · MALE|FEMALE|OTHER',
    birth_year       SMALLINT     NULL COMMENT '출생연도(YYYY) · 나이 = 현재연도 - birth_year',
    bio              VARCHAR(500) NULL COMMENT '자기소개 문구 · 프로필 카드 노출',
    interests        JSON         NULL COMMENT '관심사 태그 배열 예:["여행","러닝"] · 추천 랭킹 겹침 점수 입력',
    photos           JSON         NULL COMMENT '사진 URL 배열 · 데모는 placeholder URL 이면 충분',
    pref_gender      VARCHAR(10)  NULL COMMENT '선호 성별(추천 필터) · MALE|FEMALE|ALL',
    pref_age_min     TINYINT      NULL COMMENT '선호 최소 나이(추천 필터)',
    pref_age_max     TINYINT      NULL COMMENT '선호 최대 나이(추천 필터)',
    pref_distance_km SMALLINT     NULL COMMENT '선호 반경 km(추천 필터) · GEOSEARCH 반경 입력',
    created_at       DATETIME     NULL COMMENT '프로필 생성 시각',
    updated_at       DATETIME     NULL COMMENT '프로필 수정 시각',
    CONSTRAINT PK_PROFILE_ID PRIMARY KEY (profile_id),
    CONSTRAINT FK_PROFILE_USER_ID FOREIGN KEY (user_id) REFERENCES USERS(user_id)
);
 */