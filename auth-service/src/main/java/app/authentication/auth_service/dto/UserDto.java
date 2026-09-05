package app.authentication.auth_service.dto;

import app.authentication.auth_service.enums.Role;
import lombok.Data;

import java.util.UUID;

@Data
public class UserDto {
    private UUID id;
    private String username;
    private String email;
    private String password;
    private Role role;
}
