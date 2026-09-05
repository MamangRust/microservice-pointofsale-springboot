package com.product.product.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import com.common.dto.FileMetadataDto;
import com.product.product.client.FileStorageClient;
import com.product.product.dto.ProductResponse;
import com.product.product.entity.Product;
import com.product.product.exc.InsufficientStockException;
import com.product.product.exc.InvalidRequestException;
import com.product.product.exc.ProductNotFoundException;
import com.product.product.mapper.ProductMapper;
import com.product.product.mapper.ProductMapperImpl;
import com.product.product.repository.ProductRepository;

import io.opentelemetry.api.OpenTelemetry;

@ExtendWith(MockitoExtension.class)
class ProductServiceTest {

    @Mock
    private ProductRepository productRepository;

    @Mock
    private FileStorageClient fileStorageClient;

    private ProductService productService;

    private final ProductMapper productMapper = new ProductMapperImpl();

    @BeforeEach
    void setUp() {
        productService = new ProductService(productRepository, productMapper, OpenTelemetry.noop(), fileStorageClient);
    }

    private Product createProduct(UUID id, String name, String price, Integer quantity, UUID imageId) {
        Product product = new Product();
        product.setId(id);
        product.setName(name);
        product.setDescription("description of " + name);
        product.setPrice(new BigDecimal(price));
        product.setQuantity(quantity);
        product.setImageId(imageId);
        return product;
    }

    private void stubSaveReturningArgument() {
        // createProduct/updateProduct log savedProduct.getId().toString(), so the stub
        // must emulate DB-side UUID generation for freshly built entities.
        when(productRepository.save(any(Product.class))).thenAnswer(inv -> {
            Product product = inv.getArgument(0);
            if (product.getId() == null) {
                product.setId(UUID.randomUUID());
            }
            return product;
        });
    }

    private void stubImageMetadata(UUID imageId) {
        // NOTE: the com.common artifact installed in ~/.m2 was built without Lombok
        // processing, so FileMetadataDto has no builder()/setters at test-compile time.
        // The service only checks that the client call does not throw (it never reads
        // the body), so an all-null DTO is sufficient to represent a valid image.
        FileMetadataDto metadata = new FileMetadataDto();
        when(fileStorageClient.getFileMetadata(imageId)).thenReturn(ResponseEntity.ok(metadata));
    }

    @Test
    void createProduct_savesProductWithoutImageValidation() {
        Product product = createProduct(null, "Keyboard", "19.99", 5, null);
        stubSaveReturningArgument();

        productService.createProduct(product);

        ArgumentCaptor<Product> captor = ArgumentCaptor.forClass(Product.class);
        verify(productRepository).save(captor.capture());
        assertThat(captor.getValue().getName()).isEqualTo("Keyboard");
        assertThat(captor.getValue().getPrice()).isEqualByComparingTo("19.99");
        verifyNoInteractions(fileStorageClient);
    }

    @Test
    void createProduct_withExistingImage_savesProduct() {
        UUID imageId = UUID.randomUUID();
        Product product = createProduct(null, "Keyboard", "19.99", 5, imageId);
        stubImageMetadata(imageId);
        stubSaveReturningArgument();

        productService.createProduct(product);

        verify(fileStorageClient).getFileMetadata(imageId);
        verify(productRepository).save(any(Product.class));
    }

    @Test
    void createProduct_withMissingImage_throwsInvalidRequestAndSkipsSave() {
        UUID imageId = UUID.randomUUID();
        Product product = createProduct(null, "Keyboard", "19.99", 5, imageId);
        when(fileStorageClient.getFileMetadata(imageId)).thenThrow(new RuntimeException("404 Not Found"));

        assertThatThrownBy(() -> productService.createProduct(product))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessage("Associated image does not exist: " + imageId);

        verify(productRepository, never()).save(any(Product.class));
    }

