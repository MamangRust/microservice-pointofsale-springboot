package com.role.role.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.groups.Tuple.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
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

import com.role.role.dto.RoleMapper;
import com.role.role.dto.RoleMapperImpl;
import com.role.role.dto.RoleRequest;
import com.role.role.entity.Role;
import com.role.role.entity.UserRole;
import com.role.role.repository.RoleRepository;
import com.role.role.repository.UserRoleRepository;

import io.opentelemetry.api.OpenTelemetry;

@ExtendWith(MockitoExtension.class)
class RoleServiceTest {

    @Mock
    private RoleRepository roleRepository;

    @Mock
    private UserRoleRepository userRoleRepository;

    private RoleService roleService;

    private final RoleMapper roleMapper = new RoleMapperImpl();

    @BeforeEach
    void setUp() {
        roleService = new RoleService(roleRepository, userRoleRepository, roleMapper, OpenTelemetry.noop());
    }

    private Role createRole(Long id, String roleName) {
        Role role = new Role();
        role.setId(id);
        role.setRoleName(roleName);
        return role;
    }

    private UserRole createUserRole(Long userId, Long roleId) {
        UserRole userRole = new UserRole();
        userRole.setUserId(userId);
        userRole.setRoleId(roleId);
        return userRole;
    }

    @Test
    void getAllRoles_returnsAllFromRepository() {
        when(roleRepository.findAll()).thenReturn(List.of(createRole(1L, "ROLE_ADMIN"), createRole(2L, "ROLE_STAFF")));

        List<Role> result = roleService.getAllRoles();

        assertThat(result).hasSize(2);
        assertThat(result).extracting(Role::getRoleName).containsExactly("ROLE_ADMIN", "ROLE_STAFF");
        verify(roleRepository).findAll();
    }

    @Test
    void getRoleById_returnsRoleWhenFound() {
        when(roleRepository.findById(1L)).thenReturn(Optional.of(createRole(1L, "ROLE_ADMIN")));

        Role result = roleService.getRoleById(1L);

        assertThat(result.getId()).isEqualTo(1L);
        assertThat(result.getRoleName()).isEqualTo("ROLE_ADMIN");
    }

    @Test
    void getRoleById_throwsWhenNotFound() {
        when(roleRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> roleService.getRoleById(999L))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Role not found: 999");
    }

    @Test
    void createRole_mapsRequestToEntityAndSaves() {
        when(roleRepository.findByRoleName("ROLE_NEW")).thenReturn(Optional.empty());
        when(roleRepository.save(any(Role.class))).thenAnswer(inv -> {
            Role role = inv.getArgument(0);
            role.setId(5L);
            return role;
        });

        Role result = roleService.createRole(new RoleRequest("ROLE_NEW"));

        assertThat(result.getId()).isEqualTo(5L);
        assertThat(result.getRoleName()).isEqualTo("ROLE_NEW");

        ArgumentCaptor<Role> captor = ArgumentCaptor.forClass(Role.class);
        verify(roleRepository).save(captor.capture());
        assertThat(captor.getValue().getRoleName()).isEqualTo("ROLE_NEW");
        verify(roleRepository).findByRoleName("ROLE_NEW");
    }

    @Test
    void createRole_throwsWhenRoleNameAlreadyExists() {
        when(roleRepository.findByRoleName("ROLE_ADMIN")).thenReturn(Optional.of(createRole(1L, "ROLE_ADMIN")));

        assertThatThrownBy(() -> roleService.createRole(new RoleRequest("ROLE_ADMIN")))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Role already exists: ROLE_ADMIN");

        verify(roleRepository, never()).save(any(Role.class));
    }

    @Test
    void updateRole_updatesRoleNameOnExisting() {
        Role existing = createRole(1L, "ROLE_OLD");
        when(roleRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(roleRepository.save(any(Role.class))).thenAnswer(inv -> inv.getArgument(0));

        Role result = roleService.updateRole(1L, new RoleRequest("ROLE_RENAMED"));

        assertThat(result.getRoleName()).isEqualTo("ROLE_RENAMED");
        verify(roleRepository).save(existing);
    }

    @Test
    void updateRole_throwsWhenNotFound() {
        when(roleRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> roleService.updateRole(999L, new RoleRequest("ROLE_X")))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Role not found: 999");

        verify(roleRepository, never()).save(any(Role.class));
    }

    @Test
    void deleteRole_deletesExistingRole() {
        Role existing = createRole(1L, "ROLE_DELETE_ME");
        when(roleRepository.findById(1L)).thenReturn(Optional.of(existing));

        roleService.deleteRole(1L);

        verify(roleRepository).delete(existing);
    }

    @Test
    void deleteRole_throwsWhenNotFound() {
        when(roleRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> roleService.deleteRole(999L))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Role not found: 999");

        verify(roleRepository, never()).delete(any(Role.class));
    }

    @Test
    void assignRolesToUser_deletesExistingAssignmentsThenInsertsNew() {
        UserRole existing = createUserRole(7L, 3L);
        when(userRoleRepository.findByUserId(7L)).thenReturn(List.of(existing));

        roleService.assignRolesToUser(7L, List.of(1L, 2L));

        verify(userRoleRepository).delete(existing);

        ArgumentCaptor<UserRole> captor = ArgumentCaptor.forClass(UserRole.class);
        verify(userRoleRepository, times(2)).save(captor.capture());
        assertThat(captor.getAllValues())
                .extracting(UserRole::getUserId, UserRole::getRoleId)
                .containsExactly(tuple(7L, 1L), tuple(7L, 2L));
    }

    @Test
    void assignRolesToUser_withNoExistingAssignmentsOnlyInserts() {
        when(userRoleRepository.findByUserId(7L)).thenReturn(List.of());

        roleService.assignRolesToUser(7L, List.of(1L));

        verify(userRoleRepository, never()).delete(any(UserRole.class));
        verify(userRoleRepository).save(createUserRole(7L, 1L));
    }

    @Test
    void removeRoleFromUser_deletesWhenAssignmentExists() {
        UserRole assignment = createUserRole(7L, 3L);
        when(userRoleRepository.findByUserIdAndRoleId(7L, 3L)).thenReturn(Optional.of(assignment));

        roleService.removeRoleFromUser(7L, 3L);

        verify(userRoleRepository).delete(assignment);
    }

    @Test
    void removeRoleFromUser_doesNothingWhenAssignmentMissing() {
        when(userRoleRepository.findByUserIdAndRoleId(7L, 3L)).thenReturn(Optional.empty());

        roleService.removeRoleFromUser(7L, 3L);

        verify(userRoleRepository, never()).delete(any(UserRole.class));
    }

    @Test
    void getUserRoles_returnsRoleNamesFromRepository() {
        when(userRoleRepository.findRoleNamesByUserId(7L)).thenReturn(List.of("ROLE_ADMIN", "ROLE_STAFF"));

        List<String> result = roleService.getUserRoles(7L);

        assertThat(result).containsExactly("ROLE_ADMIN", "ROLE_STAFF");
        verify(userRoleRepository).findRoleNamesByUserId(7L);
    }
}
