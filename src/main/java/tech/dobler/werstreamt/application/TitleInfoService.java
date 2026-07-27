package tech.dobler.werstreamt.application;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import tech.dobler.werstreamt.application.dto.MetaDto;
import tech.dobler.werstreamt.domain.ImdbId;
import tech.dobler.werstreamt.services.TitleMetaService;

import java.util.Optional;

/**
 * Per-title display metadata (age rating + German title) for a table row, from the shared IMDb
 * metadata cache ({@link TitleMetaService}) — the same one fetch per title as the poster.
 * Application-layer facade so the presentation layer stays off the services layer.
 */
@Service
@RequiredArgsConstructor
public class TitleInfoService {

    private final TitleMetaService titleMetaService;

    /** Empty only on a hard fetch failure; otherwise a DTO whose fields may individually be null. */
    public Optional<MetaDto> metaFor(ImdbId imdbId) {
        return titleMetaService.get(imdbId).map(data -> new MetaDto(data.rating(), data.germanTitle()));
    }
}
