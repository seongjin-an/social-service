package com.social.common;

public record UserId(String id) {

    public UserId {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("Invalid UserId");
        }
    }

    public static UserId of(String userId) {
        return new UserId(userId);
    }
}
