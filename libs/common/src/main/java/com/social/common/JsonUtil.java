package com.social.common;

import com.fasterxml.jackson.core.JsonProcessingException;
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

    private final ObjectMapper objectMapper = new ObjectMapper();

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
