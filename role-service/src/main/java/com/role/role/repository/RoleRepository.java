package com.role.role.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.role.role.entity.Role;

public interface RoleRepository extends JpaRepository<Role, Long> {
    Optional<Role> findByRoleName(String roleName);
}
