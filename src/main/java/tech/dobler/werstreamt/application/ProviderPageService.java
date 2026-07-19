package tech.dobler.werstreamt.application;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import tech.dobler.werstreamt.application.dto.FlatrateEntryDto;
import tech.dobler.werstreamt.application.dto.PaidEntryDto;
import tech.dobler.werstreamt.application.dto.ProviderPageDto;
import tech.dobler.werstreamt.domain.ImdbEntry;
import tech.dobler.werstreamt.domain.QueryResult;
import tech.dobler.werstreamt.services.AggregateService;
import tech.dobler.werstreamt.services.ImdbCatalog;

import java.util.Comparator;
import java.util.List;

/**
 * Builds the per-provider page (flatrate "included" and/or paid "kaufbar" titles) uniformly
 * for all five providers. Delegates the catalogue resolution to {@link AggregateService},
 * which batches the lookups (see {@code contentFor}/{@code getAll}); this service only shapes
 * the results into view-agnostic DTOs.
 */
@Service
@RequiredArgsConstructor
public class ProviderPageService {

    private final AggregateService aggregateService;
    private final ImdbCatalog imdbCatalog;

    public ProviderPageDto pageFor(StreamingProvider provider) {
        if (provider == StreamingProvider.AMAZON) {
            // Amazon needs both lists; contentFor resolves the catalogue once for both.
            final var content = aggregateService.contentFor(provider.serviceName());
            return new ProviderPageDto(provider.key(),
                    includedDtos(content.included()),
                    paidDtos(content.paid()));
        }

        final var included = provider.hasFlatrate()
                ? includedDtos(aggregateService.included(provider.serviceName()))
                : List.<FlatrateEntryDto>of();
        final var paid = provider.hasPaid()
                ? paidDtos(aggregateService.paid(provider.serviceName()))
                : List.<PaidEntryDto>of();
        return new ProviderPageDto(provider.key(), included, paid);
    }

    private static List<FlatrateEntryDto> includedDtos(List<ImdbEntry> entries) {
        return entries.stream()
                .sorted(Comparator.comparing(ImdbEntry::added))
                .map(FlatrateEntryDto::from)
                .toList();
    }

    private List<PaidEntryDto> paidDtos(List<QueryResult> paid) {
        return paid.stream()
                .map(it -> PaidEntryDto.from(it, imdbCatalog.findByImdb(it.imdbId()).orElseThrow()))
                .sorted(Comparator.comparing(PaidEntryDto::added))
                .toList();
    }
}
