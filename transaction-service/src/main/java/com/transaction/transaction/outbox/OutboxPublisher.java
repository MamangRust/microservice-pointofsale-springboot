package com.transaction.transaction.outbox;

import com.transaction.transaction.entity.Outbox;
import com.transaction.transaction.entity.OutboxStatus;
import com.transaction.transaction.repository.OutboxRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Component
@Slf4j
public class OutboxPublisher {

    private static final int MAX_ATTEMPTS = 5;

    private final OutboxRepository outboxRepository;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    public OutboxPublisher(OutboxRepository outboxRepository,
                           KafkaTemplate<String, Object> kafkaTemplate) {
        this.outboxRepository = outboxRepository;
        this.kafkaTemplate = kafkaTemplate;
    }

    @Scheduled(fixedDelay = 3000)
    @Transactional
    public void publishPending() {
        List<Outbox> pending = outboxRepository.findByStatusOrderByCreatedAt(OutboxStatus.PENDING);
        for (Outbox outbox : pending) {
            try {
                kafkaTemplate.send(outbox.getTopic(), outbox.getAggregateId(), outbox.getPayload())
                    .get();
                outbox.setStatus(OutboxStatus.PROCESSED);
                outbox.setProcessedAt(LocalDateTime.now());
                outboxRepository.save(outbox);
                log.debug("Published outbox event {} to topic {}", outbox.getEventId(), outbox.getTopic());
            } catch (Exception e) {
                outbox.setAttempts(outbox.getAttempts() + 1);
                outbox.setLastError(e.getMessage());
                if (outbox.getAttempts() >= MAX_ATTEMPTS) {
                    outbox.setStatus(OutboxStatus.FAILED);
                }
                outboxRepository.save(outbox);
                log.error("Failed to publish outbox event {}: {}", outbox.getEventId(), e.getMessage());
            }
        }
    }
}