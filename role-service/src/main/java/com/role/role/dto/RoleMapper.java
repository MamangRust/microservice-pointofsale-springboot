package com.role.role.dto;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants.ComponentModel;

import com.role.role.entity.Role;

@Mapper(componentModel = ComponentModel.SPRING)
public interface RoleMapper {
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "deletedAt", ignore = true)
    Role toEntity(RoleRequest roleRequest);

    RoleResponse toResponse(Role role);
}