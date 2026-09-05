package com.category.category.service;

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

import com.category.category.dto.CategoryMapper;
import com.category.category.dto.CategoryMapperImpl;
import com.category.category.dto.CategoryRequest;
import com.category.category.entity.Category;
import com.category.category.repository.CategoryRepository;

import io.opentelemetry.api.OpenTelemetry;

@ExtendWith(MockitoExtension.class)
class CategoryServiceTest {

    @Mock
    private CategoryRepository categoryRepository;

    private CategoryService categoryService;

    private final CategoryMapper categoryMapper = new CategoryMapperImpl();

    @BeforeEach
    void setUp() {
        categoryService = new CategoryService(categoryRepository, categoryMapper, OpenTelemetry.noop());
    }

    private Category createCategory(Long id, String name, String slug) {
        Category category = new Category();
        category.setCategoryId(id);
        category.setName(name);
        category.setSlugCategory(slug);
        return category;
    }

    @Test
    void getAll_returnsAllFromRepository() {
        when(categoryRepository.findAll())
                .thenReturn(List.of(createCategory(1L, "Coffee", "coffee"), createCategory(2L, "Pastry", "pastry")));

        List<Category> result = categoryService.getAll();

        assertThat(result).hasSize(2);
        assertThat(result).extracting(Category::getName).containsExactly("Coffee", "Pastry");
        verify(categoryRepository).findAll();
    }

    @Test
    void getById_returnsCategoryWhenFound() {
        when(categoryRepository.findById(1L)).thenReturn(Optional.of(createCategory(1L, "Coffee", "coffee")));

        Category result = categoryService.getById(1L);

        assertThat(result.getCategoryId()).isEqualTo(1L);
        assertThat(result.getName()).isEqualTo("Coffee");
    }

    @Test
    void getById_throwsWhenNotFound() {
        when(categoryRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> categoryService.getById(999L))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("Category not found");
    }

    @Test
    void create_mapsRequestToEntityAndSaves() {
        when(categoryRepository.findBySlugCategory("coffee")).thenReturn(Optional.empty());
        when(categoryRepository.save(any(Category.class))).thenAnswer(inv -> {
            Category c = inv.getArgument(0);
            c.setCategoryId(5L);
            return c;
        });

        Category result = categoryService.create(new CategoryRequest("Coffee", "Arabica blend"));

        assertThat(result.getCategoryId()).isEqualTo(5L);

        ArgumentCaptor<Category> captor = ArgumentCaptor.forClass(Category.class);
        verify(categoryRepository).save(captor.capture());
        Category entity = captor.getValue();
        assertThat(entity.getName()).isEqualTo("Coffee");
        assertThat(entity.getDescription()).isEqualTo("Arabica blend");
        assertThat(entity.getSlugCategory()).isNull();
        verify(categoryRepository).findBySlugCategory("coffee");
    }

    @Test
    void create_derivesSlugFromNameForDuplicateCheck() {
        when(categoryRepository.findBySlugCategory("iced-coffee")).thenReturn(Optional.empty());
        when(categoryRepository.save(any(Category.class))).thenAnswer(inv -> inv.getArgument(0));

        categoryService.create(new CategoryRequest("Iced Coffee", null));

        verify(categoryRepository).findBySlugCategory("iced-coffee");
    }

    @Test
    void create_throwsWhenSlugAlreadyExists() {
        when(categoryRepository.findBySlugCategory("coffee"))
                .thenReturn(Optional.of(createCategory(1L, "Coffee", "coffee")));

        assertThatThrownBy(() -> categoryService.create(new CategoryRequest("Coffee", null)))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("Category already exists");

        verify(categoryRepository, never()).save(any(Category.class));
    }

    @Test
    void update_updatesNameAndDescriptionOnly() {
        Category existing = createCategory(1L, "OldName", "old-slug");
        existing.setDescription("OldDesc");

        when(categoryRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(categoryRepository.save(any(Category.class))).thenAnswer(inv -> inv.getArgument(0));

        Category result = categoryService.update(1L, new CategoryRequest("NewName", "NewDesc"));

        assertThat(result.getName()).isEqualTo("NewName");
        assertThat(result.getDescription()).isEqualTo("NewDesc");
        // update must not touch the slug
        assertThat(result.getSlugCategory()).isEqualTo("old-slug");
        verify(categoryRepository).save(existing);
    }

    @Test
    void update_throwsWhenNotFound() {
        when(categoryRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> categoryService.update(999L, new CategoryRequest("X", null)))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("Category not found");

        verify(categoryRepository, never()).save(any(Category.class));
    }

    @Test
    void delete_callsDeleteById() {
        categoryService.delete(1L);

        verify(categoryRepository).deleteById(1L);
    }
}
