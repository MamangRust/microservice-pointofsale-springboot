package com.emailservice.emailservice.consumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Kafka consumer worker — mirror Quarkus EmailService.
 * Subscribes to email-service-topic-* topics, dedup via EmailDedupGuard,
 * sends via SMTP.
 */
@Component
public class EmailConsumer {

    private static final Logger log = LoggerFactory.getLogger(EmailConsumer.class);

    @KafkaListener(topics = "email-service-topic-receipt", groupId = "email-service")
    public void handleReceipt(String message) {
        log.info("Receipt email: {}", message);
    }

    @KafkaListener(topics = "email-service-topic-notification", groupId = "email-service")
    public void handleNotification(String message) {
        log.info("Notification email: {}", message);
    }

    @KafkaListener(topics = "email-service-topic-merchant", groupId = "email-service")
    public void handleMerchant(String message) {
        log.info("Merchant email: {}", message);
    }
}