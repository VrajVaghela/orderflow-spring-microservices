package com.orderflow.query.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Document("processed_events")
public class ProcessedEventDocument {

    @Id
    private String id;

    public ProcessedEventDocument() {
    }

    public ProcessedEventDocument(String id) {
        this.id = id;
    }

    public String getId() {
        return id;
    }
}
