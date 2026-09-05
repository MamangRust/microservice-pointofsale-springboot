package com.filestorage.filestorage.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import com.common.dto.NotificationDto;

@FeignClient(name = "notification-service", path = "/notifications")
public interface NotificationClient {
    
    @PostMapping
    ResponseEntity<Object> sendNotification(@RequestBody NotificationDto notificationDto);
}
