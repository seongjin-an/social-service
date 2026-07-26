package com.social.fanout.kafka.message;

import com.fasterxml.jackson.databind.JsonNode;

public record KafkaEnvelope(String type, JsonNode payload) {}
