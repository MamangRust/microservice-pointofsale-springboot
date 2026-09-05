package com.notification.notification.service;

import java.util.List;
import java.util.UUID;

import com.common.dto.NotificationDto;
import com.notification.notification.entity.Notification;

public interface NotificationService {
    Notification sendNotification(NotificationDto notificationDto);
    List<Notification> getAllNotifications();
    List<Notification> getNotificationsByUserId(UUID userId);
    Notification getNotificationById(UUID id);
}
