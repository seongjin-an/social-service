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
        @Index(name = "uk_channel_match_id", columnList = "match_id", unique = true),

        // 오픈채팅 게시판 목록 — WHERE type='OPEN' AND status='ACTIVE' ORDER BY channel_id DESC.
        // channel_id 를 마지막에 둬서 커서 페이지네이션이 정렬 없이 인덱스만 타고 나간다.
        @Index(name = "idx_channel_open_browse", columnList = "type, status, channel_id"),

        // 카테고리 탭 — 위 인덱스는 category 가 없어 탭 조회 때 필터링이 남는다.
        // schema.sql 초안은 (type, category, status) 하나였지만 그러면 "전체" 탭이
        // category 를 건너뛰어야 해서 channel_id 범위를 못 쓴다. 그래서 둘로 나눴다.
        @Index(name = "idx_channel_open_category", columnList = "type, status, category, channel_id")
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

    // ── 아래 4개는 OPEN(오픈채팅방) 전용 — DIRECT 채널에서는 전부 NULL 이다. ──────────

    /** 게시판 탭. NULL 이면 분류 없음(DIRECT 이거나 구버전 행). */
    @Enumerated(EnumType.STRING)
    @Column(name = "category", length = 50)
    private RoomCategory category;

    /** 방장. 나가면 방을 닫는다({@link #close()}). */
    @Column(name = "owner_id", columnDefinition = "BINARY(16)")
    private UUID ownerId;

    @Column(name = "max_members")
    private Integer maxMembers;

    /**
     * 인원 캐시 — 게시판 목록에서 방마다 COUNT 를 돌리지 않기 위한 비정규화 컬럼.
     * 갱신은 반드시 원자적 UPDATE({@code member_count < max_members} 조건)로만 한다.
     * 엔티티 세터를 두지 않는 이유: read-modify-write 하면 동시 입장에 정원이 새기 때문이다.
     */
    @Column(name = "member_count")
    private Integer memberCount;

    private ChannelEntity(String title, ChannelType type, UUID matchId, ChannelStatus status,
        RoomCategory category, UUID ownerId, Integer maxMembers, Integer memberCount) {
        this.title = title;
        this.type = type;
        this.matchId = matchId;
        this.status = status;
        this.category = category;
        this.ownerId = ownerId;
        this.maxMembers = maxMembers;
        this.memberCount = memberCount;
    }

    public static ChannelEntity of(String title) {
        return new ChannelEntity(title, ChannelType.OPEN, null, ChannelStatus.ACTIVE,
            null, null, null, 0);
    }

    /**
     * 오픈채팅방. 만든 사람이 곧 방장이고 첫 멤버이므로 {@code memberCount} 는 1 로 시작한다.
     * (멤버 INSERT 와 같은 트랜잭션에서 만들어야 이 값이 진실이 된다.)
     */
    public static ChannelEntity open(String title, RoomCategory category, UUID ownerId, int maxMembers) {
        return new ChannelEntity(title, ChannelType.OPEN, null, ChannelStatus.ACTIVE,
            category, ownerId, maxMembers, 1);
    }

    public boolean isOpenRoom() {
        return this.type == ChannelType.OPEN && this.ownerId != null;
    }

    public boolean isOwner(UUID userId) {
        return this.ownerId != null && this.ownerId.equals(userId);
    }

    /** 방장이 나가고 남은 멤버가 있을 때 — 방을 닫지 않고 넘긴다. */
    public void transferOwnershipTo(UUID newOwnerId) {
        this.ownerId = newOwnerId;
    }

    /**
     * 매칭으로 생성되는 1:1 채널. 제목은 상대에 따라 클라이언트가 표시하므로 빈 문자열로 둔다.
     * (title 컬럼이 기존 스키마에서 NOT NULL 이라 null 대신 "" 를 넣는다 — ddl-auto=update 는 제약을 완화하지 않는다.)
     */
    public static ChannelEntity direct(UUID matchId) {
        return new ChannelEntity("", ChannelType.DIRECT, matchId, ChannelStatus.ACTIVE,
            null, null, null, null);
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
