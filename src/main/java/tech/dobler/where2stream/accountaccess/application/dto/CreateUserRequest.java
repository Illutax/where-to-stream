package tech.dobler.where2stream.accountaccess.application.dto;

import tech.dobler.where2stream.accountaccess.domain.Role;

import java.util.List;

/** Body of {@code POST /api/admin/users}. Empty {@code roles} defaults to {@code [USER]}. */
public record CreateUserRequest(
        String username,
        String password,
        String email,
        List<Role> roles
) {
}
