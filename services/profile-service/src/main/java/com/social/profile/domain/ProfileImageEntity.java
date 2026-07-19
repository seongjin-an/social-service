package com.social.profile.domain;

import com.social.common.UuidV7Generator;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.domain.Persistable;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "profile_image")
@Entity
public class ProfileImageEntity extends BaseEntity implements Persistable<UUID> {

    @Id
    @Column(name = "profile_image_id", columnDefinition = "BINARY(16)")
    private UUID profileImageId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "profile_id")
    private ProfileEntity profile; // 프로필 ID

    @Column(nullable = false, length = 255)
    private String originalFileName; // 원본 파일명

    @Column(nullable = false, unique = true, length = 255)
    private String storedFileName; // 저장된 파일명(UUID 등)

    @Column(nullable = false, unique = true, length = 500)
    private String objectKey; // S3 Key 또는 MinIO Object Key

    @Column(nullable = false, length = 1000)
    private String imageUrl; // 접근 URL(CDN URL)

    @Column(nullable = false, length = 100)
    private String contentType; // MIME Type

    @Column(nullable = false)
    private Long fileSize; // 파일 크기(Byte)

    @Column(nullable = false)
    private Boolean primaryImage; // 대표 프로필 사진 여부

    @Builder
    public ProfileImageEntity(
        ProfileEntity profile,
        String originalFileName,
        String storedFileName,
        String objectKey,
        String imageUrl,
        String contentType,
        Long fileSize,
        Boolean primaryImage
    ) {
        this.profileImageId = UuidV7Generator.generate();
        this.profile = profile;
        this.originalFileName = originalFileName;
        this.storedFileName = storedFileName;
        this.objectKey = objectKey;
        this.imageUrl = imageUrl;
        this.contentType = contentType;
        this.fileSize = fileSize;
        this.primaryImage = primaryImage;
    }

    public void changePrimary(boolean primary) {
        this.primaryImage = primary;
    }

    @Override
    public UUID getId() {
        return profileImageId;
    }

    @Override
    public boolean isNew() {
        return getCreatedAt() == null;
    }
}
