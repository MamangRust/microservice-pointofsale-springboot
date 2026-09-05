package com.product.product.controller;

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

import static org.hamcrest.Matchers.containsString;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.product.product.dto.ProductResponse;
import com.product.product.exc.GeneralExceptionHandler;
import com.product.product.exc.InsufficientStockException;
import com.product.product.exc.InvalidRequestException;
import com.product.product.exc.ProductNotFoundException;
import com.product.product.service.ProductService;

@ExtendWith(MockitoExtension.class)
class ProductControllerTest {

    @Mock
    private ProductService productService;

    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = JsonMapper.builder()
            .addModule(new JavaTimeModule())
            .build();

    private UUID productId;

    @BeforeEach
    void setUp() {
        productId = UUID.randomUUID();
        ProductController controller = new ProductController(productService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GeneralExceptionHandler())
                .setMessageConverters(new MappingJackson2HttpMessageConverter(objectMapper))
                .build();
    }

    private ProductResponse createResponse(UUID id, String name, Double price, Integer quantity, UUID imageId) {
        ProductResponse response = new ProductResponse();
        response.setId(id);
        response.setName(name);
        response.setDescription("description of " + name);
        response.setPrice(price);
        response.setQuantity(quantity);
        response.setImageId(imageId);
        response.setImageUrl(imageId != null ? "/files/download/" + imageId : null);
        return response;
    }

    private String productJson(String name, String price, Integer quantity) {
        return "{\"name\": \"" + name + "\", \"description\": \"desc\", "
                + "\"price\": " + price + ", \"quantity\": " + quantity + "}";
    }

    @Test
    void createProduct_returnsProductEcho() throws Exception {
        mockMvc.perform(post("/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(productJson("Keyboard", "19.99", 5)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Keyboard"))
                .andExpect(jsonPath("$.price").value(19.99))
                .andExpect(jsonPath("$.quantity").value(5));

        verify(productService).createProduct(any(com.product.product.entity.Product.class));
    }

    @Test
    void createProduct_returns400WhenNameBlank() throws Exception {
        mockMvc.perform(post("/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(productJson(" ", "19.99", 5)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Validation Error"))
                .andExpect(jsonPath("$.message", containsString("name")));

        verify(productService, never()).createProduct(any(com.product.product.entity.Product.class));
    }

    @Test
    void createProduct_returns400WhenPriceBelowMinimum() throws Exception {
        mockMvc.perform(post("/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(productJson("Keyboard", "0.00", 5)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Validation Error"))
                .andExpect(jsonPath("$.message", containsString("price")));
    }

    @Test
    void createProduct_returns400WhenQuantityNegative() throws Exception {
        mockMvc.perform(post("/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(productJson("Keyboard", "19.99", -1)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Validation Error"))
                .andExpect(jsonPath("$.message", containsString("quantity")));
    }

    @Test
    void createProduct_returns400WhenJsonMalformed() throws Exception {
        mockMvc.perform(post("/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{not-json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Invalid JSON"))
                .andExpect(jsonPath("$.message").value("Request body contains invalid JSON format"));
    }

    @Test
    void createProduct_returns400WhenServiceRejectsRequest() throws Exception {
        doThrow(new InvalidRequestException("Associated image does not exist: " + productId))
                .when(productService).createProduct(any(com.product.product.entity.Product.class));

        mockMvc.perform(post("/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(productJson("Keyboard", "19.99", 5)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Invalid Request"))
                .andExpect(jsonPath("$.message", containsString("Associated image does not exist")));
    }

