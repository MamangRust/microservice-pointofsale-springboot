package com.cashier.cashier.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.cashier.cashier.dto.CashierMapper;
import com.cashier.cashier.dto.CashierMapperImpl;
import com.cashier.cashier.dto.CashierRequest;
import com.cashier.cashier.entity.Cashier;
import com.cashier.cashier.service.CashierService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

@ExtendWith(MockitoExtension.class)
class CashierControllerTest {

    @Mock
    private CashierService cashierService;

    private MockMvc mockMvc;

    private final CashierMapper cashierMapper = new CashierMapperImpl();

    private final ObjectMapper objectMapper = JsonMapper.builder()
            .addModule(new JavaTimeModule())
            .build();

    @BeforeEach
    void setUp() {
        CashierController controller = new CashierController(cashierService, cashierMapper);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setMessageConverters(new MappingJackson2HttpMessageConverter(objectMapper))
                .build();
    }

    private Cashier createCashier(Long id, Long merchantId, Long userId, String name) {
        Cashier cashier = new Cashier();
        cashier.setCashierId(id);
        cashier.setMerchantId(merchantId);
        cashier.setUserId(userId);
        cashier.setName(name);
        return cashier;
    }

    @Test
    void getAllCashiers_returnsMappedList() throws Exception {
        when(cashierService.getAllCashiers())
                .thenReturn(List.of(createCashier(1L, 1L, 1L, "Cashier1")));

        mockMvc.perform(get("/cashiers"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].cashierId").value(1))
                .andExpect(jsonPath("$[0].name").value("Cashier1"));
    }

    @Test
    void getAllCashiers_returnsEmptyListWhenNone() throws Exception {
        when(cashierService.getAllCashiers()).thenReturn(List.of());

        mockMvc.perform(get("/cashiers"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    void getCashiersByMerchantId_returnsMappedList() throws Exception {
        when(cashierService.getCashiersByMerchantId(1L))
                .thenReturn(List.of(createCashier(1L, 1L, 1L, "MerchantCashier")));

        mockMvc.perform(get("/cashiers/merchant/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("MerchantCashier"))
                .andExpect(jsonPath("$[0].merchantId").value(1));
    }

    @Test
    void getCashierById_returnsResponse() throws Exception {
        when(cashierService.getCashierById(1L)).thenReturn(createCashier(1L, 1L, 1L, "Cashier1"));

        mockMvc.perform(get("/cashiers/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cashierId").value(1))
                .andExpect(jsonPath("$.name").value("Cashier1"));
    }

    @Test
    void getCashierById_returns404WhenNotFound() throws Exception {
        when(cashierService.getCashierById(99L))
                .thenThrow(new RuntimeException("Cashier not found: 99"));

        mockMvc.perform(get("/cashiers/99"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$").value("Cashier not found: 99"));
    }

    @Test
    void createCashier_returnsCreatedResponse() throws Exception {
        CashierRequest request = new CashierRequest(1L, 1L, "NewCashier");

        when(cashierService.createCashier(any(CashierRequest.class)))
                .thenReturn(createCashier(5L, 1L, 1L, "NewCashier"));

        mockMvc.perform(post("/cashiers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cashierId").value(5))
                .andExpect(jsonPath("$.name").value("NewCashier"));
    }

    @Test
    void createCashier_returns500WhenServiceFails() throws Exception {
        CashierRequest request = new CashierRequest(1L, 1L, "NewCashier");

        when(cashierService.createCashier(any(CashierRequest.class)))
                .thenThrow(new RuntimeException("db down"));

        mockMvc.perform(post("/cashiers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$").value("db down"));
    }

    @Test
    void createCashier_returns400WhenNameBlank() throws Exception {
        CashierRequest request = new CashierRequest(1L, 1L, " ");

        mockMvc.perform(post("/cashiers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());

        verify(cashierService, never()).createCashier(any(CashierRequest.class));
    }

    @Test
    void createCashier_returns400WhenMerchantIdNull() throws Exception {
        String body = "{\"merchantId\": null, \"userId\": 1, \"name\": \"X\"}";

        mockMvc.perform(post("/cashiers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());

        verify(cashierService, never()).createCashier(any(CashierRequest.class));
    }

    @Test
    void updateCashier_returnsUpdatedResponse() throws Exception {
        CashierRequest request = new CashierRequest(2L, 3L, "UpdatedName");

        when(cashierService.updateCashier(eq(1L), any(CashierRequest.class)))
                .thenReturn(createCashier(1L, 2L, 3L, "UpdatedName"));

        mockMvc.perform(put("/cashiers/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("UpdatedName"))
                .andExpect(jsonPath("$.merchantId").value(2));
    }

    @Test
    void updateCashier_returns404WhenNotFound() throws Exception {
        CashierRequest request = new CashierRequest(1L, 1L, "UpdatedName");

        when(cashierService.updateCashier(eq(99L), any(CashierRequest.class)))
                .thenThrow(new RuntimeException("Cashier not found: 99"));

        mockMvc.perform(put("/cashiers/99")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound());
    }

    @Test
    void deleteCashier_returnsSuccessMessage() throws Exception {
        mockMvc.perform(delete("/cashiers/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").value("Cashier deleted successfully"));

        verify(cashierService).deleteCashier(1L);
    }

    @Test
    void deleteCashier_returns404WhenNotFound() throws Exception {
        doThrow(new RuntimeException("Cashier not found: 99")).when(cashierService).deleteCashier(99L);

        mockMvc.perform(delete("/cashiers/99"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$").value("Cashier not found: 99"));
    }

    @Test
    void responseContainsTimestamps() throws Exception {
        Cashier cashier = createCashier(1L, 1L, 1L, "Cashier1");
        cashier.setCreatedAt(LocalDateTime.of(2026, 9, 4, 10, 0));

        when(cashierService.getCashierById(1L)).thenReturn(cashier);

        mockMvc.perform(get("/cashiers/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.createdAt[0]").value(2026));
    }
}
