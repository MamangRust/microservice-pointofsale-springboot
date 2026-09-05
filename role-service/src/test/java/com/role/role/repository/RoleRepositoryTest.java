package com.role.role.repository;

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

import com.role.role.entity.Role;
import com.role.role.entity.UserRole;
import com.role.role.entity.UserRoleId;

@DataJpaTest(properties = {
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.flyway.enabled=true"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers(disabledWithoutDocker = true)
class RoleRepositoryTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine");

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private UserRoleRepository userRoleRepository;

    private Role createRole(String roleName) {
        Role role = new Role();
        role.setRoleName(roleName);
        return role;
    }

    private UserRole createUserRole(Long userId, Long roleId) {
        UserRole userRole = new UserRole();
        userRole.setUserId(userId);
        userRole.setRoleId(roleId);
        return userRole;
    }

    // ---- RoleRepository ----

    @Test
    void save_persistsRoleWithGeneratedIdAndTimestamps() {
        Role saved = roleRepository.save(createRole("ROLE_REPO_TEST"));

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.getUpdatedAt()).isNotNull();
        assertThat(saved.getDeletedAt()).isNull();
    }

    @Test
    void findById_returnsSavedRole() {
        Role saved = roleRepository.save(createRole("ROLE_FIND_ME"));

        Optional<Role> found = roleRepository.findById(saved.getId());

        assertThat(found).isPresent();
        assertThat(found.get().getRoleName()).isEqualTo("ROLE_FIND_ME");
    }

    @Test
    void findById_returnsEmptyWhenMissing() {
        assertThat(roleRepository.findById(999999L)).isEmpty();
    }

    @Test
    void findAll_containsMigrationSeedRoles() {
        List<Role> all = roleRepository.findAll();

        assertThat(all).extracting(Role::getRoleName)
                .contains("ROLE_ADMIN", "ROLE_STAFF", "ROLE_USER");
    }

    @Test
    void findByRoleName_returnsSeededRole() {
        Optional<Role> found = roleRepository.findByRoleName("ROLE_ADMIN");

        assertThat(found).isPresent();
        assertThat(found.get().getRoleName()).isEqualTo("ROLE_ADMIN");
    }

    @Test
    void findByRoleName_returnsSavedRole() {
        roleRepository.save(createRole("ROLE_LOOKUP_TEST"));

        Optional<Role> found = roleRepository.findByRoleName("ROLE_LOOKUP_TEST");

        assertThat(found).isPresent();
        assertThat(found.get().getRoleName()).isEqualTo("ROLE_LOOKUP_TEST");
    }

    @Test
    void findByRoleName_returnsEmptyForUnknownName() {
        assertThat(roleRepository.findByRoleName("ROLE_DOES_NOT_EXIST")).isEmpty();
    }

    @Test
    void update_touchesUpdatedAtViaPreUpdate() {
        Role saved = roleRepository.save(createRole("ROLE_UPDATE_ME"));
        LocalDateTime createdAtBefore = saved.getCreatedAt();

        saved.setRoleName("ROLE_UPDATED_NAME");
        Role updated = roleRepository.saveAndFlush(saved);

        assertThat(updated.getRoleName()).isEqualTo("ROLE_UPDATED_NAME");
        assertThat(updated.getCreatedAt()).isEqualTo(createdAtBefore);
        assertThat(updated.getUpdatedAt()).isNotNull();
    }

    @Test
    void delete_removesRoleRow() {
        Role saved = roleRepository.save(createRole("ROLE_DELETE_ME"));

        roleRepository.delete(saved);
        roleRepository.flush();

        assertThat(roleRepository.findById(saved.getId())).isEmpty();
    }

    // ---- UserRoleRepository (composite @IdClass(UserRoleId)) ----

    @Test
    void save_persistsUserRoleWithCompositeKey() {
        Role role = roleRepository.save(createRole("ROLE_ASSIGN_TEST"));

        UserRole saved = userRoleRepository.save(createUserRole(9100L, role.getId()));

        assertThat(saved.getUserId()).isEqualTo(9100L);
        assertThat(saved.getRoleId()).isEqualTo(role.getId());

        Optional<UserRole> found = userRoleRepository.findById(new UserRoleId(9100L, role.getId()));
        assertThat(found).isPresent();
        assertThat(found.get().getUserId()).isEqualTo(9100L);
        assertThat(found.get().getRoleId()).isEqualTo(role.getId());
    }

    @Test
    void findById_returnsEmptyWhenCompositeKeyMissing() {
        assertThat(userRoleRepository.findById(new UserRoleId(999999L, 999999L))).isEmpty();
    }

    @Test
    void save_withSameCompositeKeyDoesNotDuplicateRow() {
        // Spring Data save() on an assigned composite id routes through merge():
        // the second save finds the existing row, so no PK violation is thrown
        // and no duplicate row is created.
        Role role = roleRepository.save(createRole("ROLE_DUP_TEST"));

        userRoleRepository.saveAndFlush(createUserRole(9101L, role.getId()));
        userRoleRepository.saveAndFlush(createUserRole(9101L, role.getId()));

        assertThat(userRoleRepository.findByUserId(9101L)).hasSize(1);
        assertThat(userRoleRepository.findByUserIdAndRoleId(9101L, role.getId())).isPresent();
    }

    @Test
    void findByUserId_returnsOnlyThatUsersRoles() {
        Role r1 = roleRepository.save(createRole("ROLE_USER_QUERY_A"));
        Role r2 = roleRepository.save(createRole("ROLE_USER_QUERY_B"));
        Role r3 = roleRepository.save(createRole("ROLE_USER_QUERY_C"));

        userRoleRepository.save(createUserRole(9200L, r1.getId()));
        userRoleRepository.save(createUserRole(9200L, r2.getId()));
        userRoleRepository.save(createUserRole(9201L, r3.getId()));

        List<UserRole> result = userRoleRepository.findByUserId(9200L);

        assertThat(result).hasSize(2);
        assertThat(result).extracting(UserRole::getRoleId)
                .containsExactlyInAnyOrder(r1.getId(), r2.getId());
    }

    @Test
    void findByUserId_returnsEmptyForUnknownUser() {
        assertThat(userRoleRepository.findByUserId(999999L)).isEmpty();
    }

    @Test
    void findByRoleId_returnsAllUsersWithThatRole() {
        Role role = roleRepository.save(createRole("ROLE_ROLE_QUERY"));

        userRoleRepository.save(createUserRole(9300L, role.getId()));
        userRoleRepository.save(createUserRole(9301L, role.getId()));

        List<UserRole> result = userRoleRepository.findByRoleId(role.getId());

        assertThat(result).hasSize(2);
        assertThat(result).extracting(UserRole::getUserId)
                .containsExactlyInAnyOrder(9300L, 9301L);
    }

    @Test
    void findByUserIdAndRoleId_returnsMatch() {
        Role role = roleRepository.save(createRole("ROLE_PAIR_QUERY"));

        userRoleRepository.save(createUserRole(9400L, role.getId()));

        Optional<UserRole> found = userRoleRepository.findByUserIdAndRoleId(9400L, role.getId());

        assertThat(found).isPresent();
        assertThat(found.get().getUserId()).isEqualTo(9400L);
        assertThat(found.get().getRoleId()).isEqualTo(role.getId());
    }

    @Test
    void findByUserIdAndRoleId_returnsEmptyWhenPairMissing() {
        Role role = roleRepository.save(createRole("ROLE_PAIR_MISSING"));

        userRoleRepository.save(createUserRole(9401L, role.getId()));

        assertThat(userRoleRepository.findByUserIdAndRoleId(9401L, role.getId() + 1)).isEmpty();
        assertThat(userRoleRepository.findByUserIdAndRoleId(999999L, role.getId())).isEmpty();
    }

    @Test
    void findRoleNamesByUserId_joinsRoleTable() {
        Role own = roleRepository.save(createRole("ROLE_JOIN_CHECK"));
        Role seeded = roleRepository.findByRoleName("ROLE_ADMIN").orElseThrow();

        userRoleRepository.save(createUserRole(9500L, own.getId()));
        userRoleRepository.save(createUserRole(9500L, seeded.getId()));

        List<String> roleNames = userRoleRepository.findRoleNamesByUserId(9500L);

        assertThat(roleNames).containsExactlyInAnyOrder("ROLE_JOIN_CHECK", "ROLE_ADMIN");
    }

    @Test
    void findRoleNamesByUserId_returnsEmptyForUnknownUser() {
        assertThat(userRoleRepository.findRoleNamesByUserId(999999L)).isEmpty();
    }

    @Test
    void deleteByCompositeKey_removesRow() {
        Role role = roleRepository.save(createRole("ROLE_REMOVE_CHECK"));
        UserRole saved = userRoleRepository.save(createUserRole(9600L, role.getId()));

        userRoleRepository.delete(saved);
        userRoleRepository.flush();

        assertThat(userRoleRepository.findById(new UserRoleId(9600L, role.getId()))).isEmpty();
    }
}
