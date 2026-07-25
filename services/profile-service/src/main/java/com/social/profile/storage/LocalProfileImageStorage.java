package com.social.profile.storage;

import com.social.common.UuidV7Generator;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

/**
 * 로컬 디스크 저장 구현(데모용).
 *   - base-dir 아래 profile/{uuid}.{ext} 로 저장
 *   - imageUrl 은 public-base-url + objectKey (WebConfig 리소스 핸들러가 서빙)
 * 운영 전환 시 이 클래스만 MinIO/S3 구현으로 교체하면 된다.
 */
@Component
public class LocalProfileImageStorage implements ProfileImageStorage {

    private final Path baseDir;
    private final String publicBaseUrl;

    public LocalProfileImageStorage(
        @Value("${app.image.base-dir}") String baseDir,
        @Value("${app.image.public-base-url}") String publicBaseUrl
    ) {
        this.baseDir = Path.of(baseDir).toAbsolutePath().normalize();
        this.publicBaseUrl = stripTrailingSlash(publicBaseUrl);
    }

    @Override
    public StoredImage store(MultipartFile file) {
        String storedFileName = UuidV7Generator.generate() + extension(file);
        String objectKey = "profile/" + storedFileName;
        Path dest = baseDir.resolve(objectKey).normalize();

        try (InputStream in = file.getInputStream()) {
            Files.createDirectories(dest.getParent());
            Files.copy(in, dest, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            // 쓰다 만 부분 파일 정리 (이 파일은 아직 objectKey 반환 전이라 상위 보상 훅이 못 잡음)
            try {
                Files.deleteIfExists(dest);
            } catch (IOException ignore) {
                // best-effort
            }
            throw new UncheckedIOException("이미지 저장 실패: " + file.getOriginalFilename(), e);
        }

        return new StoredImage(
            file.getOriginalFilename(),
            storedFileName,
            objectKey,
            publicBaseUrl + "/" + objectKey,
            file.getContentType(),
            file.getSize()
        );
    }

    @Override
    public void delete(String objectKey) {
        try {
            Files.deleteIfExists(baseDir.resolve(objectKey).normalize());
        } catch (IOException e) {
            throw new UncheckedIOException("이미지 삭제 실패: " + objectKey, e);
        }
    }

    private String extension(MultipartFile file) {
        String original = StringUtils.cleanPath(
            file.getOriginalFilename() == null ? "" : file.getOriginalFilename());
        String ext = StringUtils.getFilenameExtension(original);
        return (ext == null || ext.isBlank()) ? "" : "." + ext.toLowerCase();
    }

    private String stripTrailingSlash(String url) {
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }
}
