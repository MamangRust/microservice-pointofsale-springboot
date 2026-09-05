package com.category.category.dto;

import com.category.category.entity.Category;
import java.time.LocalDateTime;
import javax.annotation.processing.Generated;
import org.springframework.stereotype.Component;

@Generated(
    value = "org.mapstruct.ap.MappingProcessor",
    date = "2026-09-05T18:18:46+0700",
    comments = "version: 1.6.1, compiler: Eclipse JDT (IDE) 3.46.100.v20260624-0231, environment: Java 21.0.11 (Eclipse Adoptium)"
)
@Component
public class CategoryMapperImpl implements CategoryMapper {

    @Override
    public Category toEntity(CategoryRequest request) {
        if ( request == null ) {
            return null;
        }

        Category category = new Category();

        category.setDescription( request.description() );
        category.setName( request.name() );

        return category;
    }

    @Override
    public CategoryResponse toResponse(Category category) {
        if ( category == null ) {
            return null;
        }

        Long categoryId = null;
        String name = null;
        String description = null;
        String slugCategory = null;
        LocalDateTime createdAt = null;
        LocalDateTime updatedAt = null;

        categoryId = category.getCategoryId();
        name = category.getName();
        description = category.getDescription();
        slugCategory = category.getSlugCategory();
        createdAt = category.getCreatedAt();
        updatedAt = category.getUpdatedAt();

        CategoryResponse categoryResponse = new CategoryResponse( categoryId, name, description, slugCategory, createdAt, updatedAt );

        return categoryResponse;
    }
}
