package com.pm.notificationservice.events;

import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
@ToString
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AccountFreezeEvent {
    private UUID accountId;
    private UUID userId;
    private String accountNumber;
    private Instant freezeTime;
}
