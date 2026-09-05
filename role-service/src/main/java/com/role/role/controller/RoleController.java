package com.role.role.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import com.role.role.dto.AssignRoleRequest;
import com.role.role.dto.RoleMapper;
import com.role.role.dto.RoleRequest;
import com.role.role.entity.Role;
import com.role.role.service.RoleService;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/roles")
@Tag(name = "Role Management", description = "Role CRUD + user assignment")
@SecurityRequirement(name = "Bearer Authentication")
public class RoleController {
    private final RoleService roleService;
    private final RoleMapper roleMapper;

    public RoleController(RoleService roleService, RoleMapper roleMapper) {
        this.roleService = roleService;
        this.roleMapper = roleMapper;
    }

    @GetMapping
    @Operation(summary = "Get all roles")
    public ResponseEntity<?> getAllRoles() {
        List<Role> roles = roleService.getAllRoles();
        return ResponseEntity.ok(roles.stream().map(roleMapper::toResponse).collect(Collectors.toList()));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get role by ID")
    public ResponseEntity<?> getRoleById(@PathVariable Long id) {
        try {
            return ResponseEntity.ok(roleMapper.toResponse(roleService.getRoleById(id)));
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(e.getMessage());
        }
    }

    @PostMapping
    @Operation(summary = "Create a new role")
    @ApiResponses(@ApiResponse(responseCode = "200", description = "Role created"))
    public ResponseEntity<?> createRole(@Valid @RequestBody RoleRequest request) {
        try {
            Role role = roleService.createRole(request);
            return ResponseEntity.ok(roleMapper.toResponse(role));
        } catch (RuntimeException e) {
            if (e.getMessage().contains("already exists")) {
                return ResponseEntity.status(HttpStatus.CONFLICT).body(e.getMessage());
            }
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update a role")
    public ResponseEntity<?> updateRole(@PathVariable Long id, @Valid @RequestBody RoleRequest request) {
        try {
            Role role = roleService.updateRole(id, request);
            return ResponseEntity.ok(roleMapper.toResponse(role));
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(e.getMessage());
        }
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete a role")
    public ResponseEntity<?> deleteRole(@PathVariable Long id) {
        try {
            roleService.deleteRole(id);
            return ResponseEntity.ok("Role deleted successfully");
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(e.getMessage());
        }
    }

    @PostMapping("/assign")
    @Operation(summary = "Assign roles to a user")
    public ResponseEntity<?> assignRoles(@Valid @RequestBody AssignRoleRequest request) {
        roleService.assignRolesToUser(request.userId(), request.roleIds());
        return ResponseEntity.ok("Roles assigned successfully");
    }

    @DeleteMapping("/{roleId}/users/{userId}")
    @Operation(summary = "Remove a role from a user")
    public ResponseEntity<?> removeRole(@PathVariable Long userId, @PathVariable Long roleId) {
        roleService.removeRoleFromUser(userId, roleId);
        return ResponseEntity.ok("Role removed from user");
    }

    @GetMapping("/users/{userId}")
    @Operation(summary = "Get roles for a user")
    public ResponseEntity<?> getUserRoles(@PathVariable Long userId) {
        List<String> roles = roleService.getUserRoles(userId);
        return ResponseEntity.ok(roles);
    }
}