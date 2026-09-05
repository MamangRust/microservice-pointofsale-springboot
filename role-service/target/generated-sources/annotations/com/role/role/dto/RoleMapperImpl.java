package com.role.role.dto;

import com.role.role.entity.Role;
import java.time.LocalDateTime;
import javax.annotation.processing.Generated;
import org.springframework.stereotype.Component;

@Generated(
    value = "org.mapstruct.ap.MappingProcessor",
    date = "2026-09-05T18:19:08+0700",
    comments = "version: 1.6.1, compiler: Eclipse JDT (IDE) 3.46.100.v20260624-0231, environment: Java 21.0.11 (Eclipse Adoptium)"
)
@Component
public class RoleMapperImpl implements RoleMapper {

    @Override
    public Role toEntity(RoleRequest roleRequest) {
        if ( roleRequest == null ) {
            return null;
        }

        Role role = new Role();

        role.setRoleName( roleRequest.roleName() );

        return role;
    }

    @Override
    public RoleResponse toResponse(Role role) {
        if ( role == null ) {
            return null;
        }

        Long id = null;
        String roleName = null;
        LocalDateTime createdAt = null;
        LocalDateTime updatedAt = null;

        id = role.getId();
        roleName = role.getRoleName();
        createdAt = role.getCreatedAt();
        updatedAt = role.getUpdatedAt();

        RoleResponse roleResponse = new RoleResponse( id, roleName, createdAt, updatedAt );

        return roleResponse;
    }
}
