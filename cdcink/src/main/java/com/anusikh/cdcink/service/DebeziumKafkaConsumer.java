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

        log.debug("Received Debezium event - Topic: {}, Key: {}, Partition: {}, Offset: {}",
                topic, key, record.partition(), record.offset());

        try {
            elasticsearchIndexingService.indexDebeziumEvent(topic, envelope);
            acknowledgment.acknowledge();
            log.debug("Successfully processed and acknowledged message from topic: {}", topic);
        } catch (Exception e) {
            log.error("Failed to process Debezium event from topic: {}. Error: {}", topic, e.getMessage(), e);
            throw e;
        }
    }
}
