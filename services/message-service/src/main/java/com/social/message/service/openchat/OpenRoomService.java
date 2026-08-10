package com.social.message.service.openchat;

import com.social.common.exception.BusinessException;
import com.social.message.domain.ChannelEntity;
import com.social.message.domain.ChannelMemberEntity;
import com.social.message.domain.RoomCategory;
import com.social.message.repository.channel.ChannelRepository;
import com.social.message.repository.channelmember.ChannelMemberRepository;
import com.social.message.service.channelmember.ChannelMemberCacheService;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 오픈채팅 게시판 — 방을 만들고 찾고 들어가고 나가는 것까지가 이 서비스의 책임이다.
 *
 * <p><b>대화 자체는 여기 없다.</b> 오픈방도 {@code channel}·{@code channel_members}·{@code message}
 * 를 그대로 쓰기 때문에, 멤버가 {@code channel:members:{channelId}} SET 에 들어가는 순간
 * 기존 파이프라인(message-relay → outbox → message-fanout → connection-instance-* → WS)이
 * 1:1 채팅과 <b>완전히 동일하게</b> 단체 대화를 실어 나른다. 그래서 connection-service 와
 * fanout-delivery-service 는 오픈채팅 때문에 고칠 것이 없다.
 *
 * <p>설계 문서(schema.sql 메모 F)의 "별도 openchat-service 로 뺄지"는 여기로 정리했다 —
 * 소유 테이블이 하나도 없어서 서비스를 나누면 방 생성/입장이 두 서비스에 걸친 분산 트랜잭션이 된다.
 * 같은 서비스 안에 두면 채널 INSERT 와 방장 멤버 INSERT 가 로컬 트랜잭션 하나로 끝난다.
 */
@Slf4j
@RequiredArgsConstructor
@Transactional(readOnly = true)
@Service
public class OpenRoomService {

    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 50;

    private final ChannelRepository channelRepository;
    private final ChannelMemberRepository channelMemberRepository;
    private final ChannelMemberCacheService channelMemberCacheService;
    private final RoomPresenceService roomPresenceService;

    /** F3-1 방 생성 — 만든 사람이 방장이자 첫 멤버. 채널과 멤버가 한 트랜잭션이라 반쪽 방이 생기지 않는다. */
    @Transactional
    public OpenRoom create(CreateRoom command) {
        ChannelEntity room = channelRepository.saveAndFlush(ChannelEntity.open(
            command.title().trim(), command.category(), command.ownerId(), command.maxMembers()));

        channelMemberRepository.save(ChannelMemberEntity.owner(room.getChannelId(), command.ownerId()));
        channelMemberCacheService.invalidateAfterCommit(room.getChannelId());

        log.info("[OpenRoom] 생성: channelId={}, category={}, ownerId={}, maxMembers={}",
            room.getChannelId(), command.category(), command.ownerId(), command.maxMembers());

        long online = roomPresenceService.countOnline(List.of(command.ownerId()));
        return OpenRoom.of(room, online, true, true);
    }

    /** F3-2 방 탐색 — 카테고리 탭 + 제목 검색 + channelId 내림차순 키셋 커서. */
    public OpenRoomPage browse(RoomCategory category, String keyword, Long cursor, Integer size,
        UUID viewerId) {
        int limit = normalizeSize(size);
        long from = startFrom(cursor);
        String q = keyword == null ? "" : keyword.trim();
        Pageable window = PageRequest.of(0, limit + 1);   // +1 로 "다음 페이지 있음"만 판정한다

        List<ChannelEntity> found = (category == null)
            ? channelRepository.browseOpenRooms(q, from, window)
            : channelRepository.browseOpenRoomsByCategory(category, q, from, window);

        return toPage(found, limit, viewerId);
    }

    /** 내가 들어가 있는 방 목록. */
    public OpenRoomPage myRooms(UUID userId, Long cursor, Integer size) {
        int limit = normalizeSize(size);
        List<ChannelEntity> found = channelRepository.findMyOpenRooms(
            userId, startFrom(cursor), PageRequest.of(0, limit + 1));
        return toPage(found, limit, userId);
    }

