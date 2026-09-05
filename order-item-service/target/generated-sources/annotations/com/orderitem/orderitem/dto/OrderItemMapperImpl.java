package com.orderitem.orderitem.dto;

import com.orderitem.orderitem.entity.OrderItem;
import java.time.LocalDateTime;
import javax.annotation.processing.Generated;
import org.springframework.stereotype.Component;

@Generated(
    value = "org.mapstruct.ap.MappingProcessor",
    date = "2026-09-05T18:19:01+0700",
    comments = "version: 1.6.1, compiler: Eclipse JDT (IDE) 3.46.100.v20260624-0231, environment: Java 21.0.11 (Eclipse Adoptium)"
)
@Component
public class OrderItemMapperImpl implements OrderItemMapper {

    @Override
    public OrderItem toEntity(OrderItemRequest request) {
        if ( request == null ) {
            return null;
        }

        OrderItem orderItem = new OrderItem();

        orderItem.setOrderId( request.orderId() );
        orderItem.setPrice( request.price() );
        orderItem.setProductId( request.productId() );
        orderItem.setQuantity( request.quantity() );

        return orderItem;
    }

    @Override
    public OrderItemResponse toResponse(OrderItem orderItem) {
        if ( orderItem == null ) {
            return null;
        }

        Long orderItemId = null;
        Long orderId = null;
        Long productId = null;
        Integer quantity = null;
        Integer price = null;
        LocalDateTime createdAt = null;
        LocalDateTime updatedAt = null;

        orderItemId = orderItem.getOrderItemId();
        orderId = orderItem.getOrderId();
        productId = orderItem.getProductId();
        quantity = orderItem.getQuantity();
        price = orderItem.getPrice();
        createdAt = orderItem.getCreatedAt();
        updatedAt = orderItem.getUpdatedAt();

        OrderItemResponse orderItemResponse = new OrderItemResponse( orderItemId, orderId, productId, quantity, price, createdAt, updatedAt );

        return orderItemResponse;
    }
}
