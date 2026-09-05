package com.common.kafka;

import org.apache.kafka.clients.admin.NewTopic;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.support.converter.StringJsonMessageConverter;

/**
 * Shared Kafka configuration — mirrors Quarkus Kafka setup.
 * Topics follow the pattern: stats.pos.<domain>.event, email-service-topic-*
 */
@Configuration
public class KafkaCommonConfig {

    private static final Logger log = LoggerFactory.getLogger(KafkaCommonConfig.class);

    // === Topic names ===
    public static final String TOPIC_ORDER_EVENT = "stats.pos.order.event";
    public static final String TOPIC_TRANSACTION_EVENT = "stats.pos.transaction.event";
    public static final String TOPIC_EMAIL_RECEIPT = "email-service-topic-receipt";
    public static final String TOPIC_EMAIL_NOTIFICATION = "email-service-topic-notification";
    public static final String TOPIC_EMAIL_MERCHANT = "email-service-topic-merchant";
    public static final String TOPIC_NOTIFICATION = "notification-topic";

    public static final int PARTITIONS = 3;
    public static final short REPLICATION = 1;

    @Bean
    @ConditionalOnMissingBean
    public StringJsonMessageConverter jsonMessageConverter() {
        return new StringJsonMessageConverter();
    }

    @Bean
    public KafkaTemplate<String, Object> kafkaTemplate(ProducerFactory<String, Object> producerFactory) {
        return new KafkaTemplate<>(producerFactory);
    }

    // === Topic definitions ===

    @Bean
    public NewTopic topicOrderEvent() {
        return TopicBuilder.name(TOPIC_ORDER_EVENT)
            .partitions(PARTITIONS)
            .replicas(REPLICATION)
            .build();
    }

    @Bean
    public NewTopic topicTransactionEvent() {
        return TopicBuilder.name(TOPIC_TRANSACTION_EVENT)
            .partitions(PARTITIONS)
            .replicas(REPLICATION)
            .build();
    }

    @Bean
    public NewTopic topicEmailReceipt() {
        return TopicBuilder.name(TOPIC_EMAIL_RECEIPT)
            .partitions(PARTITIONS)
            .replicas(REPLICATION)
            .build();
    }

    @Bean
    public NewTopic topicEmailNotification() {
        return TopicBuilder.name(TOPIC_EMAIL_NOTIFICATION)
            .partitions(PARTITIONS)
            .replicas(REPLICATION)
            .build();
    }

    @Bean
    public NewTopic topicEmailMerchant() {
        return TopicBuilder.name(TOPIC_EMAIL_MERCHANT)
            .partitions(PARTITIONS)
            .replicas(REPLICATION)
            .build();
    }

    @Bean
    public NewTopic topicNotification() {
        return TopicBuilder.name(TOPIC_NOTIFICATION)
            .partitions(PARTITIONS)
            .replicas(REPLICATION)
            .build();
    }
}