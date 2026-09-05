package com.category.category.controller;

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

import com.category.category.dto.CategoryMapper;
import com.category.category.dto.CategoryMapperImpl;
import com.category.category.dto.CategoryRequest;
import com.category.category.entity.Category;
import com.category.category.exc.GeneralExceptionHandler;
import com.category.category.service.CategoryService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

@ExtendWith(MockitoExtension.class)
class CategoryControllerTest {

    @Mock
    private CategoryService categoryService;

    private MockMvc mockMvc;

    private final CategoryMapper categoryMapper = new CategoryMapperImpl();

    private final ObjectMapper objectMapper = JsonMapper.builder()
            .addModule(new JavaTimeModule())
            .build();

    @BeforeEach
    void setUp() {
        CategoryController controller = new CategoryController(categoryService, categoryMapper);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GeneralExceptionHandler())
                .setMessageConverters(new MappingJackson2HttpMessageConverter(objectMapper))
                .build();
    }

    private Category createCategory(Long id, String name, String slug) {
        Category category = new Category();
        category.setCategoryId(id);
        category.setName(name);
        category.setSlugCategory(slug);
        return category;
    }

    @Test
    void getAll_returnsMappedList() throws Exception {
        when(categoryService.getAll()).thenReturn(List.of(createCategory(1L, "Coffee", "coffee")));

        mockMvc.perform(get("/categories"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].categoryId").value(1))
                .andExpect(jsonPath("$[0].name").value("Coffee"))
                .andExpect(jsonPath("$[0].slugCategory").value("coffee"));
    }

    @Test
    void getAll_returnsEmptyListWhenNone() throws Exception {
        when(categoryService.getAll()).thenReturn(List.of());

        mockMvc.perform(get("/categories"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    void getById_returnsResponse() throws Exception {
        when(categoryService.getById(1L)).thenReturn(createCategory(1L, "Coffee", "coffee"));

        mockMvc.perform(get("/categories/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.categoryId").value(1))
                .andExpect(jsonPath("$.name").value("Coffee"))
                .andExpect(jsonPath("$.slugCategory").value("coffee"));
    }

    @Test
    void getById_returns404WhenNotFound() throws Exception {
        when(categoryService.getById(99L)).thenThrow(new RuntimeException("Category not found"));

        mockMvc.perform(get("/categories/99"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$").value("Category not found"));
    }

    @Test
    void create_returnsResponse() throws Exception {
        CategoryRequest request = new CategoryRequest("Tea", "Hot beverages");

        when(categoryService.create(any(CategoryRequest.class))).thenReturn(createCategory(5L, "Tea", "tea"));

        mockMvc.perform(post("/categories")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.categoryId").value(5))
                .andExpect(jsonPath("$.name").value("Tea"))
                .andExpect(jsonPath("$.slugCategory").value("tea"));
    }

    @Test
    void create_returns409WhenAlreadyExists() throws Exception {
        CategoryRequest request = new CategoryRequest("Coffee", null);

        when(categoryService.create(any(CategoryRequest.class)))
                .thenThrow(new RuntimeException("Category already exists"));

        mockMvc.perform(post("/categories")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$").value("Category already exists"));
    }

    @Test
    void create_returns500WhenOtherFailure() throws Exception {
        CategoryRequest request = new CategoryRequest("Tea", null);

        when(categoryService.create(any(CategoryRequest.class))).thenThrow(new RuntimeException("db down"));

        mockMvc.perform(post("/categories")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$").value("db down"));
    }

    @Test
    void create_returns400WhenNameBlank() throws Exception {
        CategoryRequest request = new CategoryRequest(" ", null);

        mockMvc.perform(post("/categories")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Validation Error"));

        verify(categoryService, never()).create(any(CategoryRequest.class));
    }

    @Test
    void create_returns400WhenNameTooLong() throws Exception {
        CategoryRequest request = new CategoryRequest("C".repeat(101), null);

        mockMvc.perform(post("/categories")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());

        verify(categoryService, never()).create(any(CategoryRequest.class));
    }

    @Test
    void update_returnsUpdatedResponse() throws Exception {
        CategoryRequest request = new CategoryRequest("UpdatedTea", "Updated desc");

        when(categoryService.update(eq(1L), any(CategoryRequest.class)))
                .thenReturn(createCategory(1L, "UpdatedTea", "tea"));

        mockMvc.perform(put("/categories/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.categoryId").value(1))
                .andExpect(jsonPath("$.name").value("UpdatedTea"));
    }

    @Test
    void update_returns404WhenNotFound() throws Exception {
        CategoryRequest request = new CategoryRequest("UpdatedTea", null);

        when(categoryService.update(eq(99L), any(CategoryRequest.class)))
                .thenThrow(new RuntimeException("Category not found"));

        mockMvc.perform(put("/categories/99")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$").value("Category not found"));
    }

    @Test
    void update_returns400WhenNameBlank() throws Exception {
        CategoryRequest request = new CategoryRequest(" ", null);

        mockMvc.perform(put("/categories/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());

        verify(categoryService, never()).update(any(), any(CategoryRequest.class));
    }

    @Test
    void delete_returnsSuccessMessage() throws Exception {
        mockMvc.perform(delete("/categories/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").value("Category deleted"));

        verify(categoryService).delete(1L);
    }

    @Test
    void delete_returns404WhenNotFound() throws Exception {
        doThrow(new RuntimeException("Category not found")).when(categoryService).delete(99L);

        mockMvc.perform(delete("/categories/99"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$").value("Category not found"));
    }

    @Test
    void getById_responseContainsTimestamps() throws Exception {
        Category category = createCategory(1L, "Coffee", "coffee");
        category.setCreatedAt(LocalDateTime.of(2026, 9, 4, 10, 0));

        when(categoryService.getById(1L)).thenReturn(category);

        mockMvc.perform(get("/categories/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.createdAt[0]").value(2026));
    }
}