    @Test
    void getAllProducts_mapsResponsesAndBuildsImageUrl() {
        UUID imageId = UUID.randomUUID();
        Product withImage = createProduct(UUID.randomUUID(), "Keyboard", "19.99", 5, imageId);
        Product withoutImage = createProduct(UUID.randomUUID(), "Mouse", "9.99", 3, null);

        when(productRepository.findAll()).thenReturn(List.of(withImage, withoutImage));

        List<ProductResponse> result = productService.getAllProducts();

        assertThat(result).hasSize(2);
        assertThat(result.get(0).getName()).isEqualTo("Keyboard");
        assertThat(result.get(0).getPrice()).isEqualTo(19.99);
        assertThat(result.get(0).getImageUrl()).isEqualTo("/files/download/" + imageId);
        assertThat(result.get(1).getName()).isEqualTo("Mouse");
        assertThat(result.get(1).getImageUrl()).isNull();
    }

    @Test
    void getProductById_returnsMappedResponse() {
        UUID imageId = UUID.randomUUID();
        Product product = createProduct(UUID.randomUUID(), "Keyboard", "19.99", 5, imageId);
        when(productRepository.findById(product.getId())).thenReturn(Optional.of(product));

        ProductResponse result = productService.getProductById(product.getId());

        assertThat(result.getId()).isEqualTo(product.getId());
        assertThat(result.getName()).isEqualTo("Keyboard");
        assertThat(result.getPrice()).isEqualTo(19.99);
        assertThat(result.getQuantity()).isEqualTo(5);
        assertThat(result.getImageUrl()).isEqualTo("/files/download/" + imageId);
    }

    @Test
    void getProductById_throwsProductNotFoundWhenMissing() {
        UUID id = UUID.randomUUID();
        when(productRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> productService.getProductById(id))
                .isInstanceOf(ProductNotFoundException.class)
                .hasMessage("Product not found with id: " + id);
    }

    @Test
    void updateProduct_copiesFieldsAndSaves() {
        UUID id = UUID.randomUUID();
        UUID newImageId = UUID.randomUUID();
        Product existing = createProduct(id, "Old", "5.00", 10, null);
        Product changes = createProduct(null, "New", "7.50", 20, newImageId);

        when(productRepository.findById(id)).thenReturn(Optional.of(existing));
        stubImageMetadata(newImageId);
        stubSaveReturningArgument();

        productService.updateProduct(id, changes);

        ArgumentCaptor<Product> captor = ArgumentCaptor.forClass(Product.class);
        verify(productRepository).save(captor.capture());
        Product saved = captor.getValue();
        assertThat(saved.getId()).isEqualTo(id);
        assertThat(saved.getName()).isEqualTo("New");
        assertThat(saved.getDescription()).isEqualTo("description of New");
        assertThat(saved.getPrice()).isEqualByComparingTo("7.50");
        assertThat(saved.getQuantity()).isEqualTo(20);
        assertThat(saved.getImageId()).isEqualTo(newImageId);
    }

    @Test
    void updateProduct_withNullImageId_skipsImageValidationAndSaves() {
        UUID id = UUID.randomUUID();
        Product existing = createProduct(id, "Old", "5.00", 10, UUID.randomUUID());
        Product changes = createProduct(null, "New", "7.50", 20, null);

        when(productRepository.findById(id)).thenReturn(Optional.of(existing));
        stubSaveReturningArgument();

        productService.updateProduct(id, changes);

        ArgumentCaptor<Product> captor = ArgumentCaptor.forClass(Product.class);
        verify(productRepository).save(captor.capture());
        assertThat(captor.getValue().getImageId()).isNull();
        verifyNoInteractions(fileStorageClient);
    }

    @Test
    void updateProduct_withMissingImage_throwsInvalidRequestAndSkipsSave() {
        UUID id = UUID.randomUUID();
        UUID imageId = UUID.randomUUID();
        Product changes = createProduct(null, "New", "7.50", 20, imageId);

        // image validation runs BEFORE the repository lookup, so findById is never reached
        when(fileStorageClient.getFileMetadata(imageId)).thenThrow(new RuntimeException("404 Not Found"));

        assertThatThrownBy(() -> productService.updateProduct(id, changes))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessage("Associated image does not exist: " + imageId);

        verify(productRepository, never()).save(any(Product.class));
    }

