package com.order.order.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import com.order.order.client.ProductClient;
import com.order.order.client.UserClient;
import com.order.order.dto.OrderRequest;
import com.order.order.dto.Product;
import com.order.order.dto.UserDto;
import com.order.order.entity.Order;
import com.order.order.entity.PaymentStatusEnum;
import com.order.order.exc.AuthException;
import com.order.order.exc.ResourceNotFoundException;
import com.order.order.mapper.OrderMapper;
import com.order.order.repository.OrderRepository;

import io.opentelemetry.api.OpenTelemetry;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    private static final UUID PRODUCT_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID USER_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID OTHER_USER_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");
    private static final UUID ORDER_ID = UUID.fromString("44444444-4444-4444-4444-444444444444");
    private static final UUID ORDER_ID_2 = UUID.fromString("55555555-5555-5555-5555-555555555555");

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private ProductClient productClient;

    @Mock
    private UserClient userClient;

    private final OrderMapper orderMapper = new OrderMapper();

    private OrderService orderService;

    @BeforeEach
    void setUp() {
        orderService = new OrderService(orderRepository, productClient, userClient, orderMapper,
                OpenTelemetry.noop());
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("john", null, List.of()));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private OrderRequest request(UUID productId, Integer quantity) {
        OrderRequest request = new OrderRequest();
        request.setProductId(productId);
        request.setQuantity(quantity);
        return request;
    }

    private Product product(UUID id, String name) {
        Product product = new Product();
        product.setId(id);
        product.setName(name);
        return product;
    }

    private UserDto user(UUID id, String username) {
        UserDto user = new UserDto();
        user.setId(id);
        user.setUsername(username);
        return user;
    }

    private Order order(UUID id, UUID productId, Integer quantity, UUID userId, PaymentStatusEnum status) {
        Order order = new Order();
        order.setId(id);
        order.setProductId(productId);
        order.setQuantity(quantity);
        order.setUserId(userId);
        order.setPaymentStatus(status);
        return order;
    }

    @Test
    void getProductById_returnsProductFromClient() {
        Product expected = product(PRODUCT_ID, "Keyboard");
        when(productClient.getProductById(PRODUCT_ID)).thenReturn(expected);

        Product result = orderService.getProductById(PRODUCT_ID);

        assertThat(result).isSameAs(expected);
        verify(productClient).getProductById(PRODUCT_ID);
    }

    @Test
    void getProductById_propagatesClientError() {
        when(productClient.getProductById(PRODUCT_ID)).thenThrow(new RuntimeException("connection refused"));

        assertThatThrownBy(() -> orderService.getProductById(PRODUCT_ID))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("connection refused");
    }

    @Test
    void createOrder_resolvesUserSetsPendingDecreasesStockAndSaves() {
        when(userClient.getUserByUsername("john")).thenReturn(user(USER_ID, "john"));
        // mimic JPA id assignment: createOrder reads savedOrder.getId() for the span
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> {
            Order toSave = inv.getArgument(0);
            toSave.setId(ORDER_ID);
            return toSave;
        });

        Order result = orderService.createOrder(request(PRODUCT_ID, 2));

        assertThat(result.getProductId()).isEqualTo(PRODUCT_ID);
        assertThat(result.getQuantity()).isEqualTo(2);
        assertThat(result.getUserId()).isEqualTo(USER_ID);
        assertThat(result.getPaymentStatus()).isEqualTo(PaymentStatusEnum.PENDING);

        ArgumentCaptor<Order> captor = ArgumentCaptor.forClass(Order.class);
        verify(orderRepository).save(captor.capture());
        assertThat(captor.getValue().getUserId()).isEqualTo(USER_ID);
        assertThat(captor.getValue().getPaymentStatus()).isEqualTo(PaymentStatusEnum.PENDING);
        verify(productClient).decreaseStock(PRODUCT_ID, 2);
    }

    @Test
    void createOrder_throwsAuthWhenNoAuthentication() {
        SecurityContextHolder.clearContext();

        assertThatThrownBy(() -> orderService.createOrder(request(PRODUCT_ID, 2)))
                .isInstanceOf(AuthException.class)
                .hasMessage("User not authenticated");

        verify(userClient, never()).getUserByUsername(any());
        verify(productClient, never()).decreaseStock(any(), any());
        verify(orderRepository, never()).save(any(Order.class));
    }

    @Test
    void createOrder_throwsAuthForAnonymousUser() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("anonymousUser", null, List.of()));

        assertThatThrownBy(() -> orderService.createOrder(request(PRODUCT_ID, 2)))
                .isInstanceOf(AuthException.class)
                .hasMessageContaining("User not properly authenticated");

        verify(userClient, never()).getUserByUsername(any());
        verify(orderRepository, never()).save(any(Order.class));
    }

    @Test
    void createOrder_throwsAuthForEmptyUsername() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("", null, List.of()));

        assertThatThrownBy(() -> orderService.createOrder(request(PRODUCT_ID, 2)))
                .isInstanceOf(AuthException.class)
                .hasMessage("Username not found in token");

        verify(userClient, never()).getUserByUsername(any());
        verify(orderRepository, never()).save(any(Order.class));
    }

    @Test
    void createOrder_throwsResourceNotFoundWhenUserMissing() {
        when(userClient.getUserByUsername("john")).thenReturn(null);

        assertThatThrownBy(() -> orderService.createOrder(request(PRODUCT_ID, 2)))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("User not found for username: john");

        verify(productClient, never()).decreaseStock(any(), any());
        verify(orderRepository, never()).save(any(Order.class));
    }

    @Test
    void createOrder_propagatesStockFailureAndDoesNotSave() {
        when(userClient.getUserByUsername("john")).thenReturn(user(USER_ID, "john"));
        doThrow(new RuntimeException("stock service down")).when(productClient).decreaseStock(PRODUCT_ID, 2);

        assertThatThrownBy(() -> orderService.createOrder(request(PRODUCT_ID, 2)))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("stock service down");

        verify(orderRepository, never()).save(any(Order.class));
    }

    @Test
    void isOrderExist_returnsRepositoryAnswer() {
        when(orderRepository.existsById(ORDER_ID)).thenReturn(true);
        when(orderRepository.existsById(PRODUCT_ID)).thenReturn(false);

        assertThat(orderService.isOrderExist(ORDER_ID)).isTrue();
        assertThat(orderService.isOrderExist(PRODUCT_ID)).isFalse();
    }

    @Test
    void updateOrderPaymentStatus_updatesStatusAndSaves() {
        Order existing = order(ORDER_ID, PRODUCT_ID, 2, USER_ID, PaymentStatusEnum.PENDING);
        when(orderRepository.findById(ORDER_ID)).thenReturn(java.util.Optional.of(existing));
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

        orderService.updateOrderPaymentStatus(ORDER_ID, PaymentStatusEnum.COMPLETED);

        assertThat(existing.getPaymentStatus()).isEqualTo(PaymentStatusEnum.COMPLETED);
        verify(orderRepository).save(existing);
    }

    @Test
    void updateOrderPaymentStatus_throwsWhenOrderNotFound() {
        when(orderRepository.findById(ORDER_ID)).thenReturn(java.util.Optional.empty());

        assertThatThrownBy(() -> orderService.updateOrderPaymentStatus(ORDER_ID, PaymentStatusEnum.FAILED))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Order not found");

        verify(orderRepository, never()).save(any(Order.class));
    }

    @Test
    void getOrdersByUserId_returnsOrdersForAuthenticatedUser() {
        when(userClient.getUserByUsername("john")).thenReturn(user(USER_ID, "john"));
        when(orderRepository.findByUserId(USER_ID)).thenReturn(List.of(
                order(ORDER_ID, PRODUCT_ID, 2, USER_ID, PaymentStatusEnum.PENDING),
                order(ORDER_ID_2, PRODUCT_ID, 1, USER_ID, PaymentStatusEnum.COMPLETED)));

        List<Order> result = orderService.getOrdersByUserId();

        assertThat(result).hasSize(2);
        assertThat(result).allSatisfy(o -> assertThat(o.getUserId()).isEqualTo(USER_ID));
        verify(orderRepository).findByUserId(USER_ID);
    }

    @Test
    void getOrdersByUserId_throwsWhenNoOrders() {
        when(userClient.getUserByUsername("john")).thenReturn(user(USER_ID, "john"));
        when(orderRepository.findByUserId(USER_ID)).thenReturn(List.of());

        assertThatThrownBy(() -> orderService.getOrdersByUserId())
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("No orders found for user ID: " + USER_ID);
    }

    @Test
    void getOrdersByUserId_throwsAuthWhenNoAuthentication() {
        SecurityContextHolder.clearContext();

        assertThatThrownBy(() -> orderService.getOrdersByUserId())
                .isInstanceOf(AuthException.class)
                .hasMessage("User not authenticated");

        verify(userClient, never()).getUserByUsername(any());
        verify(orderRepository, never()).findByUserId(any());
    }

    @Test
    void getOrdersByUserId_throwsNpeWhenUserMissing() {
        // quirk: getOrdersByUserId does not null-check the user like createOrder does
        when(userClient.getUserByUsername("john")).thenReturn(null);

        assertThatThrownBy(() -> orderService.getOrdersByUserId())
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void getAllOrders_returnsAllFromRepository() {
        when(orderRepository.findAll()).thenReturn(List.of(
                order(ORDER_ID, PRODUCT_ID, 2, USER_ID, PaymentStatusEnum.PENDING),
                order(ORDER_ID_2, PRODUCT_ID, 1, OTHER_USER_ID, PaymentStatusEnum.REFUNDED)));

        List<Order> result = orderService.getAllOrders();

        assertThat(result).hasSize(2);
        assertThat(result).extracting(Order::getId)
                .containsExactly(ORDER_ID, ORDER_ID_2);
        verify(orderRepository).findAll();
    }
}
