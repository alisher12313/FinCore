package com.pm.notificationservice.service;

import com.pm.notificationservice.entity.EventType;
import com.pm.notificationservice.entity.Notification;
import com.pm.notificationservice.entity.NotificationStatus;
import com.pm.notificationservice.events.AccountFreezeEvent;
import com.pm.notificationservice.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationRepository notificationRepository;

    public void handleAccountFrozen(AccountFreezeEvent event) {
        NotificationStatus status;
        try {
            // later: emailService.sendFrozenNotification(...)
            status = NotificationStatus.SENT;
        } catch (Exception e) {
            log.error("Failed to send notification: {}", e.getMessage());
            status = NotificationStatus.FAILED;
        }

        Notification notification = Notification.builder()
                .userId(event.getUserId().toString())
                .type(EventType.FROZEN)
                .message("Your account " + event.getAccountNumber() + " has been frozen.")
                .sentAt(LocalDateTime.now())
                .status(status)
                .build();

        notificationRepository.save(notification);
    }
}