    @Test
    void getAllProducts_returnsMappedList() throws Exception {
        UUID imageId = UUID.randomUUID();
        when(productService.getAllProducts()).thenReturn(List.of(
                createResponse(UUID.randomUUID(), "Keyboard", 19.99, 5, imageId),
                createResponse(UUID.randomUUID(), "Mouse", 9.99, 3, null)));

        mockMvc.perform(get("/products"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("Keyboard"))
                .andExpect(jsonPath("$[0].imageUrl").value("/files/download/" + imageId))
                .andExpect(jsonPath("$[1].name").value("Mouse"))
                .andExpect(jsonPath("$[1].imageUrl").doesNotExist());
    }

    @Test
    void getAllProducts_returnsEmptyListWhenNone() throws Exception {
        when(productService.getAllProducts()).thenReturn(List.of());

        mockMvc.perform(get("/products"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    void getProductById_returnsResponse() throws Exception {
        UUID imageId = UUID.randomUUID();
        when(productService.getProductById(productId))
                .thenReturn(createResponse(productId, "Keyboard", 19.99, 5, imageId));

        mockMvc.perform(get("/products/" + productId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(productId.toString()))
                .andExpect(jsonPath("$.name").value("Keyboard"))
                .andExpect(jsonPath("$.imageUrl").value("/files/download/" + imageId));
    }

    @Test
    void getProductById_returns404WhenNotFound() throws Exception {
        when(productService.getProductById(productId))
                .thenThrow(new ProductNotFoundException(productId));

        mockMvc.perform(get("/products/" + productId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.error").value("Product Not Found"))
                .andExpect(jsonPath("$.message").value("Product not found with id: " + productId));
    }

    @Test
    void getProductById_returns400WhenIdNotUuid() throws Exception {
        mockMvc.perform(get("/products/not-a-uuid"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Type Mismatch"))
                .andExpect(jsonPath("$.message", containsString("Parameter 'id' should be of type UUID")));
    }

    @Test
    void updateProduct_returnsProductEcho() throws Exception {
        mockMvc.perform(put("/products/" + productId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(productJson("Updated", "7.50", 20)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Updated"))
                .andExpect(jsonPath("$.price").value(7.5))
                .andExpect(jsonPath("$.quantity").value(20));

        verify(productService).updateProduct(eq(productId), any(com.product.product.entity.Product.class));
    }

    @Test
    void updateProduct_returns404WhenNotFound() throws Exception {
        doThrow(new ProductNotFoundException(productId))
                .when(productService).updateProduct(eq(productId), any(com.product.product.entity.Product.class));

        mockMvc.perform(put("/products/" + productId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(productJson("Updated", "7.50", 20)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Product Not Found"));
    }

    @Test
    void deleteProduct_returns204() throws Exception {
        mockMvc.perform(delete("/products/" + productId))
                .andExpect(status().isNoContent());

        verify(productService).deleteProduct(productId);
    }

    @Test
    void deleteProduct_returns404WhenNotFound() throws Exception {
        doThrow(new ProductNotFoundException(productId)).when(productService).deleteProduct(productId);

        mockMvc.perform(delete("/products/" + productId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Product Not Found"))
                .andExpect(jsonPath("$.message").value("Product not found with id: " + productId));
    }

    @Test
    void decreaseStock_returnsMessageWithProductIdAndQuantity() throws Exception {
        mockMvc.perform(post("/products/" + productId + "/decrease").param("quantity", "3"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Product stock decreased successfully"))
                .andExpect(jsonPath("$.productId").value(productId.toString()))
                .andExpect(jsonPath("$.decreasedQuantity").value(3));

        verify(productService).decreaseStock(productId, 3);
    }

    @Test
    void decreaseStock_returns400WhenInsufficientStock() throws Exception {
        doThrow(new InsufficientStockException(2, 5)).when(productService).decreaseStock(productId, 5);

        mockMvc.perform(post("/products/" + productId + "/decrease").param("quantity", "5"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Insufficient Stock"))
                .andExpect(jsonPath("$.message").value("Insufficient stock. Available: 2, Requested: 5"));
    }

    // Spring 6.2 built-in method validation raises HandlerMethodValidationException for
    // @Min(1) on the request param; GeneralExceptionHandler has no dedicated handler, so
    // the generic @ExceptionHandler(Exception.class) intercepts it (actual product behavior).
    @Test
    void decreaseStock_quantityZeroIsRejectedByMethodValidation() throws Exception {
        mockMvc.perform(post("/products/" + productId + "/decrease").param("quantity", "0"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.error").value("Internal Server Error"));

        verify(productService, never()).decreaseStock(any(UUID.class), org.mockito.ArgumentMatchers.anyInt());
    }
}
