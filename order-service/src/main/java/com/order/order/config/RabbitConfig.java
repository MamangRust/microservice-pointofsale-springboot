package com.order.order.config;

import org.springframework.amqp.core.Queue;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitConfig {

    public static final String QUEUE = "order-queue";

    @Bean
    public Queue orderQueue(){
        return new Queue(QUEUE, true);
    }
}
