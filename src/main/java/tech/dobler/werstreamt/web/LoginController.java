package tech.dobler.werstreamt.web;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

/** Serves the custom login page and tells the template whether OIDC login is available. */
@Controller
@RequiredArgsConstructor
public class LoginController {

    private final ObjectProvider<ClientRegistrationRepository> clientRegistrations;

    @GetMapping("/login")
    public String login(Model model) {
        model.addAttribute("googleEnabled", clientRegistrations.getIfAvailable() != null);
        return "login";
    }
}
