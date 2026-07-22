package tech.dobler.werstreamt.application.dto;

import tech.dobler.werstreamt.domain.Role;

import java.util.List;

/** Body of {@code PUT /api/admin/users/{id}} — updates the mutable fields (not username/password). */
public record UpdateUserRequest(
        String email,
        List<Role> roles,
        boolean enabled
) {
}
