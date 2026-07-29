package tech.dobler.where2stream.accountaccess.application.command;

import tech.dobler.where2stream.accountaccess.domain.Role;
import tech.dobler.where2stream.shared.platform.api.ValidationException;

import java.util.List;

/** Create a new user account. Empty {@code roles} defaults to {@code [USER]}. */
public record CreateUserCommand(String username, String password, String email, List<Role> roles) {
    public CreateUserCommand {
        username = requireText(username, "username");
        password = requireText(password, "password");
        roles = roles == null || roles.isEmpty() ? List.of(Role.USER) : roles;
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new ValidationException(field + " must not be blank");
        }
        return value.trim();
    }
}