    @Test
    void updateProduct_throwsProductNotFoundWhenMissing() {
        UUID id = UUID.randomUUID();
        Product changes = createProduct(null, "New", "7.50", 20, null);
        when(productRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> productService.updateProduct(id, changes))
                .isInstanceOf(ProductNotFoundException.class)
                .hasMessage("Product not found with id: " + id);

        verify(productRepository, never()).save(any(Product.class));
    }

    @Test
    void deleteProduct_deletesWhenExists() {
        UUID id = UUID.randomUUID();
        when(productRepository.existsById(id)).thenReturn(true);

        productService.deleteProduct(id);

        verify(productRepository).deleteById(id);
    }

    @Test
    void deleteProduct_throwsProductNotFoundWhenMissing() {
        UUID id = UUID.randomUUID();
        when(productRepository.existsById(id)).thenReturn(false);

        assertThatThrownBy(() -> productService.deleteProduct(id))
                .isInstanceOf(ProductNotFoundException.class)
                .hasMessage("Product not found with id: " + id);

        verify(productRepository, never()).deleteById(any(UUID.class));
    }

    @Test
    void decreaseStock_decrementsQuantityAndSaves() {
        UUID id = UUID.randomUUID();
        Product product = createProduct(id, "Keyboard", "19.99", 10, null);
        when(productRepository.findById(id)).thenReturn(Optional.of(product));
        stubSaveReturningArgument();

        productService.decreaseStock(id, 3);

        ArgumentCaptor<Product> captor = ArgumentCaptor.forClass(Product.class);
        verify(productRepository).save(captor.capture());
        assertThat(captor.getValue().getId()).isEqualTo(id);
        assertThat(captor.getValue().getQuantity()).isEqualTo(7);
    }

    @Test
    void decreaseStock_allowsDecreasingToZero() {
        UUID id = UUID.randomUUID();
        Product product = createProduct(id, "Keyboard", "19.99", 5, null);
        when(productRepository.findById(id)).thenReturn(Optional.of(product));
        stubSaveReturningArgument();

        productService.decreaseStock(id, 5);

        ArgumentCaptor<Product> captor = ArgumentCaptor.forClass(Product.class);
        verify(productRepository).save(captor.capture());
        assertThat(captor.getValue().getQuantity()).isEqualTo(0);
    }

    @Test
    void decreaseStock_throwsInvalidRequestWhenQuantityZero() {
        UUID id = UUID.randomUUID();

        assertThatThrownBy(() -> productService.decreaseStock(id, 0))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessage("Quantity must be greater than 0");

        verify(productRepository, never()).findById(any(UUID.class));
        verify(productRepository, never()).save(any(Product.class));
    }

    @Test
    void decreaseStock_throwsInvalidRequestWhenQuantityNegative() {
        UUID id = UUID.randomUUID();

        assertThatThrownBy(() -> productService.decreaseStock(id, -3))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessage("Quantity must be greater than 0");

        verify(productRepository, never()).save(any(Product.class));
    }

    @Test
    void decreaseStock_throwsInsufficientStock() {
        UUID id = UUID.randomUUID();
        Product product = createProduct(id, "Keyboard", "19.99", 2, null);
        when(productRepository.findById(id)).thenReturn(Optional.of(product));

        assertThatThrownBy(() -> productService.decreaseStock(id, 5))
                .isInstanceOf(InsufficientStockException.class)
                .hasMessage("Insufficient stock. Available: 2, Requested: 5");

        verify(productRepository, never()).save(any(Product.class));
    }

    @Test
    void decreaseStock_throwsProductNotFoundWhenMissing() {
        UUID id = UUID.randomUUID();
        when(productRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> productService.decreaseStock(id, 1))
                .isInstanceOf(ProductNotFoundException.class)
                .hasMessage("Product not found with id: " + id);

        verify(productRepository, never()).save(any(Product.class));
    }
}
