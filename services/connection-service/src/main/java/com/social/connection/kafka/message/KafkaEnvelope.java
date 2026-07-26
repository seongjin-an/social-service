package com.social.connection.kafka.message;

import com.fasterxml.jackson.databind.JsonNode;

public record KafkaEnvelope(String type, JsonNode payload) {}
