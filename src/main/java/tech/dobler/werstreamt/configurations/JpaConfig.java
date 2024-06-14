package tech.dobler.werstreamt.configurations;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tech.dobler.werstreamt.services.ExportReader;
import tech.dobler.werstreamt.services.ImdbEntryRepository;

@Configuration
@RequiredArgsConstructor
public class JpaConfig {

    private final ExportReader exportReader;

    @Bean
    public ImdbEntryRepository imdbEntryRepository() {
        final var imdbEntryRepository = new ImdbEntryRepository();
        imdbEntryRepository.init(this.exportReader.parse());
        return imdbEntryRepository;
    }
}
