package com.role.role.service;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.api.metrics.Meter;
import io.opentelemetry.api.metrics.LongCounter;
import io.opentelemetry.api.metrics.DoubleHistogram;
import io.opentelemetry.context.Scope;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.common.AttributeKey;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.role.role.dto.RoleMapper;
import com.role.role.dto.RoleRequest;
import com.role.role.entity.Role;
import com.role.role.entity.UserRole;
import com.role.role.repository.RoleRepository;
import com.role.role.repository.UserRoleRepository;

import java.util.List;

@Service
public class RoleService {
    private static final Logger log = LoggerFactory.getLogger(RoleService.class);

    private final RoleRepository roleRepository;
    private final UserRoleRepository userRoleRepository;
    private final RoleMapper roleMapper;
    private final Tracer tracer;
    private final Meter meter;
    private final LongCounter requestsTotal;
    private final DoubleHistogram requestsDurationSeconds;
    private final LongCounter failureTotal;

    public RoleService(RoleRepository roleRepository,
                       UserRoleRepository userRoleRepository,
                       RoleMapper roleMapper,
                       OpenTelemetry openTelemetry) {
        this.roleRepository = roleRepository;
        this.userRoleRepository = userRoleRepository;
        this.roleMapper = roleMapper;
        this.tracer = openTelemetry.getTracer("role-service", "1.0.0");
        this.meter = openTelemetry.getMeter("role-service");
        this.requestsTotal = meter.counterBuilder("requests_total")
            .setDescription("Total requests").setUnit("1").build();
        this.requestsDurationSeconds = meter.histogramBuilder("requests_duration_seconds")
            .setDescription("Request duration").setUnit("s").build();
        this.failureTotal = meter.counterBuilder("failure_total")
            .setDescription("Total failures").setUnit("1").build();
    }

    public List<Role> getAllRoles() {
        return roleRepository.findAll();
    }

    public Role getRoleById(Long id) {
        return roleRepository.findById(id)
            .orElseThrow(() -> new RuntimeException("Role not found: " + id));
    }

    public Role createRole(RoleRequest request) {
        if (roleRepository.findByRoleName(request.roleName()).isPresent()) {
            throw new RuntimeException("Role already exists: " + request.roleName());
        }
        Role role = roleMapper.toEntity(request);
        return roleRepository.save(role);
    }

    public Role updateRole(Long id, RoleRequest request) {
        Role role = getRoleById(id);
        role.setRoleName(request.roleName());
        return roleRepository.save(role);
    }

    public void deleteRole(Long id) {
        Role role = getRoleById(id);
        roleRepository.delete(role);
    }

    @Transactional
    public void assignRolesToUser(Long userId, List<Long> roleIds) {
        userRoleRepository.findByUserId(userId).forEach(ur ->
            userRoleRepository.delete(ur));
        roleIds.forEach(roleId -> {
            UserRole ur = new UserRole();
            ur.setUserId(userId);
            ur.setRoleId(roleId);
            userRoleRepository.save(ur);
        });
    }

    @Transactional
    public void removeRoleFromUser(Long userId, Long roleId) {
        userRoleRepository.findByUserIdAndRoleId(userId, roleId)
            .ifPresent(userRoleRepository::delete);
    }

    public List<String> getUserRoles(Long userId) {
        return userRoleRepository.findRoleNamesByUserId(userId);
    }
}