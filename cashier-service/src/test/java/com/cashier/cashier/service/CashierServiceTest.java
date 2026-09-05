package com.cashier.cashier.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.cashier.cashier.dto.CashierMapper;
import com.cashier.cashier.dto.CashierMapperImpl;
import com.cashier.cashier.dto.CashierRequest;
import com.cashier.cashier.entity.Cashier;
import com.cashier.cashier.repository.CashierRepository;

import io.opentelemetry.api.OpenTelemetry;

@ExtendWith(MockitoExtension.class)
class CashierServiceTest {

    @Mock
    private CashierRepository cashierRepository;

    private CashierService cashierService;

    private final CashierMapper cashierMapper = new CashierMapperImpl();

    @BeforeEach
    void setUp() {
        cashierService = new CashierService(cashierRepository, cashierMapper, OpenTelemetry.noop());
    }

    private Cashier createCashier(Long id, Long merchantId, Long userId, String name) {
        Cashier cashier = new Cashier();
        cashier.setCashierId(id);
        cashier.setMerchantId(merchantId);
        cashier.setUserId(userId);
        cashier.setName(name);
        return cashier;
    }

    private CashierRequest createRequest(Long merchantId, Long userId, String name) {
        return new CashierRequest(merchantId, userId, name);
    }

    @Test
    void getAllCashiers_returnsAllFromRepository() {
        Cashier c1 = createCashier(1L, 1L, 1L, "Cashier1");
        Cashier c2 = createCashier(2L, 1L, 2L, "Cashier2");

        when(cashierRepository.findAll()).thenReturn(List.of(c1, c2));

        List<Cashier> result = cashierService.getAllCashiers();

        assertThat(result).hasSize(2);
        assertThat(result).extracting(Cashier::getName).containsExactly("Cashier1", "Cashier2");
        verify(cashierRepository).findAll();
    }

    @Test
    void getCashierById_returnsCashierWhenFound() {
        when(cashierRepository.findById(1L)).thenReturn(Optional.of(createCashier(1L, 1L, 1L, "Cashier1")));

        Cashier result = cashierService.getCashierById(1L);

        assertThat(result.getCashierId()).isEqualTo(1L);
        assertThat(result.getName()).isEqualTo("Cashier1");
    }

    @Test
    void getCashierById_throwsWhenNotFound() {
        when(cashierRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> cashierService.getCashierById(999L))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Cashier not found: 999");
    }

    @Test
    void createCashier_mapsRequestToEntityAndSaves() {
        CashierRequest request = createRequest(1L, 1L, "NewCashier");
        Cashier saved = createCashier(5L, 1L, 1L, "NewCashier");

        when(cashierRepository.save(any(Cashier.class))).thenReturn(saved);

        Cashier result = cashierService.createCashier(request);

        assertThat(result.getCashierId()).isEqualTo(5L);

        ArgumentCaptor<Cashier> captor = ArgumentCaptor.forClass(Cashier.class);
        verify(cashierRepository).save(captor.capture());
        assertThat(captor.getValue().getName()).isEqualTo("NewCashier");
        assertThat(captor.getValue().getMerchantId()).isEqualTo(1L);
        assertThat(captor.getValue().getUserId()).isEqualTo(1L);
    }

    @Test
    void updateCashier_updatesFieldsOnExisting() {
        Cashier existing = createCashier(1L, 1L, 1L, "OldName");
        CashierRequest request = createRequest(2L, 3L, "UpdatedName");

        when(cashierRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(cashierRepository.save(any(Cashier.class))).thenAnswer(inv -> inv.getArgument(0));

        Cashier result = cashierService.updateCashier(1L, request);

        assertThat(result.getName()).isEqualTo("UpdatedName");
        assertThat(result.getMerchantId()).isEqualTo(2L);
        assertThat(result.getUserId()).isEqualTo(3L);
        verify(cashierRepository).save(existing);
    }

    @Test
    void updateCashier_throwsWhenNotFound() {
        when(cashierRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> cashierService.updateCashier(999L, createRequest(1L, 1L, "X")))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Cashier not found: 999");

        verify(cashierRepository, never()).save(any(Cashier.class));
    }

    @Test
    void deleteCashier_setsDeletedAtAndSaves() {
        Cashier existing = createCashier(1L, 1L, 1L, "TrashMe");

        when(cashierRepository.findById(1L)).thenReturn(Optional.of(existing));

        cashierService.deleteCashier(1L);

        assertThat(existing.getDeletedAt()).isNotNull();
        verify(cashierRepository).save(existing);
    }

    @Test
    void deleteCashier_throwsWhenNotFound() {
        when(cashierRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> cashierService.deleteCashier(999L))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Cashier not found: 999");

        verify(cashierRepository, never()).save(any(Cashier.class));
    }

    @Test
    void getCashiersByMerchantId_returnsFromRepository() {
        when(cashierRepository.findByMerchantId(1L))
                .thenReturn(List.of(createCashier(1L, 1L, 1L, "MerchantCashier1")));

        List<Cashier> result = cashierService.getCashiersByMerchantId(1L);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getMerchantId()).isEqualTo(1L);
        verify(cashierRepository).findByMerchantId(1L);
    }

    @Test
    void getCashiersByMerchantId_returnsEmptyWhenNoMatch() {
        when(cashierRepository.findByMerchantId(42L)).thenReturn(List.of());

        List<Cashier> result = cashierService.getCashiersByMerchantId(42L);

        assertThat(result).isEmpty();
    }
}
