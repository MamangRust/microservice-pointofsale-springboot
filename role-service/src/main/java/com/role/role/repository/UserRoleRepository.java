package com.role.role.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.role.role.entity.UserRole;
import com.role.role.entity.UserRoleId;

public interface UserRoleRepository extends JpaRepository<UserRole, UserRoleId> {
    List<UserRole> findByUserId(Long userId);

    List<UserRole> findByRoleId(Long roleId);

    Optional<UserRole> findByUserIdAndRoleId(Long userId, Long roleId);

    @Query("SELECT r.roleName FROM UserRole ur JOIN Role r ON r.id = ur.roleId WHERE ur.userId = :userId")
    List<String> findRoleNamesByUserId(@Param("userId") Long userId);
}
