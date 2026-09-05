package com.order.order.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.order.order.config.RabbitConfig;
import com.order.order.dto.Product;
import com.order.order.dto.OrderRequest;
import com.order.order.entity.Order;
import com.order.order.entity.PaymentStatusEnum;
import com.order.order.exc.AuthException;
import com.order.order.exc.InvalidRequestException;
import com.order.order.exc.ResourceNotFoundException;
import com.order.order.mapper.OrderMapper;
import com.order.order.service.OrderService;

@ExtendWith(MockitoExtension.class)
class OrderControllerTest {

    private static final UUID PRODUCT_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID ORDER_ID = UUID.fromString("44444444-4444-4444-4444-444444444444");

    @Mock
    private OrderService orderService;

    @Mock
    private RabbitTemplate rabbitTemplate;

    private final OrderMapper orderMapper = new OrderMapper();

    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = JsonMapper.builder()
            .addModule(new JavaTimeModule())
            .build();

    @BeforeEach
    void setUp() {
        OrderController controller = new OrderController(orderService, orderMapper, rabbitTemplate);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new com.order.order.exc.GeneralExceptionHandler())
                .setMessageConverters(new MappingJackson2HttpMessageConverter(objectMapper))
                .build();
    }

    private Order order(UUID id, UUID productId, Integer quantity, PaymentStatusEnum status) {
        Order order = new Order();
        order.setId(id);
        order.setProductId(productId);
        order.setQuantity(quantity);
        order.setPaymentStatus(status);
        return order;
    }

    private OrderRequest request(UUID productId, Integer quantity) {
        OrderRequest request = new OrderRequest();
        request.setProductId(productId);
        request.setQuantity(quantity);
        return request;
    }

    @Test
    void createOrder_createsOrderAndPublishesToRabbit() throws Exception {
        when(orderService.getProductById(PRODUCT_ID))
                .thenReturn(product(PRODUCT_ID, "Keyboard"));
        when(orderService.createOrder(any(OrderRequest.class)))
                .thenReturn(order(ORDER_ID, PRODUCT_ID, 2, PaymentStatusEnum.PENDING));

        mockMvc.perform(post("/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request(PRODUCT_ID, 2))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(ORDER_ID.toString()))
                .andExpect(jsonPath("$.productId").value(PRODUCT_ID.toString()))
                .andExpect(jsonPath("$.quantity").value(2))
                .andExpect(jsonPath("$.paymentStatus").value("PENDING"));

        ArgumentCaptor<Order> captor = ArgumentCaptor.forClass(Order.class);
        verify(rabbitTemplate).convertAndSend(eq(RabbitConfig.QUEUE), captor.capture());
        assertThat(captor.getValue().getId()).isEqualTo(ORDER_ID);
        verify(orderService).createOrder(any(OrderRequest.class));
    }

    @Test
    void createOrder_returns400WhenProductIdMissing() throws Exception {
        String body = "{\"quantity\": 2}";

        mockMvc.perform(post("/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value("BAD_REQUEST"))
                .andExpect(jsonPath("$.message").value("Failed to verify product: Product ID is required"));

        verify(orderService, never()).createOrder(any(OrderRequest.class));
        verify(rabbitTemplate, never()).convertAndSend(any(String.class), any(Object.class));
    }

    @Test
    void createOrder_returns400WhenQuantityMissing() throws Exception {
        String body = "{\"productId\": \"" + PRODUCT_ID + "\"}";

        mockMvc.perform(post("/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Failed to verify product: Quantity must be a positive number"));

        verify(orderService, never()).createOrder(any(OrderRequest.class));
    }

    @Test
    void createOrder_returns400WhenQuantityZero() throws Exception {
        String body = "{\"productId\": \"" + PRODUCT_ID + "\", \"quantity\": 0}";

        mockMvc.perform(post("/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Failed to verify product: Quantity must be a positive number"));

        verify(orderService, never()).createOrder(any(OrderRequest.class));
    }

    @Test
    void createOrder_returns400WhenProductNotFound() throws Exception {
        when(orderService.getProductById(PRODUCT_ID)).thenReturn(null);

        mockMvc.perform(post("/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request(PRODUCT_ID, 2))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Failed to verify product: Product not found"));

        verify(orderService, never()).createOrder(any(OrderRequest.class));
        verify(rabbitTemplate, never()).convertAndSend(any(String.class), any(Object.class));
    }

    @Test
    void createOrder_returns400WrappedWhenProductClientFails() throws Exception {
        when(orderService.getProductById(PRODUCT_ID)).thenThrow(new RuntimeException("connection refused"));

        mockMvc.perform(post("/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request(PRODUCT_ID, 2))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message")
                        .value("Failed to verify product: connection refused"));

        verify(orderService, never()).createOrder(any(OrderRequest.class));
    }

    @Test
    void createOrder_mapsResourceNotFoundTo404() throws Exception {
        when(orderService.getProductById(PRODUCT_ID)).thenReturn(product(PRODUCT_ID, "Keyboard"));
        when(orderService.createOrder(any(OrderRequest.class)))
                .thenThrow(new ResourceNotFoundException("User not found for username: john"));

        mockMvc.perform(post("/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request(PRODUCT_ID, 2))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("User not found for username: john"));

        verify(rabbitTemplate, never()).convertAndSend(any(String.class), any(Object.class));
    }

    @Test
    void createOrder_mapsAuthExceptionTo401() throws Exception {
        when(orderService.getProductById(PRODUCT_ID)).thenReturn(product(PRODUCT_ID, "Keyboard"));
        when(orderService.createOrder(any(OrderRequest.class)))
                .thenThrow(new AuthException("User not authenticated"));

        mockMvc.perform(post("/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request(PRODUCT_ID, 2))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value("UNAUTHORIZED"));
    }

    @Test
    void createOrder_mapsUnexpectedCreateFailureTo500() throws Exception {
        when(orderService.getProductById(PRODUCT_ID)).thenReturn(product(PRODUCT_ID, "Keyboard"));
        when(orderService.createOrder(any(OrderRequest.class)))
                .thenThrow(new RuntimeException("db down"));

        mockMvc.perform(post("/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request(PRODUCT_ID, 2))))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.message").value("An unexpected error occurred"))
                .andExpect(jsonPath("$.path").value("uri=/orders"));
    }

    @Test
    void getOrderById_returnsTrueWhenExists() throws Exception {
        when(orderService.isOrderExist(ORDER_ID)).thenReturn(true);

        mockMvc.perform(get("/orders/" + ORDER_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").value(true));
    }

    @Test
    void getOrderById_returnsFalseWhenMissing() throws Exception {
        when(orderService.isOrderExist(ORDER_ID)).thenReturn(false);

        mockMvc.perform(get("/orders/" + ORDER_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").value(false));
    }

    @Test
    void updateOrderPaymentStatus_delegatesToService() throws Exception {
        mockMvc.perform(put("/orders/" + ORDER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(PaymentStatusEnum.COMPLETED)))
                .andExpect(status().isOk());

        verify(orderService).updateOrderPaymentStatus(ORDER_ID, PaymentStatusEnum.COMPLETED);
    }

    @Test
    void updateOrderPaymentStatus_acceptsRefundedStatus() throws Exception {
        mockMvc.perform(put("/orders/" + ORDER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("\"REFUNDED\""))
                .andExpect(status().isOk());

        verify(orderService).updateOrderPaymentStatus(ORDER_ID, PaymentStatusEnum.REFUNDED);
    }

    @Test
    void updateOrderPaymentStatus_mapsUnreadableStatusBodyTo500() throws Exception {
        // quirk: no HttpMessageNotReadableException handler in the advice, so the generic
        // Exception.class handler (500) wins over a 400
        mockMvc.perform(put("/orders/" + ORDER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("\"NOT_A_STATUS\""))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.message").value("An unexpected error occurred"));

        verify(orderService, never()).updateOrderPaymentStatus(any(), any());
    }

    @Test
    void updateOrderPaymentStatus_mapsNotFoundTo404() throws Exception {
        doThrow(new ResourceNotFoundException("Order not found")).when(orderService)
                .updateOrderPaymentStatus(eq(ORDER_ID), any());

        mockMvc.perform(put("/orders/" + ORDER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("\"COMPLETED\""))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Order not found"));
    }

    @Test
    void getMyOrders_returnsOrdersForUser() throws Exception {
        when(orderService.getOrdersByUserId()).thenReturn(List.of(
                order(ORDER_ID, PRODUCT_ID, 2, PaymentStatusEnum.PENDING)));

        mockMvc.perform(get("/orders/my-orders"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(ORDER_ID.toString()))
                .andExpect(jsonPath("$[0].productId").value(PRODUCT_ID.toString()));
    }

    @Test
    void getMyOrders_mapsNotFoundTo404() throws Exception {
        when(orderService.getOrdersByUserId())
                .thenThrow(new ResourceNotFoundException("No orders found for user ID: " + ORDER_ID));

        mockMvc.perform(get("/orders/my-orders"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("No orders found for user ID: " + ORDER_ID));
    }

    @Test
    void getAllOrders_returnsList() throws Exception {
        when(orderService.getAllOrders()).thenReturn(List.of(
                order(ORDER_ID, PRODUCT_ID, 2, PaymentStatusEnum.PENDING),
                order(UUID.fromString("55555555-5555-5555-5555-555555555555"), PRODUCT_ID, 1,
                        PaymentStatusEnum.COMPLETED)));

        mockMvc.perform(get("/orders"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].paymentStatus").value("PENDING"))
                .andExpect(jsonPath("$[1].paymentStatus").value("COMPLETED"));
    }

    @Test
    void getAllOrders_returnsEmptyListWhenNone() throws Exception {
        when(orderService.getAllOrders()).thenReturn(List.of());

        mockMvc.perform(get("/orders"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$").isEmpty());
    }

    private Product product(UUID id, String name) {
        Product product = new Product();
        product.setId(id);
        product.setName(name);
        return product;
    }
}
