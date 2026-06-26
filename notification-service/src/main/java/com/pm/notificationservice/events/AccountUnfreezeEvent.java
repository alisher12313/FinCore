package com.pm.notificationservice.events;


import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AccountUnfreezeEvent {
    private UUID accountId;
    private UUID userId;
    private String accountNumber;
    private Instant unfreezeTime;
    private String email;
}

