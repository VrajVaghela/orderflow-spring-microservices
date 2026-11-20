package com.orderflow.common;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

public final class EventSerde {

    private final ObjectMapper mapper;

    public EventSerde() {
        this.mapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .setSerializationInclusion(JsonInclude.Include.NON_NULL);
    }

    public String write(EventEnvelope envelope) {
        try {
            return mapper.writeValueAsString(envelope);
        } catch (Exception ex) {
            throw new IllegalStateException("failed to serialize event", ex);
        }
    }

    public EventEnvelope read(String json) {
        try {
            return mapper.readValue(json, EventEnvelope.class);
        } catch (Exception ex) {
            throw new PermanentMessageException("event payload is not a valid envelope", ex);
        }
    }

    public JsonNode toNode(Object payload) {
        return mapper.valueToTree(payload);
    }

    public <T> T convert(JsonNode node, Class<T> type) {
        try {
            return mapper.convertValue(node, type);
        } catch (IllegalArgumentException ex) {
            throw new PermanentMessageException("event payload does not match " + type.getSimpleName(), ex);
        }
    }
}
