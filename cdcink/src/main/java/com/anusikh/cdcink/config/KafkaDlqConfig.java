package com.anusikh.cdcink.config;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.header.Headers;
import org.apache.kafka.common.header.internals.RecordHeaders;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.boot.kafka.autoconfigure.KafkaProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.listener.CommonErrorHandler;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.ExponentialBackOffWithMaxRetries;
import org.springframework.kafka.support.serializer.JsonSerializer;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

@Configuration
@EnableConfigurationProperties(CdcDlqProperties.class)
public class KafkaDlqConfig {

    @Bean
    public ProducerFactory<String, Object> dlqProducerFactory(KafkaProperties kafkaProperties) {
        Map<String, Object> properties = new HashMap<>(kafkaProperties.buildProducerProperties());
        properties.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        properties.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);
        properties.put(JsonSerializer.ADD_TYPE_INFO_HEADERS, false);
        return new DefaultKafkaProducerFactory<>(properties);
    }

    @Bean
    public KafkaTemplate<String, Object> dlqKafkaTemplate(ProducerFactory<String, Object> dlqProducerFactory) {
        return new KafkaTemplate<>(dlqProducerFactory);
    }

    @Bean
    public DeadLetterPublishingRecoverer deadLetterPublishingRecoverer(
            KafkaTemplate<String, Object> dlqKafkaTemplate,
            CdcDlqProperties dlqProperties
    ) {
        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(
                dlqKafkaTemplate,
                (record, exception) -> new org.apache.kafka.common.TopicPartition(dlqProperties.getAppTopic(), record.partition())
        );
        recoverer.setHeadersFunction(this::dlqHeaders);
        return recoverer;
    }

    @Bean
    public CommonErrorHandler kafkaErrorHandler(
            DeadLetterPublishingRecoverer deadLetterPublishingRecoverer,
            CdcDlqProperties dlqProperties
    ) {
        ExponentialBackOffWithMaxRetries backOff = new ExponentialBackOffWithMaxRetries(dlqProperties.getMaxAttempts());
        backOff.setInitialInterval(dlqProperties.getInitialIntervalMs());
        backOff.setMultiplier(dlqProperties.getMultiplier());
        backOff.setMaxInterval(dlqProperties.getMaxIntervalMs());

        DefaultErrorHandler errorHandler = new DefaultErrorHandler(deadLetterPublishingRecoverer, backOff);
        errorHandler.addNotRetryableExceptions(com.anusikh.cdcink.service.exception.NonRetryableCdcException.class);
        errorHandler.setAckAfterHandle(false);
        return errorHandler;
    }

    private Headers dlqHeaders(ConsumerRecord<?, ?> record, Exception exception) {
        RecordHeaders headers = new RecordHeaders();
        Throwable rootCause = rootCause(exception);
        headers.add("dlq.source", "app".getBytes(StandardCharsets.UTF_8));
        headers.add("dlq.error", rootCause.getClass().getName().getBytes(StandardCharsets.UTF_8));
        headers.add("dlq.error-message", safeMessage(rootCause).getBytes(StandardCharsets.UTF_8));
        headers.add("dlq.timestamp", Instant.now().toString().getBytes(StandardCharsets.UTF_8));
        headers.add("dlq.original-topic", record.topic().getBytes(StandardCharsets.UTF_8));
        return headers;
    }

    private Throwable rootCause(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        return current;
    }

    private String safeMessage(Throwable throwable) {
        return throwable.getMessage() == null ? "" : throwable.getMessage();
    }
}
