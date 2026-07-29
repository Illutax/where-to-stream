package tech.dobler.where2stream.shared.web;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Serves the Angular single-page app, which is the only UI now
 * (the server-rendered Thymeleaf pages were removed).
 * The built bundle lives on the classpath under {@code static/app/} (copied there by the Maven build),
 * so Spring Boot serves its assets ({@code /app/*.js}, {@code /app/*.css}, …) automatically.
 *
 * <p>The app uses hash-based routing, so the server only ever needs to hand out the shell at
 * {@code /app/}: no catch-all fallback is required.
 * {@code /app} (no trailing slash) and the root {@code /} both redirect to it so the
 * {@code <base href>} resolves correctly and visitors landing on the root reach the app.
 */
@Controller
public class SpaController {

    @GetMapping("/")
    public String redirectRootToApp() {
        return "redirect:/app/";
    }

    @GetMapping("/app")
    public String redirectToApp() {
        return "redirect:/app/";
    }

    @GetMapping("/app/")
    public String app() {
        return "forward:/app/index.html";
    }
}
