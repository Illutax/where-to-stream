package tech.dobler.werstreamt.web;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ui.Model;
import tech.dobler.werstreamt.application.WatchlistImportService;

import java.util.UUID;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CommonAttributeServiceTest {

    private static final UUID USER = UUID.fromString("00000000-0000-0000-0000-000000000001");

    @Mock
    private WatchlistImportService watchlistImportService;
    @Mock
    private Model model;

    @Test
    void addsTheWatchlistCountFromTheApplicationLayer() {
        when(watchlistImportService.count(USER)).thenReturn(42L);

        new CommonAttributeService(watchlistImportService).add(model, USER);

        verify(model).addAttribute("watchlistCount", 42L);
    }
}
