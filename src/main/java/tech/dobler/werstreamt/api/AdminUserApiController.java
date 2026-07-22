package tech.dobler.werstreamt.api;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import tech.dobler.werstreamt.application.UserAdminService;
import tech.dobler.werstreamt.application.dto.CreateUserRequest;
import tech.dobler.werstreamt.application.dto.ResetPasswordRequest;
import tech.dobler.werstreamt.application.dto.UpdateUserRequest;
import tech.dobler.werstreamt.application.dto.UserDto;

import java.util.List;
import java.util.UUID;

/** ADMIN-only user administration API (also guarded at the URL and method-security level). */
@RestController
@RequestMapping("/api/admin/users")
@RequiredArgsConstructor
public class AdminUserApiController {

    private final UserAdminService userAdminService;

    @GetMapping
    public List<UserDto> list() {
        return userAdminService.list();
    }

    @GetMapping("/{id}")
    public UserDto get(@PathVariable UUID id) {
        return userAdminService.get(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public UserDto create(@RequestBody CreateUserRequest request) {
        return userAdminService.create(request);
    }

    @PutMapping("/{id}")
    public UserDto update(@PathVariable UUID id, @RequestBody UpdateUserRequest request) {
        return userAdminService.update(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID id) {
        userAdminService.delete(id);
    }

    @PostMapping("/{id}/password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void resetPassword(@PathVariable UUID id, @RequestBody ResetPasswordRequest request) {
        userAdminService.resetPassword(id, request.newPassword());
    }
}
