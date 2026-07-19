package com.social.profile.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.domain.Persistable;

@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Getter
@Table(
    name = "profile_tag",
    uniqueConstraints = {
        @UniqueConstraint(
            name = "uk_profile_tag",
            columnNames = {"profile_id", "tag_id"}
        )
    }
)
@Entity
public class ProfileTagEntity extends BaseEntity implements Persistable<UUID> {
    @Id
    @Column(name = "profile_tag_id", columnDefinition = "BINARY(16)")
    private UUID profileTagId;

    @Setter
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "profile_id", nullable = false)
    private ProfileEntity profile;

    @Setter
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tag_id", nullable = false)
    private TagEntity tag;

    private ProfileTagEntity(ProfileEntity profile, TagEntity tag) {
        this.profile = profile;
        this.tag = tag;
    }

    private ProfileTagEntity(UUID profileTagId, ProfileEntity profile, TagEntity tag) {
        this(profile, tag);
        this.profileTagId = profileTagId;
    }

    public static ProfileTagEntity of(UUID profileTagId, ProfileEntity profile, TagEntity tag) {
        return new ProfileTagEntity(profileTagId, profile, tag);
    }

    @Override
    public UUID getId() {
        return profileTagId;
    }

    @Override
    public boolean isNew() {
        return getCreatedAt() == null;
    }
}
