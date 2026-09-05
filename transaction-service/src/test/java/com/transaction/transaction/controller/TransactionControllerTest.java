package com.transaction.transaction.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.transaction.transaction.dto.TransactionMapper;
import com.transaction.transaction.dto.TransactionMapperImpl;
import com.transaction.transaction.entity.Transaction;
import com.transaction.transaction.exc.GeneralExceptionHandler;
import com.transaction.transaction.service.TransactionService;

@ExtendWith(MockitoExtension.class)
class TransactionControllerTest {

    @Mock
    private TransactionService transactionService;

    private MockMvc mockMvc;

    private final TransactionMapper transactionMapper = new TransactionMapperImpl();

    private final ObjectMapper objectMapper = JsonMapper.builder()
            .addModule(new JavaTimeModule())
            .build();

    @BeforeEach
    void setUp() {
        TransactionController controller = new TransactionController(transactionService, transactionMapper);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GeneralExceptionHandler())
                .setMessageConverters(new MappingJackson2HttpMessageConverter(objectMapper))
                .build();
    }

    private Transaction createTransaction(Long id, Long orderId, Long merchantId, Integer amount, String status) {
        Transaction txn = new Transaction();
        txn.setTransactionId(id);
        txn.setOrderId(orderId);
        txn.setMerchantId(merchantId);
        txn.setAmount(amount);
        txn.setStatus(status);
        return txn;
    }

    // ---- GET /transactions ----

    @Test
    void getAllTransactions_returnsMappedList() throws Exception {
        when(transactionService.getAll())
                .thenReturn(List.of(createTransaction(1L, 10L, 1L, 111000, "PENDING")));

        mockMvc.perform(get("/transactions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].transactionId").value(1))
                .andExpect(jsonPath("$[0].orderId").value(10))
                .andExpect(jsonPath("$[0].amount").value(111000))
                .andExpect(jsonPath("$[0].status").value("PENDING"));
    }

    @Test
    void getAllTransactions_returnsEmptyListWhenNone() throws Exception {
        when(transactionService.getAll()).thenReturn(List.of());

        mockMvc.perform(get("/transactions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$").isEmpty());
    }

    // ---- GET /transactions/order/{orderId} ----

    @Test
    void getTransactionsByOrderId_returnsMappedList() throws Exception {
        when(transactionService.getByOrderId(10L))
                .thenReturn(List.of(createTransaction(1L, 10L, 1L, 111000, "PENDING")));

        mockMvc.perform(get("/transactions/order/10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].orderId").value(10))
                .andExpect(jsonPath("$[0].transactionId").value(1));
    }

    @Test
    void getTransactionsByOrderId_returnsEmptyListWhenNone() throws Exception {
        when(transactionService.getByOrderId(42L)).thenReturn(List.of());

        mockMvc.perform(get("/transactions/order/42"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());
    }

    // ---- GET /transactions/{id} ----

    @Test
    void getTransactionById_returnsResponse() throws Exception {
        when(transactionService.getById(1L)).thenReturn(createTransaction(1L, 10L, 1L, 111000, "PENDING"));

        mockMvc.perform(get("/transactions/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.transactionId").value(1))
                .andExpect(jsonPath("$.orderId").value(10))
                .andExpect(jsonPath("$.status").value("PENDING"));
    }

    @Test
    void getTransactionById_returns404WhenNotFound() throws Exception {
        when(transactionService.getById(99L)).thenThrow(new RuntimeException("Transaction not found"));

        mockMvc.perform(get("/transactions/99"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$").value("Transaction not found"));
    }

    // ---- POST /transactions ----

    @Test
    void createTransaction_returnsCreatedResponse() throws Exception {
        String body = """
                {"orderId": 10, "merchantId": 1, "paymentMethod": "QRIS", "amount": 111000, "idempotencyKey": "idem-1"}
                """;

        when(transactionService.create(any(Transaction.class)))
                .thenReturn(createTransaction(5L, 10L, 1L, 111000, "PENDING"));

        mockMvc.perform(post("/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.transactionId").value(5))
                .andExpect(jsonPath("$.amount").value(111000));

        ArgumentCaptor<Transaction> captor = ArgumentCaptor.forClass(Transaction.class);
        verify(transactionService).create(captor.capture());
        assertThat(captor.getValue().getOrderId()).isEqualTo(10L);
        assertThat(captor.getValue().getMerchantId()).isEqualTo(1L);
        assertThat(captor.getValue().getAmount()).isEqualTo(111000);
        assertThat(captor.getValue().getPaymentMethod()).isEqualTo("QRIS");
        assertThat(captor.getValue().getIdempotencyKey()).isEqualTo("idem-1");
        // mapper pins status to PENDING on create
        assertThat(captor.getValue().getStatus()).isEqualTo("PENDING");
    }

    @Test
    void createTransaction_returns409WhenDuplicate() throws Exception {
        String body = """
                {"orderId": 10, "merchantId": 1, "amount": 111000, "idempotencyKey": "idem-1"}
                """;

        when(transactionService.create(any(Transaction.class)))
                .thenThrow(new RuntimeException("Duplicate transaction: idempotency key already processed"));

        mockMvc.perform(post("/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$").value("Duplicate transaction: idempotency key already processed"));
    }

    @Test
    void createTransaction_returns500WhenServiceFails() throws Exception {
        String body = """
                {"orderId": 10, "merchantId": 1, "amount": 111000}
                """;

        when(transactionService.create(any(Transaction.class)))
                .thenThrow(new RuntimeException("db down"));

        mockMvc.perform(post("/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$").value("db down"));
    }

    @Test
    void createTransaction_returns400WhenAmountNegative() throws Exception {
        String body = """
                {"orderId": 10, "merchantId": 1, "amount": -1}
                """;

        mockMvc.perform(post("/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());

        verify(transactionService, never()).create(any(Transaction.class));
    }

    @Test
    void createTransaction_returns400WhenOrderIdMissing() throws Exception {
        String body = """
                {"merchantId": 1, "amount": 111000}
                """;

        mockMvc.perform(post("/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());

        verify(transactionService, never()).create(any(Transaction.class));
    }

    // ---- POST /transactions/{id}/complete & /fail ----

    @Test
    void completeTransaction_returnsSuccessMessage() throws Exception {
        mockMvc.perform(post("/transactions/1/complete"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").value("Transaction completed"));

        verify(transactionService).complete(1L);
    }

    @Test
    void completeTransaction_returns404WhenNotFound() throws Exception {
        doThrow(new RuntimeException("Transaction not found")).when(transactionService).complete(99L);

        mockMvc.perform(post("/transactions/99/complete"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$").value("Transaction not found"));
    }

    @Test
    void failTransaction_returnsFailureMessage() throws Exception {
        mockMvc.perform(post("/transactions/1/fail"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").value("Transaction failed"));

        verify(transactionService).fail(1L);
    }

    @Test
    void failTransaction_returns404WhenNotFound() throws Exception {
        doThrow(new RuntimeException("Transaction not found")).when(transactionService).fail(99L);

        mockMvc.perform(post("/transactions/99/fail"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$").value("Transaction not found"));
    }

    // ---- serialization shape ----

    @Test
    void responseContainsTimestamps() throws Exception {
        Transaction txn = createTransaction(1L, 10L, 1L, 111000, "PENDING");
        txn.setCreatedAt(LocalDateTime.of(2026, 9, 4, 10, 0));

        when(transactionService.getById(1L)).thenReturn(txn);

        mockMvc.perform(get("/transactions/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.createdAt[0]").value(2026));
    }
}
