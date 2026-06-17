package com.pm.transactionservice.events;


import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@Component
@RequiredArgsConstructor
@Slf4j
public class KafkaEventPublisher {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    public void publish(String topic, String key, Object event) {

        ProducerRecord<String, Object> record =
                new ProducerRecord<>(topic, key, event);

        record.headers().add(
                "messageId",
                UUID.randomUUID()
                        .toString()
                        .getBytes(StandardCharsets.UTF_8)
        );

        CompletableFuture<SendResult<String, Object>> future =
                kafkaTemplate.send(record);

        future.whenComplete((res, ex) -> {

            if (ex != null) {
                log.error(
                        "Failed to publish event to topic {}: {}",
                        topic,
                        ex.getMessage(),
                        ex
                );
                return;
            }

            var metadata = res.getRecordMetadata();

            log.info(
                    "Event published. Topic={}, Key={}, Partition={}, Offset={}, Timestamp={}",
                    metadata.topic(),
                    res.getProducerRecord().key(),
                    metadata.partition(),
                    metadata.offset(),
                    metadata.timestamp()
            );
        });
    }
}

