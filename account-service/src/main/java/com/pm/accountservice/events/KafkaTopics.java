package com.pm.accountservice.events;

import lombok.Getter;
import lombok.Setter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@Getter
@Setter
public class KafkaTopics {

    @Value("${app.kafka-topics.topic-change-account-status}")
    private String topicAccountStatusChanged;
}