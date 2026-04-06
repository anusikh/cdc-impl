package com.anusikh.cdcink.service;

import com.anusikh.cdcink.model.CdcDocument;
import com.anusikh.cdcink.model.DebeziumEnvelope;
import com.anusikh.cdcink.repository.CdcDocumentRepository;
import com.anusikh.cdcink.service.exception.NonRetryableCdcException;
import com.anusikh.cdcink.service.exception.RetryableCdcException;
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

    public void indexDebeziumEvent(String topic, String key, DebeziumEnvelope envelope) {
        if (envelope == null || envelope.getPayload() == null) {
            log.info("Received heartbeat/tombstone (null payload) from topic: {}", topic);
            return;
        }

        DebeziumEnvelope.Payload payload = envelope.getPayload();
        String operation = payload.getOp();

        // Some connectors emit snapshot reads with null op; treat them as read instead of dropping the first message.
        if (operation == null) {
            operation = "r";
            log.info("Treating null operation as snapshot read for topic: {}", topic);
        }

        CdcDocument document = mapToCdcDocument(topic, key, payload, operation);

        switch (operation) {
            case "c":
            case "u":
            case "r":
                log.info("Indexing {} event for table: {} with ID: {}",
                        operation.equals("c") ? "CREATE" : (operation.equals("u") ? "UPDATE" : "READ"),
                        document.getTable(), document.getId());
                saveDocument(document);
                break;
            case "d":
                log.info("Processing DELETE event for table: {} with ID: {}", document.getTable(), document.getId());
                deleteDocument(document);
                break;
            default:
                throw new NonRetryableCdcException(
                        "Unsupported Debezium operation '" + operation + "' for table " + document.getTable()
                );
        }
    }

    private CdcDocument mapToCdcDocument(String topic, String key, DebeziumEnvelope.Payload payload, String operation) {
        DebeziumEnvelope.Source source = payload.getSource();
        
        Map<String, Object> primaryKey = extractPrimaryKey(payload);

        return CdcDocument.builder()
                .id(generateDocumentId(topic, key, primaryKey))
                .operation(operation)
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

    private String generateDocumentId(String topic, String key, Map<String, Object> primaryKey) {
        if (key != null && !key.isEmpty()) {
            // Clean up the JSON key for use as an ID
            String cleanKey = key.replace("\"", "").replace("{", "").replace("}", "").replace(":", "_").replace(",", "_");
            return topic + "_" + cleanKey;
        }
        if (primaryKey != null && !primaryKey.isEmpty()) {
            String id = primaryKey.toString();
            return topic + "_" + Math.abs(id.hashCode());
        }
        return topic + "_" + UUID.randomUUID().toString();
    }

    private void saveDocument(CdcDocument document) {
        try {
            CdcDocument saved = cdcDocumentRepository.save(document);
            log.debug("Saved document with ID: {} for table: {}", saved.getId(), document.getTable());
        } catch (Exception e) {
            log.error("Failed to save document for table: {}. Error: {}", document.getTable(), e.getMessage(), e);
            throw new RetryableCdcException("Failed to index document to Elasticsearch", e);
        }
    }

    private void deleteDocument(CdcDocument document) {
        try {
            cdcDocumentRepository.deleteById(document.getId());
            log.debug("Deleted document with ID: {} for table: {}", document.getId(), document.getTable());
        } catch (Exception e) {
            log.error("Failed to delete document for table: {}. Error: {}", document.getTable(), e.getMessage(), e);
            throw new RetryableCdcException("Failed to delete document from Elasticsearch", e);
        }
    }
}
