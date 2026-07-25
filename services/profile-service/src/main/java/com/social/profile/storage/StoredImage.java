package com.social.profile.storage;

/** 스토리지에 저장된 이미지의 메타 — ProfileImageEntity 로 매핑된다. */
public record StoredImage(
    String originalFileName,
    String storedFileName,
    String objectKey,
    String imageUrl,
    String contentType,
    long fileSize
) {
}
