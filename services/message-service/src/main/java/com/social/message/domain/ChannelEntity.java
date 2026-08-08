package com.social.message.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.util.Objects;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@AllArgsConstructor(access = AccessLevel.PRIVATE)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Getter
@Table(
    name = "channel",
    indexes = {
        // ★ Saga 멱등 키: MATCH_CREATED 재수신 시 "이 매칭의 채널이 이미 있나"를 이걸로 판정.
        //   UNIQUE 라 동시 재수신 폭주에도 DIRECT 채널은 매칭당 정확히 1개.
        @Index(name = "uk_channel_match_id", columnList = "match_id", unique = true)
    })
@Entity
public class ChannelEntity extends BaseEntity{

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "channel_id", nullable = false)
    private Long channelId;

    @Column(name = "title", nullable = false)
    private String title;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", length = 10)
    private ChannelType type;

    /** DIRECT 채널이 귀속된 매칭(matching-service 의 matches.match_id). OPEN 은 NULL. */
    @Column(name = "match_id", columnDefinition = "BINARY(16)")
    private UUID matchId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 10)
    private ChannelStatus status;

    private ChannelEntity(String title, ChannelType type, UUID matchId, ChannelStatus status) {
        this.title = title;
        this.type = type;
        this.matchId = matchId;
        this.status = status;
    }

    public static ChannelEntity of(String title) {
        return new ChannelEntity(title, ChannelType.OPEN, null, ChannelStatus.ACTIVE);
    }

    /**
     * 매칭으로 생성되는 1:1 채널. 제목은 상대에 따라 클라이언트가 표시하므로 빈 문자열로 둔다.
     * (title 컬럼이 기존 스키마에서 NOT NULL 이라 null 대신 "" 를 넣는다 — ddl-auto=update 는 제약을 완화하지 않는다.)
     */
    public static ChannelEntity direct(UUID matchId) {
        return new ChannelEntity("", ChannelType.DIRECT, matchId, ChannelStatus.ACTIVE);
    }

    /** 언매치 → 채널 닫기. 이미 CLOSED 면 no-op(멱등). */
    public void close() {
        this.status = ChannelStatus.CLOSED;
    }

    /** 기존 행은 status 가 NULL 일 수 있으므로 "CLOSED 가 아니면 열린 것"으로 판정한다. */
    public boolean isClosed() {
        return this.status == ChannelStatus.CLOSED;
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        ChannelEntity that = (ChannelEntity) o;
        return Objects.equals(channelId, that.channelId);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(channelId);
    }
}
