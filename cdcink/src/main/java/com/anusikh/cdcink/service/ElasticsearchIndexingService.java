package com.anusikh.cdcink.service;

import com.anusikh.cdcink.model.CdcDocument;
import com.anusikh.cdcink.model.DebeziumEnvelope;
import com.anusikh.cdcink.repository.CdcDocumentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class ElasticsearchIndexingService {

    private final CdcDocumentRepository cdcDocumentRepository;

    public void indexDebeziumEvent(String topic, DebeziumEnvelope envelope) {
        if (envelope == null || envelope.getPayload() == null) {
            log.warn("Received null envelope or payload from topic: {}", topic);
            return;
        }

        DebeziumEnvelope.Payload payload = envelope.getPayload();
        String operation = payload.getOp();

        if (operation == null) {
            log.debug("Skipping read event (snapshot) from topic: {}", topic);
            return;
        }

        CdcDocument document = mapToCdcDocument(topic, payload);
        
        switch (operation) {
            case "c":
                log.info("Processing CREATE event for table: {}", document.getTable());
                saveDocument(document);
                break;
            case "u":
                log.info("Processing UPDATE event for table: {}", document.getTable());
                saveDocument(document);
                break;
            case "d":
                log.info("Processing DELETE event for table: {}", document.getTable());
                deleteDocument(document);
                break;
            case "r":
                log.debug("Processing SNAPSHOT READ event for table: {}", document.getTable());
                saveDocument(document);
                break;
            default:
                log.warn("Unknown operation type: {} for table: {}", operation, document.getTable());
        }
    }

    private CdcDocument mapToCdcDocument(String topic, DebeziumEnvelope.Payload payload) {
        DebeziumEnvelope.Source source = payload.getSource();
        
        Map<String, Object> primaryKey = extractPrimaryKey(payload);

        return CdcDocument.builder()
                .id(generateDocumentId(topic, primaryKey))
                .operation(payload.getOp())
                .table(source != null ? source.getTable() : "unknown")
                .database(source != null ? source.getDb() : "unknown")
                .schema(source != null ? source.getSchema() : "unknown")
                .before(payload.getBefore())
                .after(payload.getAfter())
                .primaryKey(primaryKey)
                .timestamp(Instant.ofEpochMilli(payload.getTsMs() != null ? payload.getTsMs() : System.currentTimeMillis()))
                .connectorName(source != null ? source.getName() : "unknown")
                .build();
    }

    private Map<String, Object> extractPrimaryKey(DebeziumEnvelope.Payload payload) {
        Map<String, Object> primaryKey = new HashMap<>();
        
        if (payload.getAfter() != null) {
            primaryKey.putAll(payload.getAfter());
        } else if (payload.getBefore() != null) {
            primaryKey.putAll(payload.getBefore());
        }
        
        return primaryKey;
    }

    private String generateDocumentId(String topic, Map<String, Object> primaryKey) {
        if (primaryKey != null && !primaryKey.isEmpty()) {
            String id = primaryKey.toString();
            return topic + "_" + Math.abs(id.hashCode());
        }
        return UUID.randomUUID().toString();
    }

    private void saveDocument(CdcDocument document) {
        try {
            CdcDocument saved = cdcDocumentRepository.save(document);
            log.debug("Saved document with ID: {} for table: {}", saved.getId(), document.getTable());
        } catch (Exception e) {
            log.error("Failed to save document for table: {}. Error: {}", document.getTable(), e.getMessage(), e);
            throw new RuntimeException("Failed to index document to Elasticsearch", e);
        }
    }

    private void deleteDocument(CdcDocument document) {
        try {
            cdcDocumentRepository.deleteById(document.getId());
            log.debug("Deleted document with ID: {} for table: {}", document.getId(), document.getTable());
        } catch (Exception e) {
            log.error("Failed to delete document for table: {}. Error: {}", document.getTable(), e.getMessage(), e);
            throw new RuntimeException("Failed to delete document from Elasticsearch", e);
        }
    }
}
