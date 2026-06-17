package com.pm.notificationservice.listener;

import com.pm.notificationservice.events.AccountFreezeEvent;
import com.pm.notificationservice.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaHandler;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@KafkaListener(topics = "${app.kafka-topics.topic-account-status-changed}", groupId = "notification-service")
@RequiredArgsConstructor
public class AccountStatusChangedListener {

    private final NotificationService notificationService;

    @KafkaHandler
    public void handle(@Payload AccountFreezeEvent event, @Header("messageId") String messageId) {
        log.info("=== ENTERED handle(AccountFreezeEvent) ===");
        log.info("Event received: {}", event);
        notificationService.handleAccountFrozen(event);
        log.info("=== FINISHED handle(AccountFreezeEvent) ===");
    }
}
