package tech.dobler.where2stream.accountaccess.adapter.in.api;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import tech.dobler.where2stream.accountaccess.application.UserPreferencesService;
import tech.dobler.where2stream.accountaccess.application.command.UsernameUpdateCommand;
import tech.dobler.where2stream.accountaccess.port.spi.PosterAttributionProvider;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * The username-rename side effects, unit-tested at the controller seam: the command is passed on,
 * the current session is ended, and the security context is cleared so the old principal cannot
 * keep acting. Done with mocks rather than MockMvc because session-fixation protection swaps the
 * session instance under an end-to-end request, which makes the invalidation unobservable there.
 */
@ExtendWith(MockitoExtension.class)
class UpdateUsernameTest {

    @Mock
    private UserPreferencesService userPreferencesService;
    @Mock
    private PosterAttributionProvider posterAttributionProvider;
    @Mock
    private HttpServletRequest httpRequest;
    @Mock
    private HttpSession session;

    private final Authentication auth =
            new UsernamePasswordAuthenticationToken("old-name", "x", List.of());

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    private MeApiController controller() {
        return new MeApiController(userPreferencesService, posterAttributionProvider);
    }

    @Test
    void renamePassesTheCommandInvalidatesTheSessionAndClearsTheContext() {
        SecurityContextHolder.getContext().setAuthentication(auth);
        when(httpRequest.getSession(false)).thenReturn(session);

        controller().updateUsername(auth, new UsernameUpdateRequest("new-name"), httpRequest);

        verify(userPreferencesService)
                .updateUsername(new UsernameUpdateCommand("old-name", "new-name"));
        verify(session).invalidate();
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void renameWithNoActiveSessionStillClearsTheContextWithoutFailing() {
        SecurityContextHolder.getContext().setAuthentication(auth);
        when(httpRequest.getSession(false)).thenReturn(null);

        controller().updateUsername(auth, new UsernameUpdateRequest("new-name"), httpRequest);

        verifyNoInteractions(session);
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }
}
