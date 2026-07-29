package tech.dobler.where2stream.application;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tech.dobler.where2stream.persistence.AppUser;
import tech.dobler.where2stream.persistence.AppUserRepository;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CurrentUserServiceTest {

    @Mock
    private AppUserRepository users;
    @InjectMocks
    private CurrentUserService service;

    @Test
    void resolvesTheIdOfAKnownUsername() {
        final var id = UUID.fromString("00000000-0000-0000-0000-000000000009");
        final var user = mock(AppUser.class);
        when(user.getId()).thenReturn(id);
        when(users.findByUsername("alice")).thenReturn(Optional.of(user));

        assertThat(service.resolveId("alice")).isEqualTo(id);
    }

    @Test
    void throwsWhenTheAuthenticatedUserIsNotInTheDatabase() {
        when(users.findByUsername("ghost")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.resolveId("ghost"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("ghost");
    }
}
