package tech.dobler.where2stream.configurations;

import org.springframework.core.convert.converter.Converter;
import org.springframework.stereotype.Component;
import tech.dobler.where2stream.domain.ImdbId;

/**
 * Binds a request parameter / path variable string to an {@link ImdbId} (e.g.
 * {@code GET /api/search?imdbId=tt…}). A malformed value fails conversion, which Spring MVC turns
 * into a 400 — the validation lives in {@code ImdbId}. Spring Boot auto-registers {@code Converter}
 * beans into the MVC conversion service.
 */
@Component
public class StringToImdbIdConverter implements Converter<String, ImdbId> {

    @Override
    public ImdbId convert(String source) {
        return ImdbId.of(source);
    }
}
