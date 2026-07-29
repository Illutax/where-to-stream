package tech.dobler.where2stream.accountaccess.application.command;

import tech.dobler.where2stream.accountaccess.domain.Role;

import java.util.List;
import java.util.UUID;

/** Update a user's mutable admin-managed fields (not username/password). Empty {@code roles} defaults to {@code [USER]}. */
public record UpdateUserCommand(UUID id, String email, List<Role> roles, boolean enabled) {
    public UpdateUserCommand {
        roles = roles == null || roles.isEmpty() ? List.of(Role.USER) : roles;
    }
}
