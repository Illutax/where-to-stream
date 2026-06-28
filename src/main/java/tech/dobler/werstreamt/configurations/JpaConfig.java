package tech.dobler.werstreamt.configurations;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tech.dobler.werstreamt.domain.ImdbEntry;
import tech.dobler.werstreamt.services.ExportReader;
import tech.dobler.werstreamt.services.FileUtils;
import tech.dobler.werstreamt.services.ImdbCatalog;

import java.util.List;

@Configuration
@RequiredArgsConstructor
public class JpaConfig {

    private final ExportReader exportReader;
    private final FileUtils fileUtils;

    @Bean
    public ImdbCatalog imdbCatalog() {
        final var imdbCatalog = new ImdbCatalog();
        String list = fileUtils.availableLists().getLast();
        List<ImdbEntry> entries = this.exportReader.parse(list);
        imdbCatalog.init(entries, list);
        return imdbCatalog;
    }
}
