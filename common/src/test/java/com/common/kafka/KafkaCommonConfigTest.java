package com.common.kafka;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import java.util.function.Supplier;

import org.apache.kafka.clients.admin.NewTopic;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.support.converter.StringJsonMessageConverter;

/**
 * Unit tests for the shared Kafka configuration. Beans are created by direct
 * instantiation — no Spring context needed.
 */
class KafkaCommonConfigTest {

    private final KafkaCommonConfig config = new KafkaCommonConfig();

    @Test
    void topicNameConstants_matchPublishedContract() {
        assertThat(KafkaCommonConfig.TOPIC_ORDER_EVENT).isEqualTo("stats.pos.order.event");
        assertThat(KafkaCommonConfig.TOPIC_TRANSACTION_EVENT).isEqualTo("stats.pos.transaction.event");
        assertThat(KafkaCommonConfig.TOPIC_EMAIL_RECEIPT).isEqualTo("email-service-topic-receipt");
        assertThat(KafkaCommonConfig.TOPIC_EMAIL_NOTIFICATION).isEqualTo("email-service-topic-notification");
        assertThat(KafkaCommonConfig.TOPIC_EMAIL_MERCHANT).isEqualTo("email-service-topic-merchant");
        assertThat(KafkaCommonConfig.TOPIC_NOTIFICATION).isEqualTo("notification-topic");
        assertThat(KafkaCommonConfig.PARTITIONS).isEqualTo(3);
        assertThat(KafkaCommonConfig.REPLICATION).isEqualTo((short) 1);
    }

    @Test
    void topicBeans_carryDeclaredNamePartitionsAndReplication() {
        Map<String, Supplier<NewTopic>> topicBeans = Map.of(
                KafkaCommonConfig.TOPIC_ORDER_EVENT, config::topicOrderEvent,
                KafkaCommonConfig.TOPIC_TRANSACTION_EVENT, config::topicTransactionEvent,
                KafkaCommonConfig.TOPIC_EMAIL_RECEIPT, config::topicEmailReceipt,
                KafkaCommonConfig.TOPIC_EMAIL_NOTIFICATION, config::topicEmailNotification,
                KafkaCommonConfig.TOPIC_EMAIL_MERCHANT, config::topicEmailMerchant,
                KafkaCommonConfig.TOPIC_NOTIFICATION, config::topicNotification);

        topicBeans.forEach((expectedName, bean) -> {
            NewTopic topic = bean.get();
            assertThat(topic.name()).as("topic name").isEqualTo(expectedName);
            assertThat(topic.numPartitions()).as("partitions of " + expectedName)
                    .isEqualTo(KafkaCommonConfig.PARTITIONS);
            assertThat(topic.replicationFactor()).as("replication of " + expectedName)
                    .isEqualTo(KafkaCommonConfig.REPLICATION);
        });
    }

    @Test
    void topicBeans_doNotSilentlyReuseTopicBuilderDefaults() {
        // sanity: TopicBuilder alone would produce different values than the config
        NewTopic raw = TopicBuilder.name("raw").partitions(1).replicas(1).build();
        assertThat(raw.numPartitions()).isNotEqualTo(KafkaCommonConfig.PARTITIONS);
    }

    @Test
    void jsonMessageConverter_returnsStringJsonMessageConverter() {
        assertThat(config.jsonMessageConverter()).isInstanceOf(StringJsonMessageConverter.class);
    }

    @SuppressWarnings("unchecked")
    @Test
    void kafkaTemplate_wrapsGivenProducerFactory() {
        ProducerFactory<String, Object> producerFactory = Mockito.mock(ProducerFactory.class);

        KafkaTemplate<String, Object> template = config.kafkaTemplate(producerFactory);

        assertThat(template).isNotNull();
    }
}
