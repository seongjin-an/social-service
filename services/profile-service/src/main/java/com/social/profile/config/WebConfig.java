package com.social.profile.config;

import java.nio.file.Path;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 로컬 디스크에 저장한 프로필 이미지를 /files/** 로 서빙(데모용).
 * MinIO/S3 로 가면 이 핸들러는 필요 없어진다(스토리지/CDN 이 직접 서빙).
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final String baseDir;

    public WebConfig(@Value("${app.image.base-dir}") String baseDir) {
        this.baseDir = Path.of(baseDir).toAbsolutePath().normalize().toString();
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/files/**")
            .addResourceLocations("file:" + baseDir + "/");
    }
}
