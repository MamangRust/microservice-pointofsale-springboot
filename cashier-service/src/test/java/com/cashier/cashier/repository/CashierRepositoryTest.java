package com.cashier.cashier.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.cashier.cashier.entity.Cashier;

@DataJpaTest(properties = {
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.flyway.enabled=true"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers(disabledWithoutDocker = true)
class CashierRepositoryTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine");

    @Autowired
    private CashierRepository cashierRepository;

    private Cashier createCashier(Long merchantId, Long userId, String name) {
        Cashier cashier = new Cashier();
        cashier.setMerchantId(merchantId);
        cashier.setUserId(userId);
        cashier.setName(name);
        return cashier;
    }

    @Test
    void save_persistsCashierWithGeneratedIdAndTimestamps() {
        Cashier saved = cashierRepository.save(createCashier(1L, 1L, "Cashier1"));

        assertThat(saved.getCashierId()).isNotNull();
        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.getUpdatedAt()).isNotNull();
        assertThat(saved.getDeletedAt()).isNull();
    }

    @Test
    void findById_returnsSavedCashier() {
        Cashier saved = cashierRepository.save(createCashier(1L, 1L, "Cashier2"));

        Optional<Cashier> found = cashierRepository.findById(saved.getCashierId());

        assertThat(found).isPresent();
        assertThat(found.get().getName()).isEqualTo("Cashier2");
        assertThat(found.get().getMerchantId()).isEqualTo(1L);
    }

    @Test
    void findById_returnsEmptyWhenMissing() {
        Optional<Cashier> found = cashierRepository.findById(999999L);

        assertThat(found).isEmpty();
    }

    @Test
    void findAll_returnsAllPersisted() {
        cashierRepository.save(createCashier(1L, 1L, "Cashier1"));
        cashierRepository.save(createCashier(1L, 2L, "Cashier2"));

        List<Cashier> all = cashierRepository.findAll();

        assertThat(all).hasSize(2);
        assertThat(all).extracting(Cashier::getName).containsExactlyInAnyOrder("Cashier1", "Cashier2");
    }

    @Test
    void findByMerchantId_returnsOnlyThatMerchant() {
        cashierRepository.save(createCashier(1L, 1L, "Merchant1Cashier"));
        cashierRepository.save(createCashier(2L, 2L, "Merchant2Cashier"));

        List<Cashier> result = cashierRepository.findByMerchantId(1L);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getName()).isEqualTo("Merchant1Cashier");
    }

    @Test
    void findByMerchantId_returnsEmptyWhenNoMatch() {
        cashierRepository.save(createCashier(1L, 1L, "Merchant1Cashier"));

        List<Cashier> result = cashierRepository.findByMerchantId(42L);

        assertThat(result).isEmpty();
    }

    @Test
    void update_touchesUpdatedAtViaPreUpdate() {
        Cashier saved = cashierRepository.save(createCashier(1L, 1L, "Before"));
        LocalDateTime createdAtBefore = saved.getCreatedAt();

        saved.setName("After");
        Cashier updated = cashierRepository.saveAndFlush(saved);

        assertThat(updated.getName()).isEqualTo("After");
        assertThat(updated.getCreatedAt()).isEqualTo(createdAtBefore);
        assertThat(updated.getUpdatedAt()).isNotNull();
    }

    @Test
    void deleteById_removesRow() {
        Cashier saved = cashierRepository.save(createCashier(1L, 1L, "DeleteMe"));

        cashierRepository.deleteById(saved.getCashierId());
        cashierRepository.flush();

        assertThat(cashierRepository.findById(saved.getCashierId())).isEmpty();
    }
}