    public OpenRoom get(Long channelId, UUID viewerId) {
        ChannelEntity room = requireOpenRoom(channelId);
        List<UUID> members = channelMemberRepository.findUserIdsByChannelId(channelId);
        return OpenRoom.of(room, roomPresenceService.countOnline(members),
            members.contains(viewerId), room.isOwner(viewerId));
    }

    /** F3-4 presence — 가입 인원과 접속 인원을 따로 준다(둘은 다른 값이고, 다른 걸 뜻한다). */
    public RoomPresence presence(Long channelId) {
        requireOpenRoom(channelId);
        List<UUID> members = channelMemberRepository.findUserIdsByChannelId(channelId);
        return new RoomPresence(channelId, members.size(), roomPresenceService.countOnline(members));
    }

    /**
     * F3-3 입장. 멱등하고, 정원을 넘기지 않는다.
     *
     * <p>순서가 중요하다 — <b>자리를 먼저 예약하고</b> 멤버를 INSERT 한다.
     * 반대로 하면 "멤버는 들어갔는데 정원이 꽉 차서 카운트만 못 올린" 상태가 남는다.
     * 예약은 {@code member_count < max_members} 를 UPDATE 의 WHERE 에 넣은 원자적 연산이라,
     * 남은 1자리에 100명이 동시에 몰려도 정확히 1명만 통과한다.
     */
    @Transactional
    public OpenRoom join(Long channelId, UUID userId) {
        requireOpenRoom(channelId);

        // 이미 멤버면 아무것도 하지 않는다 — 입장 버튼 연타에 member_count 가 부풀지 않게.
        if (channelMemberRepository.existsByChannelIdAndUserId(channelId, userId)) {
            return get(channelId, userId);
        }

        if (channelRepository.reserveSeat(channelId) == 0) {
            throw BusinessException.conflict("정원이 가득 찼습니다.");
        }

        channelMemberRepository.save(ChannelMemberEntity.member(channelId, userId));
        channelMemberCacheService.invalidateAfterCommit(channelId);

        log.info("[OpenRoom] 입장: channelId={}, userId={}", channelId, userId);
        return get(channelId, userId);
    }

    /**
     * F3-3 퇴장. 멱등(멤버가 아니면 no-op).
     *
     * <p>방장이 나가면 방을 닫는 대신 <b>최초 입장자에게 넘긴다</b> — 남은 사람들의 대화를
     * 방장 사정으로 끊지 않기 위해서다. 아무도 안 남으면 그때 닫는다(게시판에서 사라지고,
     * {@code MessageRelayHandler} 의 CLOSED 가드가 잔여 전송을 막는다).
     */
    @Transactional
    public void leave(Long channelId, UUID userId) {
        ChannelEntity room = requireOpenRoom(channelId);

        if (!channelMemberRepository.existsByChannelIdAndUserId(channelId, userId)) {
            return;
        }

        channelMemberRepository.deleteByChannelIdAndUserId(channelId, userId);

        if (room.isOwner(userId)) {
            handOverOrClose(room, userId);
        }

        // 엔티티 변경(양도/폐쇄)은 releaseSeat 의 flushAutomatically 로 먼저 flush 된다.
        channelRepository.releaseSeat(channelId);
        channelMemberCacheService.invalidateAfterCommit(channelId);

        log.info("[OpenRoom] 퇴장: channelId={}, userId={}", channelId, userId);
    }

    private void handOverOrClose(ChannelEntity room, UUID leavingOwnerId) {
        List<ChannelMemberEntity> successors = channelMemberRepository.findSuccessorCandidates(
            room.getChannelId(), leavingOwnerId, PageRequest.of(0, 1));

        if (successors.isEmpty()) {
            room.close();
            log.info("[OpenRoom] 방장 퇴장 + 남은 멤버 없음 → 방 닫음: channelId={}", room.getChannelId());
            return;
        }

        ChannelMemberEntity successor = successors.get(0);
        successor.promoteToOwner();
        room.transferOwnershipTo(successor.getUserId());
        log.info("[OpenRoom] 방장 양도: channelId={}, {} → {}",
            room.getChannelId(), leavingOwnerId, successor.getUserId());
    }

