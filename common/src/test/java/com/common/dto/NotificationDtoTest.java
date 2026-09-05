package com.common.dto;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;

/**
 * Builder sanity + jakarta validation contract for {@link NotificationDto}.
 * Runs against the default validator factory — no Spring context.
 */
class NotificationDtoTest {

    private final Validator validator;

    NotificationDtoTest() {
        try (ValidatorFactory factory = Validation.buildDefaultValidatorFactory()) {
            this.validator = factory.getValidator();
        }
    }

    private NotificationDto.NotificationDtoBuilder validDto() {
        return NotificationDto.builder()
                .userId(UUID.randomUUID())
                .recipient("john@example.com")
                .title("Order shipped")
                .message("Your order ORD-1 has shipped.")
                .type("EMAIL");
    }

    @Test
    void builder_setsAllFields() {
        UUID userId = UUID.randomUUID();

        NotificationDto dto = validDto().userId(userId).build();

        assertThat(dto.getUserId()).isEqualTo(userId);
        assertThat(dto.getRecipient()).isEqualTo("john@example.com");
        assertThat(dto.getTitle()).isEqualTo("Order shipped");
        assertThat(dto.getMessage()).isEqualTo("Your order ORD-1 has shipped.");
        assertThat(dto.getType()).isEqualTo("EMAIL");
    }

    @Test
    void validDto_hasNoViolations() {
        Set<ConstraintViolation<NotificationDto>> violations =
                validator.validate(validDto().build());

        assertThat(violations).isEmpty();
    }

    @Test
    void blankRecipientTitleMessageTypeProduceViolations() {
        NotificationDto dto = validDto()
                .recipient(" ")
                .title(" ")
                .message(" ")
                .type(" ")
                .build();

        Set<ConstraintViolation<NotificationDto>> violations = validator.validate(dto);

        assertThat(violations).isNotEmpty();
        assertThat(violations).extracting(ConstraintViolation::getPropertyPath)
                .extracting(Object::toString)
                .contains("recipient", "title", "message", "type");
    }

    @Test
    void nullUserIdProducesViolation() {
        NotificationDto dto = validDto().userId(null).build();

        Set<ConstraintViolation<NotificationDto>> violations = validator.validate(dto);

        assertThat(violations).isNotEmpty();
        assertThat(violations).extracting(ConstraintViolation::getPropertyPath)
                .extracting(Object::toString)
                .contains("userId");
    }

    @Test
    void noArgsDto_violatesEveryConstraint() {
        Set<ConstraintViolation<NotificationDto>> violations =
                validator.validate(new NotificationDto());

        assertThat(violations).hasSize(5);
    }
}
