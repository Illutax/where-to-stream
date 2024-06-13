package tech.dobler.werstreamt;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import tech.dobler.werstreamt.services.ExportReader;
import tech.dobler.werstreamt.services.ImdbEntryRepository;

@SpringBootApplication
public class WerStreamtApplication {

    public static void main(String[] args) {
        SpringApplication.run(WerStreamtApplication.class, args);
    }

    @Bean
    public ImdbEntryRepository imdbEntryRepository(ExportReader exportReader) {
        final var imdbEntryRepository = new ImdbEntryRepository();
        imdbEntryRepository.init(exportReader.parse());
        return imdbEntryRepository;
    }
}
