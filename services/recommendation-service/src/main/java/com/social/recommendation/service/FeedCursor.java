package com.social.recommendation.service;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Optional;

/**
 * 피드 커서 — {@code base64url("{스냅샷버전}:{offset}")}.
 *
 * <p>버전을 같이 실어야 하는 이유: 스냅샷이 TTL 로 사라진 뒤 재계산되면 같은 offset 이 전혀 다른
 * 사람을 가리킨다. 버전이 어긋난 커서는 무효로 보고 처음부터 다시 준다(조용히 이상한 페이지를
 * 주는 것보다 낫다).
 *
 * <p>불투명(base64) 하게 만든 건 클라이언트가 offset 을 직접 만들어 쓰지 못하게 하려는 의도다 —
 * 커서 형식은 서버 구현 사항이고 나중에 바뀔 수 있다.
 */
public record FeedCursor(String version, int offset) {

    public String encode() {
        String raw = version + ":" + offset;
        return Base64.getUrlEncoder().withoutPadding()
            .encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }

    /** 해독 불가/형식 오류는 empty — 400 으로 튕기지 않고 첫 페이지로 취급한다. */
    public static Optional<FeedCursor> decode(String cursor) {
        if (cursor == null || cursor.isBlank()) {
            return Optional.empty();
        }
        try {
            String raw = new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8);
            int separator = raw.lastIndexOf(':');
            if (separator < 0) {
                return Optional.empty();
            }
            int offset = Integer.parseInt(raw.substring(separator + 1));
            if (offset < 0) {
                return Optional.empty();
            }
            return Optional.of(new FeedCursor(raw.substring(0, separator), offset));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }
}
