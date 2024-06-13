package tech.dobler.werstreamt;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Bean;
import org.springframework.context.event.EventListener;
import tech.dobler.werstreamt.entities.ImdbEntry;
import tech.dobler.werstreamt.services.ApiClient;
import tech.dobler.werstreamt.services.ExportReader;
import tech.dobler.werstreamt.services.FavoriteStreamingServicesRepository;
import tech.dobler.werstreamt.services.ImdbEntryRepository;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
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
