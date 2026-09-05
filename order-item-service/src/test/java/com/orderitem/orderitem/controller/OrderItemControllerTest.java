package com.orderitem.orderitem.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.orderitem.orderitem.dto.OrderItemMapper;
import com.orderitem.orderitem.dto.OrderItemMapperImpl;
import com.orderitem.orderitem.dto.OrderItemRequest;
import com.orderitem.orderitem.entity.OrderItem;
import com.orderitem.orderitem.exc.GeneralExceptionHandler;
import com.orderitem.orderitem.service.OrderItemService;

@ExtendWith(MockitoExtension.class)
class OrderItemControllerTest {

    @Mock
    private OrderItemService service;

    private final OrderItemMapper mapper = new OrderItemMapperImpl();

    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = JsonMapper.builder()
            .addModule(new JavaTimeModule())
            .build();

    @BeforeEach
    void setUp() {
        OrderItemController controller = new OrderItemController(service, mapper);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GeneralExceptionHandler())
                .setMessageConverters(new MappingJackson2HttpMessageConverter(objectMapper))
                .build();
    }

    private OrderItem item(Long id, Long orderId, Long productId, Integer quantity, Integer price) {
        OrderItem item = new OrderItem();
        item.setOrderItemId(id);
        item.setOrderId(orderId);
        item.setProductId(productId);
        item.setQuantity(quantity);
        item.setPrice(price);
        return item;
    }

    @Test
    void getAll_returnsMappedList() throws Exception {
        when(service.getAll()).thenReturn(List.of(item(1L, 10L, 100L, 2, 500)));

        mockMvc.perform(get("/order-items"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].orderItemId").value(1))
                .andExpect(jsonPath("$[0].orderId").value(10))
                .andExpect(jsonPath("$[0].productId").value(100))
                .andExpect(jsonPath("$[0].quantity").value(2))
                .andExpect(jsonPath("$[0].price").value(500));
    }

    @Test
    void getAll_returnsEmptyListWhenNone() throws Exception {
        when(service.getAll()).thenReturn(List.of());

        mockMvc.perform(get("/order-items"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    void getByOrderId_returnsMappedList() throws Exception {
        when(service.getByOrderId(10L)).thenReturn(List.of(
                item(1L, 10L, 100L, 2, 500),
                item(2L, 10L, 101L, 1, 250)));

        mockMvc.perform(get("/order-items/order/10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].orderId").value(10))
                .andExpect(jsonPath("$[1].orderItemId").value(2));
    }

    @Test
    void getByOrderId_returnsEmptyListWhenNoMatch() throws Exception {
        when(service.getByOrderId(999L)).thenReturn(List.of());

        mockMvc.perform(get("/order-items/order/999"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    void getById_returnsResponse() throws Exception {
        when(service.getById(1L)).thenReturn(item(1L, 10L, 100L, 2, 500));

        mockMvc.perform(get("/order-items/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orderItemId").value(1))
                .andExpect(jsonPath("$.quantity").value(2));
    }

    @Test
    void getById_returns404WhenNotFound() throws Exception {
        when(service.getById(99L)).thenThrow(new RuntimeException("OrderItem not found"));

        mockMvc.perform(get("/order-items/99"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$").value("OrderItem not found"));
    }

    @Test
    void create_returnsResponse() throws Exception {
        OrderItemRequest request = new OrderItemRequest(10L, 100L, 2, 500);

        when(service.create(any(OrderItemRequest.class))).thenReturn(item(7L, 10L, 100L, 2, 500));

        mockMvc.perform(post("/order-items")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orderItemId").value(7))
                .andExpect(jsonPath("$.orderId").value(10));

        verify(service).create(any(OrderItemRequest.class));
    }

    @Test
    void create_returns400WhenQuantityZero() throws Exception {
        String body = "{\"orderId\": 10, \"productId\": 100, \"quantity\": 0, \"price\": 500}";

        mockMvc.perform(post("/order-items")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Validation Error"));

        verify(service, never()).create(any(OrderItemRequest.class));
    }

    @Test
    void create_returns400WhenPriceNegative() throws Exception {
        String body = "{\"orderId\": 10, \"productId\": 100, \"quantity\": 2, \"price\": -1}";

        mockMvc.perform(post("/order-items")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Validation Error"));

        verify(service, never()).create(any(OrderItemRequest.class));
    }

    @Test
    void create_returns400WhenRequiredFieldsMissing() throws Exception {
        String body = "{\"quantity\": 2, \"price\": 500}";

        mockMvc.perform(post("/order-items")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Validation Error"));

        verify(service, never()).create(any(OrderItemRequest.class));
    }

    @Test
    void update_returnsUpdatedResponse() throws Exception {
        OrderItemRequest request = new OrderItemRequest(10L, 100L, 5, 750);

        when(service.update(eq(1L), any(OrderItemRequest.class)))
                .thenReturn(item(1L, 10L, 100L, 5, 750));

        mockMvc.perform(put("/order-items/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orderItemId").value(1))
                .andExpect(jsonPath("$.quantity").value(5))
                .andExpect(jsonPath("$.price").value(750));
    }

    @Test
    void update_returns400WhenQuantityZero() throws Exception {
        String body = "{\"orderId\": 10, \"productId\": 100, \"quantity\": 0, \"price\": 500}";

        mockMvc.perform(put("/order-items/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Validation Error"));

        verify(service, never()).update(any(), any());
    }

    @Test
    void update_returns404WhenNotFound() throws Exception {
        OrderItemRequest request = new OrderItemRequest(10L, 100L, 2, 500);

        when(service.update(eq(99L), any(OrderItemRequest.class)))
                .thenThrow(new RuntimeException("OrderItem not found"));

        mockMvc.perform(put("/order-items/99")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$").value("OrderItem not found"));
    }

    @Test
    void delete_returnsSuccessMessage() throws Exception {
        mockMvc.perform(delete("/order-items/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").value("OrderItem deleted"));

        verify(service).delete(1L);
    }

    @Test
    void delete_returns404WhenNotFound() throws Exception {
        doThrow(new RuntimeException("OrderItem not found")).when(service).delete(99L);

        mockMvc.perform(delete("/order-items/99"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$").value("OrderItem not found"));
    }

    @Test
    void responseContainsTimestamps() throws Exception {
        OrderItem item = item(1L, 10L, 100L, 2, 500);
        item.setCreatedAt(LocalDateTime.of(2026, 9, 4, 10, 0));
        item.setUpdatedAt(LocalDateTime.of(2026, 9, 4, 11, 30));

        when(service.getById(1L)).thenReturn(item);

        mockMvc.perform(get("/order-items/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.createdAt[0]").value(2026))
                .andExpect(jsonPath("$.updatedAt[0]").value(2026));
    }
}
