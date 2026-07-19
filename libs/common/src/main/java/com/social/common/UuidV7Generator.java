package com.social.common;

import com.github.f4b6a3.uuid.UuidCreator;
import java.util.UUID;

public class UuidV7Generator {
    public static UUID generate() {
        return UuidCreator.getTimeOrderedEpoch();   // ← RFC 9562 UUID v7
    }
}
