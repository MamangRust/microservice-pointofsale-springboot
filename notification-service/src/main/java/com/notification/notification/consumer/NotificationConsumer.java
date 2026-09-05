package com.notification.notification.consumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import com.notification.notification.config.RabbitConfig;
import com.common.dto.NotificationDto;
import com.notification.notification.service.NotificationService;

@Component
public class NotificationConsumer {
    private static final Logger logger = LoggerFactory.getLogger(NotificationConsumer.class);

    private final NotificationService notificationService;

    public NotificationConsumer(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @RabbitListener(queues = RabbitConfig.NOTIFICATION_QUEUE)
    public void consumeNotificationMessage(NotificationDto notificationDto) {
        logger.info("Received message from RabbitMQ queue: {}", notificationDto);
        try {
            notificationService.sendNotification(notificationDto);
        } catch (Exception e) {
            logger.error("Failed to process consumed notification: {}", e.getMessage(), e);
        }
    }
}
