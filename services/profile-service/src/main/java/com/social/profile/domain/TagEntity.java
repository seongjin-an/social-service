package com.social.profile.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.util.List;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.domain.Persistable;

@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Getter
@Table(name = "tag", uniqueConstraints = {
    // 어휘 통제는 정규화 이름 기준 — "Java"="java" 를 하나로. (get-or-create 레이스도 이 유니크가 잡음)
    @UniqueConstraint(name = "uk_tag_normalized_name", columnNames = "normalized_name")
})
@Entity
public class TagEntity extends BaseEntity implements Persistable<UUID> {
    @Id
    @Column(name = "tag_id", columnDefinition = "BINARY(16)")
    private UUID tagId;

    @Column(name = "name", nullable = false, length = 50)
    private String name; // 화면에 보여줄 이름 ex) Java, Spring Boot

    @Column(name = "normalized_name", nullable = false, length = 50, updatable = false)
    private String normalizedName;

    @Column(name = "usage_count", nullable = false)
    private Long usageCount = 0L;

    @OneToMany(mappedBy = "tag")
    private List<ProfileTagEntity> profileTagEntities;

    private TagEntity(String name) {
        this.name = name;
        this.normalizedName = normalize(name);
    }

    private TagEntity(UUID tagId, String name) {
        this(name);
        this.tagId = tagId;
    }

    public static TagEntity of(UUID tagId, String name) {
        return new TagEntity(tagId, name);
    }

    @Override
    public UUID getId() {
        return tagId;
    }

    @Override
    public boolean isNew() {
        return getCreatedAt() == null;   // 영속 전이면 createdAt 아직 null → 새 엔티티
    }


    public void increaseUsage() {
        usageCount++;
    }

    public void decreaseUsage() {
        usageCount--;
    }

    public static String normalize(String value) {
        return value.trim().toLowerCase();
    }
}
