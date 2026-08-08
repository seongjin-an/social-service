package com.social.common;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class JsonUtil {

    /**
     * 서비스 간 이벤트/캐시 JSON 전용 매퍼 (HTTP 요청 바인딩은 Spring MVC 의 매퍼가 따로 쓴다).
     *
     * <p>모르는 필드는 무시한다 — 생산자가 페이로드에 필드를 하나 추가했을 때 소비자가 배포되기 전이라도
     * 깨지지 않아야 한다(전진 호환). 이게 없으면 필드 추가가 곧 컨슈머 장애다.
     */
    private final ObjectMapper objectMapper = new ObjectMapper()
        .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

    public <T> Optional<T> fromJson(String json, Class<T> clazz) {
        try {
            return Optional.ofNullable(objectMapper.readValue(json, clazz));
        } catch (Exception e) {
            log.error("Failed to parse json object: {}", e.getMessage());
            return Optional.empty();
        }
    }

    public <T> List<T> fromJsonToList(String json, Class<T> clazz) {
        try {
            return objectMapper.readerForListOf(clazz).readValue(json);
        } catch (JsonProcessingException e) {
            log.error("Failed to parse json list: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    public Optional<String> toJson(Object object) {
        try {
            return Optional.ofNullable(objectMapper.writeValueAsString(object));
        } catch (JsonProcessingException e) {
            log.error("Failed to convert object to json: {}", e.getMessage());
            return Optional.empty();
        }
    }

    public <T> Optional<JsonNode> convertJsonNode(T data) {
        try {
            return Optional.ofNullable(objectMapper.valueToTree(data));
        } catch (Exception e) {
            log.error("Failed to convert object to json: {}", e.getMessage());
            return Optional.empty();
        }
    }
}
