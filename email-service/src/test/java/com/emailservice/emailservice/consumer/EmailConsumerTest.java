package com.emailservice.emailservice.consumer;

import static org.assertj.core.api.Assertions.assertThatCode;

import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link EmailConsumer}. The consumer only logs the received
 * Kafka messages, so the honest contract to verify is: it can be instantiated
 * with no dependencies and consumes messages of any content (including empty
 * strings) without throwing.
 */
class EmailConsumerTest {

    private final EmailConsumer emailConsumer = new EmailConsumer();

    @Test
    void handleReceipt_acceptsSampleMessage() {
        assertThatCode(() -> emailConsumer.handleReceipt(
                "{\"orderId\":\"ORD-1\",\"email\":\"john@example.com\",\"total\":25000}"))
                .doesNotThrowAnyException();
    }

    @Test
    void handleNotification_acceptsSampleMessage() {
        assertThatCode(() -> emailConsumer.handleNotification(
                "{\"userId\":\"u-1\",\"recipient\":\"john@example.com\",\"title\":\"Hello\"}"))
                .doesNotThrowAnyException();
    }

    @Test
    void handleMerchant_acceptsSampleMessage() {
        assertThatCode(() -> emailConsumer.handleMerchant(
                "{\"merchantId\":\"m-1\",\"email\":\"merchant@example.com\"}"))
                .doesNotThrowAnyException();
    }

    @Test
    void allListeners_acceptEmptyStringMessage() {
        assertThatCode(() -> {
            emailConsumer.handleReceipt("");
            emailConsumer.handleNotification("");
            emailConsumer.handleMerchant("");
        }).doesNotThrowAnyException();
    }
}
