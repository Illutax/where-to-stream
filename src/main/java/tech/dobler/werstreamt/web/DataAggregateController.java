package tech.dobler.werstreamt.web;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import tech.dobler.werstreamt.entities.Availability;
import tech.dobler.werstreamt.entities.ImdbEntry;
import tech.dobler.werstreamt.entities.QueryResult;
import tech.dobler.werstreamt.services.ImdbEntryRepository;
import tech.dobler.werstreamt.services.AggregateService;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

@Controller
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class DataAggregateController {
    private final AggregateService service;
    private final ImdbEntryRepository imdbEntryRepository;

    @GetMapping(path = {"amazon", "prime"})
    public String getAmazon(Model model) {
        final var included = service.included("Prime Video").stream()
                .sorted(Comparator.comparing(ImdbEntry::added))
                .toList();
        final var paid = service.paid("Prime Video").stream()
                .map((it -> PaidDto.from(it, imdbEntryRepository.findByImdb(it.imdbId()).orElseThrow())))
                .sorted(Comparator.comparing(PaidDto::added))
                .toList();
        model.addAttribute("primeIncluded", included);
        model.addAttribute("primeOthers", paid);
        return "amazon";
    }

    @GetMapping(path = "disney")
    public String getDisney(Model model) {
        final var included = service.included("Disney+").stream()
                .sorted(Comparator.comparing(ImdbEntry::added))
                .toList();
        model.addAttribute("entries", included);
        return "disney";
    }

    @GetMapping(path = "netflix")
    public String getNetflix(Model model) {
        final var included = service.included("Netflix").stream()
                .sorted(Comparator.comparing(ImdbEntry::added))
                .toList();
        model.addAttribute("entries", included);
        return "netflix";
    }

    @GetMapping(path = "google")
    public String getGoogle(Model model) {
        final var included = service.paid("Google Play").stream()
                .map((it -> PaidDto.from(it, imdbEntryRepository.findByImdb(it.imdbId()).orElseThrow())))
                .sorted(Comparator.comparing(PaidDto::added))
                .toList();
        model.addAttribute("entries", included);
        return "google";
    }

    public record PaidDto(String name, String imdbId, String price, String added, boolean isRated, int year) {
        static PaidDto from(QueryResult result, ImdbEntry imdbEntry) {
            String price = prettyPrint(result.availabilities());
            return new PaidDto(imdbEntry.name(), imdbEntry.imdbId(), price, imdbEntry.added(), imdbEntry.isRated(), imdbEntry.year());
        }
    }

    public static String prettyPrint(List<Availability> availabilities) {
        return availabilities.stream()
                .map(a -> {
                    final var sb = new StringBuilder();
                    if (a.fourK() != null) {
                        sb.append("4k: ").append(a.fourK().value()).append(" ");
                    }
                    if (a.hd() != null) {
                        sb.append("HD: ").append(a.hd().value()).append(" ");
                    }
                    if (a.sd() != null) {
                        sb.append("SD: ").append(a.sd().value()).append(" ");
                    }
                    return sb.toString();
                }).collect(Collectors.joining(", "));
    }
}
