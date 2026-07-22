package tech.dobler.werstreamt.application.dto;

import tech.dobler.werstreamt.domain.Role;

import java.util.List;

/** Body of {@code POST /api/admin/users}. Empty {@code roles} defaults to {@code [USER]}. */
public record CreateUserRequest(
        String username,
        String password,
        String email,
        List<Role> roles
) {
}
