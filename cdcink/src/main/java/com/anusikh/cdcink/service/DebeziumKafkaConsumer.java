package com.anusikh.cdcink.service;

import com.anusikh.cdcink.model.DebeziumEnvelope;
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

        log.warn("DLQ test marker – entering listener: topic={}, partition={}, offset={}, key={}",
                topic, record.partition(), record.offset(), key);

        log.info("Received Debezium event - Topic: {}, Partition: {}, Offset: {}, Key: {}",
                topic, record.partition(), record.offset(), key);

        try {
            if (envelope != null) {
                elasticsearchIndexingService.indexDebeziumEvent(topic, key, envelope);
                log.info("Successfully indexed event from topic: {} with offset: {}", topic, record.offset());
            } else {
                log.warn("Received empty or null record value from topic: {}", topic);
            }
            acknowledgment.acknowledge();
        } catch (Exception e) {
            log.error("Error processing event from topic: {}. Error: {}", topic, e.getMessage());
            throw e;
        }
    }
}
