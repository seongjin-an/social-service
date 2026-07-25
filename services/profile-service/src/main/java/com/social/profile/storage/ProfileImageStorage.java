package com.social.profile.storage;

import org.springframework.web.multipart.MultipartFile;

/**
 * 이미지 바이트 저장소 추상화.
 * 지금은 로컬 디스크 구현(LocalProfileImageStorage)만 있고,
 * 나중에 MinIO/S3 구현으로 갈아끼우면 서비스 코드는 그대로 둔다.
 */
public interface ProfileImageStorage {

    /** 파일을 저장하고 메타(키/URL 등)를 반환. */
    StoredImage store(MultipartFile file);

    /** 저장된 오브젝트 삭제(롤백/이미지 삭제 시). */
    void delete(String objectKey);
}
