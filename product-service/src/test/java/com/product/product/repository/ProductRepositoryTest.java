package com.product.product.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.product.product.entity.Product;

@DataJpaTest(properties = {
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.flyway.enabled=true"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers(disabledWithoutDocker = true)
class ProductRepositoryTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine");

    @Autowired
    private ProductRepository productRepository;

    private Product createProduct(String name, String price, Integer quantity) {
        Product product = new Product();
        product.setName(name);
        product.setDescription("description of " + name);
        product.setPrice(new BigDecimal(price));
        product.setQuantity(quantity);
        return product;
    }

    @Test
    void save_persistsProductWithGeneratedUuidId() {
        Product saved = productRepository.save(createProduct("Keyboard", "19.99", 5));
        productRepository.flush();

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getId()).isInstanceOf(UUID.class);
        assertThat(saved.getPrice()).isEqualByComparingTo("19.99");
    }

    @Test
    void save_persistsImageIdColumn() {
        Product product = createProduct("Keyboard", "19.99", 5);
        UUID imageId = UUID.randomUUID();
        product.setImageId(imageId);

        Product saved = productRepository.saveAndFlush(product);

        assertThat(saved.getImageId()).isEqualTo(imageId);
    }

    @Test
    void findById_returnsSavedProduct() {
        Product saved = productRepository.save(createProduct("Mouse", "9.99", 3));

        Optional<Product> found = productRepository.findById(saved.getId());

        assertThat(found).isPresent();
        assertThat(found.get().getName()).isEqualTo("Mouse");
        assertThat(found.get().getDescription()).isEqualTo("description of Mouse");
        assertThat(found.get().getQuantity()).isEqualTo(3);
        assertThat(found.get().getImageId()).isNull();
    }

    @Test
    void findById_returnsEmptyWhenMissing() {
        Optional<Product> found = productRepository.findById(UUID.randomUUID());

        assertThat(found).isEmpty();
    }

    @Test
    void findAll_returnsAllPersisted() {
        productRepository.save(createProduct("Keyboard", "19.99", 5));
        productRepository.save(createProduct("Mouse", "9.99", 3));

        List<Product> all = productRepository.findAll();

        assertThat(all).hasSize(2);
        assertThat(all).extracting(Product::getName).containsExactlyInAnyOrder("Keyboard", "Mouse");
    }

    @Test
    void update_changesPriceAndQuantity() {
        Product saved = productRepository.save(createProduct("Keyboard", "19.99", 5));

        saved.setPrice(new BigDecimal("24.50"));
        saved.setQuantity(2);
        Product updated = productRepository.saveAndFlush(saved);

        assertThat(updated.getId()).isEqualTo(saved.getId());
        assertThat(updated.getPrice()).isEqualByComparingTo("24.50");
        assertThat(updated.getQuantity()).isEqualTo(2);

        Product reloaded = productRepository.findById(saved.getId()).orElseThrow();
        assertThat(reloaded.getPrice()).isEqualByComparingTo("24.50");
        assertThat(reloaded.getQuantity()).isEqualTo(2);
    }

    @Test
    void save_withNullName_isRejected() {
        Product product = createProduct("Keyboard", "19.99", 5);
        product.setName(null);

        assertThatThrownBy(() -> productRepository.saveAndFlush(product))
                .isInstanceOf(Exception.class);
    }

    @Test
    void deleteById_removesRow() {
        Product saved = productRepository.save(createProduct("DeleteMe", "1.00", 1));

        productRepository.deleteById(saved.getId());
        productRepository.flush();

        assertThat(productRepository.findById(saved.getId())).isEmpty();
    }
}
