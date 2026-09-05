package com.category.category.repository;

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

import com.category.category.entity.Category;

@DataJpaTest(properties = {
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.flyway.enabled=true"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers(disabledWithoutDocker = true)
class CategoryRepositoryTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine");

    @Autowired
    private CategoryRepository categoryRepository;

    private Category createCategory(String name, String description) {
        Category category = new Category();
        category.setName(name);
        category.setDescription(description);
        return category;
    }

    @Test
    void save_persistsCategoryWithGeneratedIdAndTimestamps() {
        Category saved = categoryRepository.save(createCategory("Coffee", "Arabica blend"));

        assertThat(saved.getCategoryId()).isNotNull();
        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.getUpdatedAt()).isNotNull();
        assertThat(saved.getDeletedAt()).isNull();
    }

    @Test
    void save_derivesSlugCategoryFromNameViaPrePersist() {
        Category saved = categoryRepository.save(createCategory("Home Appliances", "Kitchen stuff"));

        // @PrePersist derives slug when null: lowercase + whitespace collapsed to dashes
        assertThat(saved.getSlugCategory()).isEqualTo("home-appliances");
    }

    @Test
    void save_keepsExplicitSlugCategoryWhenProvided() {
        Category category = createCategory("Beverages", null);
        category.setSlugCategory("custom-beverage-slug");

        Category saved = categoryRepository.save(category);

        assertThat(saved.getSlugCategory()).isEqualTo("custom-beverage-slug");
    }

    @Test
    void findById_returnsSavedCategory() {
        Category saved = categoryRepository.save(createCategory("Coffee", "Arabica blend"));

        Optional<Category> found = categoryRepository.findById(saved.getCategoryId());

        assertThat(found).isPresent();
        assertThat(found.get().getName()).isEqualTo("Coffee");
        assertThat(found.get().getDescription()).isEqualTo("Arabica blend");
        assertThat(found.get().getSlugCategory()).isEqualTo("coffee");
    }

    @Test
    void findById_returnsEmptyWhenMissing() {
        assertThat(categoryRepository.findById(999999L)).isEmpty();
    }

    @Test
    void findBySlugCategory_returnsSavedCategory() {
        categoryRepository.save(createCategory("Frozen Food", null));

        Optional<Category> found = categoryRepository.findBySlugCategory("frozen-food");

        assertThat(found).isPresent();
        assertThat(found.get().getName()).isEqualTo("Frozen Food");
    }

    @Test
    void findBySlugCategory_returnsEmptyForUnknownSlug() {
        assertThat(categoryRepository.findBySlugCategory("no-such-slug")).isEmpty();
    }

    @Test
    void findAll_returnsAllPersistedCategories() {
        categoryRepository.save(createCategory("Coffee", null));
        categoryRepository.save(createCategory("Pastry", null));

        List<Category> all = categoryRepository.findAll();

        assertThat(all).extracting(Category::getName)
                .contains("Coffee", "Pastry");
    }

    @Test
    void update_touchesUpdatedAtViaPreUpdate() {
        Category saved = categoryRepository.save(createCategory("Before", null));
        LocalDateTime createdAtBefore = saved.getCreatedAt();

        saved.setName("After");
        saved.setDescription("Updated");
        Category updated = categoryRepository.saveAndFlush(saved);

        assertThat(updated.getName()).isEqualTo("After");
        assertThat(updated.getDescription()).isEqualTo("Updated");
        assertThat(updated.getCreatedAt()).isEqualTo(createdAtBefore);
        assertThat(updated.getUpdatedAt()).isNotNull();
    }

    @Test
    void delete_removesCategoryRow() {
        Category saved = categoryRepository.save(createCategory("DeleteMe", null));

        categoryRepository.delete(saved);
        categoryRepository.flush();

        assertThat(categoryRepository.findById(saved.getCategoryId())).isEmpty();
    }
}
