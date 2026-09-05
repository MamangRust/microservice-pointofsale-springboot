package com.merchant.merchant.service;

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

import com.merchant.merchant.dto.MerchantMapper;
import com.merchant.merchant.dto.MerchantMapperImpl;
import com.merchant.merchant.dto.MerchantRequest;
import com.merchant.merchant.entity.Merchant;
import com.merchant.merchant.entity.MerchantDocument;
import com.merchant.merchant.repository.MerchantDocumentRepository;
import com.merchant.merchant.repository.MerchantRepository;

import io.opentelemetry.api.OpenTelemetry;

@ExtendWith(MockitoExtension.class)
class MerchantServiceTest {

    @Mock
    private MerchantRepository merchantRepository;

    @Mock
    private MerchantDocumentRepository documentRepository;

    private MerchantService merchantService;

    private final MerchantMapper merchantMapper = new MerchantMapperImpl();

    @BeforeEach
    void setUp() {
        merchantService = new MerchantService(merchantRepository, documentRepository, merchantMapper,
                OpenTelemetry.noop());
    }

    private Merchant createMerchant(Long id, String name) {
        Merchant merchant = new Merchant();
        merchant.setMerchantId(id);
        merchant.setUserId(1L);
        merchant.setName(name);
        return merchant;
    }

    private MerchantRequest createRequest(String name) {
        return new MerchantRequest(name, "Kopi enak", "Jl. Mawar 1", "shop@example.com", "081234567890");
    }

    @Test
    void getAll_returnsAllFromRepository() {
        when(merchantRepository.findAll())
                .thenReturn(List.of(createMerchant(1L, "Merchant1"), createMerchant(2L, "Merchant2")));

        List<Merchant> result = merchantService.getAll();

        assertThat(result).hasSize(2);
        assertThat(result).extracting(Merchant::getName).containsExactly("Merchant1", "Merchant2");
        verify(merchantRepository).findAll();
    }

    @Test
    void getById_returnsMerchantWhenFound() {
        when(merchantRepository.findById(1L)).thenReturn(Optional.of(createMerchant(1L, "Merchant1")));

        Merchant result = merchantService.getById(1L);

        assertThat(result.getMerchantId()).isEqualTo(1L);
        assertThat(result.getName()).isEqualTo("Merchant1");
    }

    @Test
    void getById_throwsWhenNotFound() {
        when(merchantRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> merchantService.getById(999L))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("Merchant not found");
    }

    @Test
    void create_mapsRequestToEntityAndSaves() {
        Merchant saved = createMerchant(5L, "NewMerchant");
        when(merchantRepository.save(any(Merchant.class))).thenReturn(saved);

        Merchant result = merchantService.create(createRequest("NewMerchant"));

        assertThat(result.getMerchantId()).isEqualTo(5L);

        ArgumentCaptor<Merchant> captor = ArgumentCaptor.forClass(Merchant.class);
        verify(merchantRepository).save(captor.capture());
        Merchant entity = captor.getValue();
        assertThat(entity.getName()).isEqualTo("NewMerchant");
        assertThat(entity.getDescription()).isEqualTo("Kopi enak");
        assertThat(entity.getAddress()).isEqualTo("Jl. Mawar 1");
        assertThat(entity.getContactEmail()).isEqualTo("shop@example.com");
        assertThat(entity.getContactPhone()).isEqualTo("081234567890");
        assertThat(entity.getStatus()).isEqualTo("PENDING");
        assertThat(entity.getMerchantNo()).isNull();
        assertThat(entity.getApiKey()).isNull();
    }

    @Test
    void update_updatesAllMutableFields() {
        Merchant existing = createMerchant(1L, "OldName");
        existing.setDescription("OldDesc");
        existing.setAddress("OldAddress");
        existing.setContactEmail("old@example.com");
        existing.setContactPhone("080000000000");

        MerchantRequest request = createRequest("NewName");

        when(merchantRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(merchantRepository.save(any(Merchant.class))).thenAnswer(inv -> inv.getArgument(0));

        Merchant result = merchantService.update(1L, request);

        assertThat(result.getName()).isEqualTo("NewName");
        assertThat(result.getDescription()).isEqualTo("Kopi enak");
        assertThat(result.getAddress()).isEqualTo("Jl. Mawar 1");
        assertThat(result.getContactEmail()).isEqualTo("shop@example.com");
        assertThat(result.getContactPhone()).isEqualTo("081234567890");
        verify(merchantRepository).save(existing);
    }

    @Test
    void update_throwsWhenNotFound() {
        when(merchantRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> merchantService.update(999L, createRequest("X")))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("Merchant not found");

        verify(merchantRepository, never()).save(any(Merchant.class));
    }

    @Test
    void delete_callsDeleteById() {
        merchantService.delete(1L);

        verify(merchantRepository).deleteById(1L);
    }

    @Test
    void addDocument_persistsDocumentWithDefaults() {
        MerchantDocument saved = new MerchantDocument();
        saved.setDocumentId(9L);
        when(documentRepository.save(any(MerchantDocument.class))).thenReturn(saved);

        MerchantDocument result = merchantService.addDocument(1L, "NIB", "https://docs.example.com/nib.pdf");

        assertThat(result.getDocumentId()).isEqualTo(9L);

        ArgumentCaptor<MerchantDocument> captor = ArgumentCaptor.forClass(MerchantDocument.class);
        verify(documentRepository).save(captor.capture());
        MerchantDocument entity = captor.getValue();
        assertThat(entity.getMerchantId()).isEqualTo(1L);
        assertThat(entity.getDocumentType()).isEqualTo("NIB");
        assertThat(entity.getDocumentUrl()).isEqualTo("https://docs.example.com/nib.pdf");
        assertThat(entity.getStatus()).isEqualTo("PENDING");
    }

    @Test
    void getDocuments_returnsFromRepository() {
        MerchantDocument doc = new MerchantDocument();
        doc.setDocumentId(1L);
        doc.setMerchantId(1L);
        doc.setDocumentType("NIB");
        doc.setDocumentUrl("https://docs.example.com/nib.pdf");
        when(documentRepository.findByMerchantId(1L)).thenReturn(List.of(doc));

        List<MerchantDocument> result = merchantService.getDocuments(1L);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getDocumentType()).isEqualTo("NIB");
        assertThat(result.get(0).getMerchantId()).isEqualTo(1L);
        verify(documentRepository).findByMerchantId(1L);
    }

    @Test
    void getDocuments_returnsEmptyWhenNoDocuments() {
        when(documentRepository.findByMerchantId(42L)).thenReturn(List.of());

        List<MerchantDocument> result = merchantService.getDocuments(42L);

        assertThat(result).isEmpty();
    }
}
