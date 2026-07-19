package tech.dobler.werstreamt.web;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Serves the Angular single-page app. The built bundle lives on the classpath under
 * {@code static/app/} (copied there by the Maven build), so Spring Boot serves its assets
 * ({@code /app/*.js}, {@code /app/*.css}, …) automatically.
 *
 * <p>The app uses hash-based routing, so the server only ever needs to hand out the shell at
 * {@code /app/}: no catch-all fallback is required and there is no collision with the existing
 * Thymeleaf routes. {@code /app} (no trailing slash) is redirected so the {@code <base href>}
 * resolves correctly.
 */
@Controller
public class SpaController {

    @GetMapping("/app")
    public String redirectToApp() {
        return "redirect:/app/";
    }

    @GetMapping("/app/")
    public String app() {
        return "forward:/app/index.html";
    }
}
