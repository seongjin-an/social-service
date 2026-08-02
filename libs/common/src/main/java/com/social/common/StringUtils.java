package com.social.common;

import java.util.UUID;
import java.util.stream.Stream;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class StringUtils {

    public static String minStr(String a, String b) {
        return Stream.of(a, b)
            .min(String::compareTo)
            .orElseThrow();
    }

    public static String maxStr(String a, String b) {
        return Stream.of(a, b)
            .max(String::compareTo)
            .orElseThrow();
    }

    public static UUID minUuid(UUID a, UUID b) {
        return Stream.of(a, b).min(UUID::compareTo).orElseThrow();
    }

    public static UUID maxUuid(UUID a, UUID b) {
        return Stream.of(a, b).max(UUID::compareTo).orElseThrow();
    }

    public static UUID fromUuid(String str) {
        try {
            return UUID.fromString(str);
        } catch (IllegalArgumentException e) {
            log.error("error parsing from string to uuid");
            throw e;
        }
    }
}
