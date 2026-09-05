package com.notification.notification.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.common.dto.NotificationDto;
import com.notification.notification.entity.Notification;
import com.notification.notification.service.NotificationService;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

@ExtendWith(MockitoExtension.class)
class NotificationControllerTest {

    private static final String USER_ID = "3f2b6c1e-1a2b-3c4d-5e6f-7a8b9c0d1e2f";
    private static final String NOTIFICATION_ID = "7a1c9d2e-4b5f-6a7b-8c9d-0e1f2a3b4c5d";

    @Mock
    private NotificationService notificationService;

    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = JsonMapper.builder()
            .addModule(new JavaTimeModule())
            .build();

    @BeforeEach
    void setUp() {
        NotificationController controller = new NotificationController(notificationService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setMessageConverters(new MappingJackson2HttpMessageConverter(objectMapper))
                .build();
    }

    private Notification notification() {
        return Notification.builder()
                .id(UUID.fromString(NOTIFICATION_ID))
                .userId(UUID.fromString(USER_ID))
                .recipient("user@example.com")
                .title("Test Title")
                .message("Hello world")
                .type("EMAIL")
                .status("SENT")
                .createdAt(LocalDateTime.of(2026, 9, 4, 10, 0))
                .build();
    }

    @Test
    void sendNotification_returns201WithCreatedEntity() throws Exception {
        when(notificationService.sendNotification(any(NotificationDto.class))).thenReturn(notification());

        mockMvc.perform(post("/notifications")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":\"" + USER_ID + "\",\"recipient\":\"user@example.com\","
                                + "\"title\":\"Test Title\",\"message\":\"Hello world\",\"type\":\"EMAIL\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(NOTIFICATION_ID))
                .andExpect(jsonPath("$.userId").value(USER_ID))
                .andExpect(jsonPath("$.title").value("Test Title"))
                .andExpect(jsonPath("$.status").value("SENT"))
                .andExpect(jsonPath("$.createdAt[0]").value(2026));

        verify(notificationService).sendNotification(any(NotificationDto.class));
    }

    @Test
    void sendNotification_returns400WhenTitleBlank() throws Exception {
        mockMvc.perform(post("/notifications")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":\"" + USER_ID + "\",\"recipient\":\"user@example.com\","
                                + "\"title\":\" \",\"message\":\"Hello world\",\"type\":\"EMAIL\"}"))
                .andExpect(status().isBadRequest());

        verify(notificationService, never()).sendNotification(any(NotificationDto.class));
    }

    @Test
    void sendNotification_returns400WhenUserIdMissing() throws Exception {
        mockMvc.perform(post("/notifications")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"recipient\":\"user@example.com\","
                                + "\"title\":\"Test Title\",\"message\":\"Hello world\",\"type\":\"EMAIL\"}"))
                .andExpect(status().isBadRequest());

        verify(notificationService, never()).sendNotification(any(NotificationDto.class));
    }

    @Test
    void sendNotification_returns400WhenMessageMissing() throws Exception {
        mockMvc.perform(post("/notifications")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":\"" + USER_ID + "\",\"recipient\":\"user@example.com\","
                                + "\"title\":\"Test Title\",\"type\":\"EMAIL\"}"))
                .andExpect(status().isBadRequest());

        verify(notificationService, never()).sendNotification(any(NotificationDto.class));
    }

    @Test
    void getAllNotifications_returnsList() throws Exception {
        when(notificationService.getAllNotifications()).thenReturn(List.of(notification()));

        mockMvc.perform(get("/notifications"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(NOTIFICATION_ID))
                .andExpect(jsonPath("$[0].status").value("SENT"));
    }

    @Test
    void getAllNotifications_returnsEmptyListWhenNone() throws Exception {
        when(notificationService.getAllNotifications()).thenReturn(List.of());

        mockMvc.perform(get("/notifications"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    void getNotificationsByUserId_returnsList() throws Exception {
        when(notificationService.getNotificationsByUserId(UUID.fromString(USER_ID)))
                .thenReturn(List.of(notification()));

        mockMvc.perform(get("/notifications/user/" + USER_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(NOTIFICATION_ID))
                .andExpect(jsonPath("$[0].userId").value(USER_ID));
    }

    @Test
    void getNotificationsByUserId_returnsEmptyListWhenNone() throws Exception {
        when(notificationService.getNotificationsByUserId(UUID.fromString(USER_ID))).thenReturn(List.of());

        mockMvc.perform(get("/notifications/user/" + USER_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    void getNotificationById_returnsEntity() throws Exception {
        when(notificationService.getNotificationById(UUID.fromString(NOTIFICATION_ID))).thenReturn(notification());

        mockMvc.perform(get("/notifications/" + NOTIFICATION_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(NOTIFICATION_ID))
                .andExpect(jsonPath("$.message").value("Hello world"));
    }
}
