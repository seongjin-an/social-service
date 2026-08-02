package com.social.kafka;

import com.fasterxml.jackson.databind.JsonNode;

public record KafkaEnvelope(String type, JsonNode payload) {

}
