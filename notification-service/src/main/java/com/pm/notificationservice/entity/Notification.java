package com.pm.notificationservice.entity;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import lombok.*;

import java.time.LocalDateTime;

@Document(collection = "notifications")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString
public class Notification {

    @Id
    private String id;

    private String userId;
    private EventType type;
    private String message;
    private LocalDateTime sentAt;
    private NotificationStatus status;
}
