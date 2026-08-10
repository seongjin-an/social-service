package com.social.message.service.openchat;

import com.social.message.domain.RoomCategory;
import java.util.UUID;

public record CreateRoom(String title, RoomCategory category, int maxMembers, UUID ownerId) {

    public static CreateRoom of(String title, RoomCategory category, int maxMembers, UUID ownerId) {
        return new CreateRoom(title, category, maxMembers, ownerId);
    }
}
