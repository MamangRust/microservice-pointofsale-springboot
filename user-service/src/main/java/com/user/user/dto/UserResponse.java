package com.user.user.dto;

import java.util.UUID;

import com.user.user.enums.Role;

import lombok.Data;

@Data
public class UserResponse {
    private UUID id;
    private String username;
    private String email;
    private String password;
    private Role role;
}
