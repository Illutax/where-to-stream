package tech.dobler.where2stream.application;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import tech.dobler.where2stream.application.dto.FlatrateEntryDto;
import tech.dobler.where2stream.application.dto.PaidEntryDto;
import tech.dobler.where2stream.application.dto.ProviderPageDto;
import tech.dobler.where2stream.domain.ImdbEntry;
import tech.dobler.where2stream.domain.QueryResult;
import tech.dobler.where2stream.services.AggregateService;
import tech.dobler.where2stream.services.WatchlistCatalog;

import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * Builds the per-provider page (flatrate "included" and/or paid "kaufbar" titles) uniformly for
 * all five providers, scoped to a user's watchlist. Delegates the catalogue resolution to
 * {@link AggregateService} (which batches the lookups); this service only shapes the DTOs.
 */
@Service
@RequiredArgsConstructor
public class ProviderPageService {

    private final AggregateService aggregateService;
    private final WatchlistCatalog watchlistCatalog;

    public ProviderPageDto pageFor(StreamingProvider provider, UUID userId) {
        if (provider == StreamingProvider.AMAZON) {
            // Amazon needs both lists; contentFor resolves the catalogue once for both.
            final var content = aggregateService.contentFor(provider.serviceName(), userId);
            return new ProviderPageDto(provider.key(),
                    includedDtos(content.included()),
                    paidDtos(content.paid(), userId));
        }

        final var included = provider.hasFlatrate()
                ? includedDtos(aggregateService.included(provider.serviceName(), userId))
                : List.<FlatrateEntryDto>of();
        final var paid = provider.hasPaid()
                ? paidDtos(aggregateService.paid(provider.serviceName(), userId), userId)
                : List.<PaidEntryDto>of();
        return new ProviderPageDto(provider.key(), included, paid);
    }

    private static List<FlatrateEntryDto> includedDtos(List<ImdbEntry> entries) {
        return entries.stream()
                .sorted(Comparator.comparing(ImdbEntry::added))
                .map(FlatrateEntryDto::from)
                .toList();
    }

    private List<PaidEntryDto> paidDtos(List<QueryResult> paid, UUID userId) {
        return paid.stream()
                .map(it -> PaidEntryDto.from(it, watchlistCatalog.findByImdb(userId, it.imdbId()).orElseThrow()))
                .sorted(Comparator.comparing(PaidEntryDto::added))
                .toList();
    }
}
