package tech.dobler.werstreamt.configurations;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tech.dobler.werstreamt.entities.ImdbEntry;
import tech.dobler.werstreamt.services.ExportReader;
import tech.dobler.werstreamt.services.FileUtils;
import tech.dobler.werstreamt.services.ImdbEntryRepository;

import java.util.List;

@Configuration
@RequiredArgsConstructor
public class JpaConfig {

    private final ExportReader exportReader;

    @Bean
    public FileUtils fileUtils()
    {
        return new FileUtils();
    }

    @Bean
    public ImdbEntryRepository imdbEntryRepository() {
        final var imdbEntryRepository = new ImdbEntryRepository();
        String list = fileUtils().availableLists().getLast();
        List<ImdbEntry> entries = this.exportReader.parse(list);
        imdbEntryRepository.init(entries, list);
        return imdbEntryRepository;
    }
}
