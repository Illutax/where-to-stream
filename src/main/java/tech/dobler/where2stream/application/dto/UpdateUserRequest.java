package tech.dobler.where2stream.application.dto;

import tech.dobler.where2stream.domain.Role;

import java.util.List;

/** Body of {@code PUT /api/admin/users/{id}} — updates the mutable fields (not username/password). */
public record UpdateUserRequest(
        String email,
        List<Role> roles,
        boolean enabled
) {
}
