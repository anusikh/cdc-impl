package com.anusikh.cdcink.service;

import com.anusikh.cdcink.model.DebeziumEnvelope;
import com.anusikh.cdcink.service.exception.NonRetryableCdcException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class DebeziumKafkaConsumer {

    private final ElasticsearchIndexingService elasticsearchIndexingService;

    @KafkaListener(
            topicPattern = "${cdc.kafka.topic-pattern}",
            groupId = "${spring.kafka.consumer.group-id}",
            autoStartup = "${cdc.kafka.auto-start:true}"
    )
    public void consumeDebeziumEvent(ConsumerRecord<String, DebeziumEnvelope> record, Acknowledgment acknowledgment) {
        String topic = record.topic();
        String key = record.key();
        DebeziumEnvelope envelope = record.value();

        log.info("Received Debezium event - Topic: {}, Partition: {}, Offset: {}, Key: {}",
                topic, record.partition(), record.offset(), key);

        if (envelope == null) {
            log.warn("Received empty or null record value from topic: {}", topic);
            acknowledgment.acknowledge();
            return;
        }

        validateEnvelope(topic, envelope);
        elasticsearchIndexingService.indexDebeziumEvent(topic, key, envelope);
        log.info("Successfully indexed event from topic: {} with offset: {}", topic, record.offset());
        acknowledgment.acknowledge();
    }

    private void validateEnvelope(String topic, DebeziumEnvelope envelope) {
        if (envelope.getPayload() == null) {
            throw new NonRetryableCdcException("Missing Debezium payload for topic " + topic);
        }

        DebeziumEnvelope.Source source = envelope.getPayload().getSource();
        if (source == null || source.getTable() == null || source.getDb() == null) {
            throw new NonRetryableCdcException("Missing Debezium source metadata for topic " + topic);
        }
    }
}