    /**
     * 조회한 방들의 멤버·접속 상태를 <b>DB 1회 + Redis 1회</b>로 채운다.
     * 방마다 따로 물어보면 게시판 한 장(20개)에 쿼리 20번, Redis 왕복 수백 번이 난다.
     */
    private OpenRoomPage toPage(List<ChannelEntity> found, int limit, UUID viewerId) {
        boolean hasNext = found.size() > limit;
        List<ChannelEntity> rooms = hasNext ? found.subList(0, limit) : found;

        if (rooms.isEmpty()) {
            return OpenRoomPage.of(List.of(), null);
        }

        List<Long> channelIds = rooms.stream().map(ChannelEntity::getChannelId).toList();
        Map<Long, List<UUID>> membersByChannel = channelMemberRepository.findByChannelIdIn(channelIds)
            .stream()
            .collect(Collectors.groupingBy(ChannelMemberEntity::getChannelId,
                Collectors.mapping(ChannelMemberEntity::getUserId, Collectors.toList())));

        Set<String> online = roomPresenceService.onlineAmong(
            membersByChannel.values().stream()
                .flatMap(List::stream)
                .map(UUID::toString)
                .toList());

        List<OpenRoom> items = rooms.stream()
            .map(room -> {
                List<UUID> members = membersByChannel.getOrDefault(room.getChannelId(), List.of());
                long onlineCount = members.stream().filter(id -> online.contains(id.toString())).count();
                return OpenRoom.of(room, onlineCount, members.contains(viewerId), room.isOwner(viewerId));
            })
            .toList();

        Long nextCursor = hasNext ? rooms.get(rooms.size() - 1).getChannelId() : null;
        return OpenRoomPage.of(items, nextCursor);
    }

    /**
     * 오픈방 API 가 만질 수 있는 채널인지 확인한다. 세 가지를 모두 "없는 방"(404)으로 응답한다:
     *
     * <ol>
     *   <li>존재하지 않는 channelId</li>
     *   <li><b>DIRECT(1:1 매칭방)</b> — 막지 않으면 channelId 만 바꿔 남의 매칭 대화방에
     *       입장하는 경로가 된다. 403 이 아니라 404 인 이유는 존재 여부조차 흘리지 않기 위해서다.</li>
     *   <li><b>CLOSED</b> — 닫힌 방은 게시판에도 내 방 목록에도 안 나오므로 없는 것으로 취급한다.
     *       200 으로 돌려주면 프론트가 "입장하기"를 그리고, 누르면 반드시 실패한다.
     *       (닫힌 오픈방은 항상 멤버 0명이다 — 마지막 멤버가 나갈 때만 닫히므로.)</li>
     * </ol>
     */
    private ChannelEntity requireOpenRoom(Long channelId) {
        ChannelEntity channel = channelRepository.findById(channelId)
            .orElseThrow(() -> BusinessException.notFound("방을 찾을 수 없습니다."));

        if (!channel.isOpenRoom() || channel.isClosed()) {
            throw BusinessException.notFound("방을 찾을 수 없습니다.");
        }
        return channel;
    }

    /** 커서가 없으면 "가장 큰 channelId 부터" = 첫 페이지. null 을 JPQL 로 넘기지 않으려는 정규화. */
    private long startFrom(Long cursor) {
        return cursor != null ? cursor : Long.MAX_VALUE;
    }

    private int normalizeSize(Integer size) {
        if (size == null || size <= 0) {
            return DEFAULT_PAGE_SIZE;
        }
        return Math.min(size, MAX_PAGE_SIZE);
    }
}
