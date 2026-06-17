package com.pm.accountservice.events;

import lombok.Getter;
import lombok.Setter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@Getter
@Setter
@ConfigurationProperties(prefix = "app.kafka-topics")
public class KafkaTopics {

    private String topicFreezeAccount;
    private String topicUnfreezeAccount;
}