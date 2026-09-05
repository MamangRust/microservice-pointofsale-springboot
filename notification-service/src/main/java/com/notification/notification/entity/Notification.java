package com.notification.notification.entity;

import java.time.LocalDateTime;
import java.util.UUID;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "notifications")
public class Notification {
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;
    
    private UUID userId;
    private String recipient;
    private String title;
    private String message;
    private String type; // e.g. "EMAIL", "SMS", "PUSH"
    private String status; // e.g. "PENDING", "SENT", "FAILED"
    
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();
}
