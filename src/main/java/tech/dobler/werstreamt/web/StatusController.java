package tech.dobler.werstreamt.web;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.time.Instant;

@Controller
public class StatusController
{
    private static final Instant SERVER_START_TIME = Instant.now();

    @GetMapping("public/status")
    public String status(Model model)
    {
        model.addAttribute("version", getClass().getPackage().getImplementationVersion());
        model.addAttribute("serverStart", SERVER_START_TIME);

        return "statusView";
    }
}

