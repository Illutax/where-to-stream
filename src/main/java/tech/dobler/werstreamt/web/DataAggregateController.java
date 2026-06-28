package tech.dobler.werstreamt.web;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import tech.dobler.werstreamt.domain.AvailabilityType;
import tech.dobler.werstreamt.domain.Availability;
import tech.dobler.werstreamt.domain.ImdbEntry;
import tech.dobler.werstreamt.domain.QueryResult;
import tech.dobler.werstreamt.services.AggregateService;
import tech.dobler.werstreamt.services.CommonAttributeService;
import tech.dobler.werstreamt.services.ImdbCatalog;
import tech.dobler.werstreamt.services.StreamInfoService;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Controller
@RequiredArgsConstructor
public class DataAggregateController {
    private final AggregateService service;
    private final ImdbCatalog imdbCatalog;
    private final CommonAttributeService commonAttributeService;
    private final StreamInfoService streamInfoService;

    public record IndexDto(boolean isRated, String name, String imdbId, int year, String added, Optional<String> availableStreamingServices) {}

    @GetMapping(path = {"", "/"})
    public String index(Model model) {
        final var entries = imdbCatalog.findAll();
        // Resolve all entries in a single batch instead of one query per entry (avoids N+1).
        final var resolved = streamInfoService.resolveAll(entries.stream().map(ImdbEntry::imdbId).toList());
        final var included = entries.stream()
                .map(entry -> new IndexDto(entry.isRated(),
                        entry.name(),
                        entry.imdbId(),
                        entry.year(),
                        entry.added(),
                        StreamInfoService.toAvailableServiceNames(resolved.getOrDefault(entry.imdbId(), List.of()))))
                .sorted(Comparator.comparing(IndexDto::name))
                .toList();
        model.addAttribute("entries", included);
        commonAttributeService.add(model);
        return "index";
    }

    @GetMapping(path = {"amazon", "prime"})
    public String getAmazon(Model model) {
        final var content = service.contentFor("Prime Video");
        model.addAttribute("primeIncluded", sortedByAdded(content.included()));
        model.addAttribute("primeOthers", paidDtos(content.paid()));
        commonAttributeService.add(model);
        return "amazon";
    }

    @GetMapping(path = "disney")
    public String getDisney(Model model) {
        return flatratePage("Disney+", "disney", model);
    }

    @GetMapping(path = "netflix")
    public String getNetflix(Model model) {
        return flatratePage("Netflix", "netflix", model);
    }

    @GetMapping(path = "wow")
    public String getWow(Model model) {
        return flatratePage("WOW", "wow", model);
    }

    @GetMapping(path = "google")
    public String getGoogle(Model model) {
        model.addAttribute("entries", paidDtos(service.paid("Google Play")));
        commonAttributeService.add(model);
        return "google";
    }

    /** Renders a single-service "flatrate / included" page (Disney+, Netflix, WOW, …). */
    private String flatratePage(String serviceName, String view, Model model) {
        model.addAttribute("entries", sortedByAdded(service.included(serviceName)));
        commonAttributeService.add(model);
        return view;
    }

    private static List<ImdbEntry> sortedByAdded(List<ImdbEntry> entries) {
        return entries.stream().sorted(Comparator.comparing(ImdbEntry::added)).toList();
    }

    private List<PaidDto> paidDtos(List<QueryResult> paid) {
        return paid.stream()
                .map(it -> PaidDto.from(it, imdbCatalog.findByImdb(it.imdbId()).orElseThrow()))
                .sorted(Comparator.comparing(PaidDto::added))
                .toList();
    }

    public record PaidDto(String name, String imdbId, String price, String added, boolean isRated, String year) {
        static PaidDto from(QueryResult result, ImdbEntry imdbEntry) {
            String price = prettyPrint(result.availabilities());
            String year = imdbEntry.year() == 0
                    ? "Not yet released"
                    : String.valueOf(imdbEntry.year());
            return new PaidDto(imdbEntry.name(), imdbEntry.imdbId(), price, imdbEntry.added(), imdbEntry.isRated(), year);
        }
    }

    public static String prettyPrint(List<Availability> availabilities) {
        return availabilities.stream()
                .map(a -> {
                    final var sb = new StringBuilder();
                    if (a.type() == AvailabilityType.RENT) {
                        sb.append("leihen: ");
                    } else {
                        sb.append("kaufen: ");
                    }

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
