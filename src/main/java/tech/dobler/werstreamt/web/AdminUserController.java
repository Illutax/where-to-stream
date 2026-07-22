package tech.dobler.werstreamt.web;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import tech.dobler.werstreamt.application.UserAdminService;
import tech.dobler.werstreamt.application.UserManagementException;
import tech.dobler.werstreamt.application.dto.CreateUserRequest;
import tech.dobler.werstreamt.application.dto.UpdateUserRequest;
import tech.dobler.werstreamt.domain.Role;

import java.util.List;
import java.util.UUID;

/** Server-rendered user administration (ADMIN-only, enforced by SecurityConfig on /admin/**). */
@Controller
@RequestMapping("/admin/users")
@RequiredArgsConstructor
public class AdminUserController {

    private final UserAdminService userAdminService;
    private final CommonAttributeService commonAttributeService;

    @GetMapping
    public String list(Model model) {
        model.addAttribute("users", userAdminService.list());
        model.addAttribute("allRoles", Role.values());
        commonAttributeService.add(model);
        return "admin/users";
    }

    @PostMapping
    public String create(@RequestParam String username,
                         @RequestParam String password,
                         @RequestParam(required = false) String email,
                         @RequestParam(name = "roles", required = false) List<Role> roles,
                         RedirectAttributes attributes) {
        try {
            userAdminService.create(new CreateUserRequest(username, password, email, roles));
            attributes.addFlashAttribute("message", "Created user " + username);
        } catch (UserManagementException e) {
            attributes.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/admin/users";
    }

    @PostMapping("/{id}/update")
    public String update(@PathVariable UUID id,
                         @RequestParam(required = false) String email,
                         @RequestParam(name = "roles", required = false) List<Role> roles,
                         @RequestParam(name = "enabled", defaultValue = "false") boolean enabled,
                         RedirectAttributes attributes) {
        try {
            userAdminService.update(id, new UpdateUserRequest(email, roles, enabled));
            attributes.addFlashAttribute("message", "Updated user");
        } catch (UserManagementException e) {
            attributes.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/admin/users";
    }

    @PostMapping("/{id}/delete")
    public String delete(@PathVariable UUID id, RedirectAttributes attributes) {
        try {
            userAdminService.delete(id);
            attributes.addFlashAttribute("message", "Deleted user");
        } catch (UserManagementException e) {
            attributes.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/admin/users";
    }

    @PostMapping("/{id}/password")
    public String resetPassword(@PathVariable UUID id,
                                @RequestParam String newPassword,
                                RedirectAttributes attributes) {
        try {
            userAdminService.resetPassword(id, newPassword);
            attributes.addFlashAttribute("message", "Password reset");
        } catch (UserManagementException e) {
            attributes.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/admin/users";
    }
}
