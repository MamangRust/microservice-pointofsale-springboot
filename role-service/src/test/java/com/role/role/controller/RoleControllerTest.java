package com.role.role.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.doThrow;
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

import com.role.role.dto.RoleMapper;
import com.role.role.dto.RoleMapperImpl;
import com.role.role.dto.RoleRequest;
import com.role.role.entity.Role;
import com.role.role.exc.GeneralExceptionHandler;
import com.role.role.service.RoleService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

@ExtendWith(MockitoExtension.class)
class RoleControllerTest {

    @Mock
    private RoleService roleService;

    private MockMvc mockMvc;

    private final RoleMapper roleMapper = new RoleMapperImpl();

    private final ObjectMapper objectMapper = JsonMapper.builder()
            .addModule(new JavaTimeModule())
            .build();

    @BeforeEach
    void setUp() {
        RoleController controller = new RoleController(roleService, roleMapper);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GeneralExceptionHandler())
                .setMessageConverters(new MappingJackson2HttpMessageConverter(objectMapper))
                .build();
    }

    private Role createRole(Long id, String roleName) {
        Role role = new Role();
        role.setId(id);
        role.setRoleName(roleName);
        return role;
    }

    @Test
    void getAllRoles_returnsMappedList() throws Exception {
        when(roleService.getAllRoles()).thenReturn(List.of(createRole(1L, "ROLE_ADMIN")));

        mockMvc.perform(get("/roles"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(1))
                .andExpect(jsonPath("$[0].roleName").value("ROLE_ADMIN"));
    }

    @Test
    void getAllRoles_returnsEmptyListWhenNone() throws Exception {
        when(roleService.getAllRoles()).thenReturn(List.of());

        mockMvc.perform(get("/roles"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    void getRoleById_returnsResponse() throws Exception {
        when(roleService.getRoleById(1L)).thenReturn(createRole(1L, "ROLE_ADMIN"));

        mockMvc.perform(get("/roles/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.roleName").value("ROLE_ADMIN"));
    }

    @Test
    void getRoleById_returns404WhenNotFound() throws Exception {
        when(roleService.getRoleById(99L)).thenThrow(new RuntimeException("Role not found: 99"));

        mockMvc.perform(get("/roles/99"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$").value("Role not found: 99"));
    }

    @Test
    void createRole_returnsResponse() throws Exception {
        RoleRequest request = new RoleRequest("ROLE_NEW");

        when(roleService.createRole(any(RoleRequest.class))).thenReturn(createRole(5L, "ROLE_NEW"));

        mockMvc.perform(post("/roles")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(5))
                .andExpect(jsonPath("$.roleName").value("ROLE_NEW"));
    }

    @Test
    void createRole_returns409WhenAlreadyExists() throws Exception {
        RoleRequest request = new RoleRequest("ROLE_ADMIN");

        when(roleService.createRole(any(RoleRequest.class)))
                .thenThrow(new RuntimeException("Role already exists: ROLE_ADMIN"));

        mockMvc.perform(post("/roles")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$").value("Role already exists: ROLE_ADMIN"));
    }

    @Test
    void createRole_returns500WhenOtherFailure() throws Exception {
        RoleRequest request = new RoleRequest("ROLE_NEW");

        when(roleService.createRole(any(RoleRequest.class))).thenThrow(new RuntimeException("db down"));

        mockMvc.perform(post("/roles")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$").value("db down"));
    }

    @Test
    void createRole_returns400WhenNameBlank() throws Exception {
        RoleRequest request = new RoleRequest(" ");

        mockMvc.perform(post("/roles")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Validation Error"));

        verify(roleService, never()).createRole(any(RoleRequest.class));
    }

    @Test
    void createRole_returns400WhenNameTooLong() throws Exception {
        RoleRequest request = new RoleRequest("R".repeat(51));

        mockMvc.perform(post("/roles")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());

        verify(roleService, never()).createRole(any(RoleRequest.class));
    }

    @Test
    void updateRole_returnsUpdatedResponse() throws Exception {
        RoleRequest request = new RoleRequest("ROLE_RENAMED");

        when(roleService.updateRole(eq(1L), any(RoleRequest.class))).thenReturn(createRole(1L, "ROLE_RENAMED"));

        mockMvc.perform(put("/roles/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.roleName").value("ROLE_RENAMED"));
    }

    @Test
    void updateRole_returns404WhenNotFound() throws Exception {
        RoleRequest request = new RoleRequest("ROLE_X");

        when(roleService.updateRole(eq(99L), any(RoleRequest.class)))
                .thenThrow(new RuntimeException("Role not found: 99"));

        mockMvc.perform(put("/roles/99")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$").value("Role not found: 99"));
    }

    @Test
    void updateRole_returns400WhenNameBlank() throws Exception {
        RoleRequest request = new RoleRequest(" ");

        mockMvc.perform(put("/roles/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());

        verify(roleService, never()).updateRole(any(), any(RoleRequest.class));
    }

    @Test
    void deleteRole_returnsSuccessMessage() throws Exception {
        mockMvc.perform(delete("/roles/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").value("Role deleted successfully"));

        verify(roleService).deleteRole(1L);
    }

    @Test
    void deleteRole_returns404WhenNotFound() throws Exception {
        doThrow(new RuntimeException("Role not found: 99")).when(roleService).deleteRole(99L);

        mockMvc.perform(delete("/roles/99"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$").value("Role not found: 99"));
    }

    @Test
    void assignRoles_returnsSuccessMessage() throws Exception {
        String body = "{\"userId\": 7, \"roleIds\": [1, 2]}";

        mockMvc.perform(post("/roles/assign")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").value("Roles assigned successfully"));

        verify(roleService).assignRolesToUser(7L, List.of(1L, 2L));
    }

    @Test
    void removeRole_returnsSuccessMessage() throws Exception {
        mockMvc.perform(delete("/roles/3/users/7"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").value("Role removed from user"));

        verify(roleService).removeRoleFromUser(7L, 3L);
    }

    @Test
    void getUserRoles_returnsRoleNameList() throws Exception {
        when(roleService.getUserRoles(7L)).thenReturn(List.of("ROLE_ADMIN", "ROLE_STAFF"));

        mockMvc.perform(get("/roles/users/7"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0]").value("ROLE_ADMIN"))
                .andExpect(jsonPath("$[1]").value("ROLE_STAFF"));
    }

    @Test
    void getUserRoles_returnsEmptyListWhenNoAssignments() throws Exception {
        when(roleService.getUserRoles(7L)).thenReturn(List.of());

        mockMvc.perform(get("/roles/users/7"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    void getRoleById_responseContainsTimestamps() throws Exception {
        Role role = createRole(1L, "ROLE_ADMIN");
        role.setCreatedAt(LocalDateTime.of(2026, 9, 4, 10, 0));

        when(roleService.getRoleById(1L)).thenReturn(role);

        mockMvc.perform(get("/roles/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.createdAt[0]").value(2026));
    }
}
