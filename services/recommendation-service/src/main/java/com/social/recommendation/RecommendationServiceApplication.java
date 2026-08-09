package com.social.recommendation;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * {@code scanBasePackages = "com.social"} 이 필요하다 — 공용 모듈의 빈(JsonUtil 등)이
 * {@code com.social.common} 에 있어서 기본 스캔 범위({@code com.social.recommendation})로는 잡히지 않는다.
 * (다른 서비스들도 같은 이유로 같은 설정을 쓴다.)
 */
@SpringBootApplication(scanBasePackages = {"com.social"})
public class RecommendationServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(RecommendationServiceApplication.class, args);
    }
}
