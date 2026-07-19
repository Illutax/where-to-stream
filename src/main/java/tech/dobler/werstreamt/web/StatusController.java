package tech.dobler.werstreamt.web;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import tech.dobler.werstreamt.application.StatusService;

@Controller
@RequiredArgsConstructor
public class StatusController
{
    private final StatusService statusService;

    @GetMapping("/public/status")
    public String status(Model model)
    {
        final var status = statusService.status();
        model.addAttribute("version", status.version());
        model.addAttribute("serverStart", status.serverStart());

        return "statusView";
    }
}
