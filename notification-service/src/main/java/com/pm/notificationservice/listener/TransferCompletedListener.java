package com.pm.notificationservice.listener;

import com.pm.notificationservice.events.TransferCompletedEvent;
import com.pm.notificationservice.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@Slf4j
@RequiredArgsConstructor
public class TransferCompletedListener {

    private final NotificationService notificationService;

    @KafkaListener(
            topics = "${app.kafka-topics.transaction-complete-events-topic}",
            groupId = "notification-service"
    )
    public void consume(TransferCompletedEvent event) {
        log.info("Received transfer completed event: {}", event);
        notificationService.handleTransferComplete(event);
    }
}
