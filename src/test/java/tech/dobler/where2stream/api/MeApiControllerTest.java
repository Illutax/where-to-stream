package tech.dobler.where2stream.api;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// Full context so the security filter chain wraps the request (the Authentication method arg
// resolves from request.getUserPrincipal(), which the @WebMvcTest slice does not wire up).
@SpringBootTest
@AutoConfigureMockMvc
class MeApiControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void reportsTheCurrentUserWithRolesAndAdminFlag() throws Exception {
        mockMvc.perform(get("/api/me").with(user("alice").roles("ADMIN", "USER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.authenticated").value(true))
                .andExpect(jsonPath("$.username").value("alice"))
                .andExpect(jsonPath("$.admin").value(true))
                .andExpect(jsonPath("$.roles.length()").value(2))
                .andExpect(jsonPath("$.roles[0]").value("ADMIN"))
                .andExpect(jsonPath("$.roles[1]").value("USER"))
                // Unknown user (mock principal not in the DB) falls back to the SYSTEM theme.
                .andExpect(jsonPath("$.theme").value("SYSTEM"))
                // TMDB is not enabled in the test config, so posters come from IMDb (no attribution).
                .andExpect(jsonPath("$.tmdbAttribution").value(false))
                // Unknown user falls back to the age-rating badges being on.
                .andExpect(jsonPath("$.showAgeRatings").value(true))
                // ...and to English with German titles off.
                .andExpect(jsonPath("$.language").value("EN"))
                .andExpect(jsonPath("$.showGermanTitle").value(false))
                // ...and to the grid view with 6 tiles per row.
                .andExpect(jsonPath("$.viewMode").value("GRID"))
                .andExpect(jsonPath("$.tilesPerRow").value(6));
    }

    @Test
    void reportsANonAdminUser() throws Exception {
        mockMvc.perform(get("/api/me").with(user("bob").roles("USER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("bob"))
                .andExpect(jsonPath("$.admin").value(false));
    }

    @Test
    void updatingTheThemePersistsForTheCurrentUser() throws Exception {
        // "admin" is the seeded account (see test application.properties), so it exists in the DB.
        try {
            mockMvc.perform(put("/api/me/theme").with(user("admin").roles("ADMIN")).with(csrf())
                            .contentType(MediaType.APPLICATION_JSON).content("{\"theme\":\"DARK\"}"))
                    .andExpect(status().isNoContent());

            mockMvc.perform(get("/api/me").with(user("admin").roles("ADMIN")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.theme").value("DARK"));

            mockMvc.perform(put("/api/me/theme").with(user("admin").roles("ADMIN")).with(csrf())
                            .contentType(MediaType.APPLICATION_JSON).content("{\"theme\":\"NONSENSE\"}"))
                    .andExpect(status().isBadRequest());
        } finally {
            // Reset so the shared context's seeded admin is left as-is for other tests.
            mockMvc.perform(put("/api/me/theme").with(user("admin").roles("ADMIN")).with(csrf())
                    .contentType(MediaType.APPLICATION_JSON).content("{\"theme\":\"SYSTEM\"}"));
        }
    }

    @Test
    void updatingTheAgeRatingPreferencePersistsForTheCurrentUser() throws Exception {
        try {
            mockMvc.perform(put("/api/me/show-age-ratings").with(user("admin").roles("ADMIN")).with(csrf())
                            .contentType(MediaType.APPLICATION_JSON).content("{\"showAgeRatings\":false}"))
                    .andExpect(status().isNoContent());

            mockMvc.perform(get("/api/me").with(user("admin").roles("ADMIN")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.showAgeRatings").value(false));

            mockMvc.perform(put("/api/me/show-age-ratings").with(user("admin").roles("ADMIN")).with(csrf())
                            .contentType(MediaType.APPLICATION_JSON).content("{}"))
                    .andExpect(status().isBadRequest());
        } finally {
            // Reset to the default (on) so the shared context's seeded admin is left as-is.
            mockMvc.perform(put("/api/me/show-age-ratings").with(user("admin").roles("ADMIN")).with(csrf())
                    .contentType(MediaType.APPLICATION_JSON).content("{\"showAgeRatings\":true}"));
        }
    }

    @Test
    void updatingLanguageAndGermanTitlePersistForTheCurrentUser() throws Exception {
        try {
            mockMvc.perform(put("/api/me/language").with(user("admin").roles("ADMIN")).with(csrf())
                            .contentType(MediaType.APPLICATION_JSON).content("{\"language\":\"DE\"}"))
                    .andExpect(status().isNoContent());
            mockMvc.perform(put("/api/me/show-german-title").with(user("admin").roles("ADMIN")).with(csrf())
                            .contentType(MediaType.APPLICATION_JSON).content("{\"showGermanTitle\":true}"))
                    .andExpect(status().isNoContent());

            mockMvc.perform(get("/api/me").with(user("admin").roles("ADMIN")))
                    .andExpect(jsonPath("$.language").value("DE"))
                    .andExpect(jsonPath("$.showGermanTitle").value(true));

            mockMvc.perform(put("/api/me/language").with(user("admin").roles("ADMIN")).with(csrf())
                            .contentType(MediaType.APPLICATION_JSON).content("{}"))
                    .andExpect(status().isBadRequest());
        } finally {
            mockMvc.perform(put("/api/me/language").with(user("admin").roles("ADMIN")).with(csrf())
                    .contentType(MediaType.APPLICATION_JSON).content("{\"language\":\"EN\"}"));
            mockMvc.perform(put("/api/me/show-german-title").with(user("admin").roles("ADMIN")).with(csrf())
                    .contentType(MediaType.APPLICATION_JSON).content("{\"showGermanTitle\":false}"));
        }
    }

    @Test
    void updatingViewModeAndTilesPerRowPersistForTheCurrentUser() throws Exception {
        try {
            mockMvc.perform(put("/api/me/view-mode").with(user("admin").roles("ADMIN")).with(csrf())
                            .contentType(MediaType.APPLICATION_JSON).content("{\"viewMode\":\"LIST\"}"))
                    .andExpect(status().isNoContent());
            mockMvc.perform(put("/api/me/tiles-per-row").with(user("admin").roles("ADMIN")).with(csrf())
                            .contentType(MediaType.APPLICATION_JSON).content("{\"tilesPerRow\":3}"))
                    .andExpect(status().isNoContent());

            mockMvc.perform(get("/api/me").with(user("admin").roles("ADMIN")))
                    .andExpect(jsonPath("$.viewMode").value("LIST"))
                    .andExpect(jsonPath("$.tilesPerRow").value(3));

            mockMvc.perform(put("/api/me/view-mode").with(user("admin").roles("ADMIN")).with(csrf())
                            .contentType(MediaType.APPLICATION_JSON).content("{}"))
                    .andExpect(status().isBadRequest());
            mockMvc.perform(put("/api/me/tiles-per-row").with(user("admin").roles("ADMIN")).with(csrf())
                            .contentType(MediaType.APPLICATION_JSON).content("{}"))
                    .andExpect(status().isBadRequest());
            mockMvc.perform(put("/api/me/tiles-per-row").with(user("admin").roles("ADMIN")).with(csrf())
                            .contentType(MediaType.APPLICATION_JSON).content("{\"tilesPerRow\":1}"))
                    .andExpect(status().isBadRequest());
            mockMvc.perform(put("/api/me/tiles-per-row").with(user("admin").roles("ADMIN")).with(csrf())
                            .contentType(MediaType.APPLICATION_JSON).content("{\"tilesPerRow\":7}"))
                    .andExpect(status().isBadRequest());
        } finally {
            mockMvc.perform(put("/api/me/view-mode").with(user("admin").roles("ADMIN")).with(csrf())
                    .contentType(MediaType.APPLICATION_JSON).content("{\"viewMode\":\"GRID\"}"));
            mockMvc.perform(put("/api/me/tiles-per-row").with(user("admin").roles("ADMIN")).with(csrf())
                    .contentType(MediaType.APPLICATION_JSON).content("{\"tilesPerRow\":6}"));
        }
    }

    @Test
    void rejectsABlankUsernameRename() throws Exception {
        // Blank is rejected before any DB change, so the shared admin is untouched.
        mockMvc.perform(put("/api/me/username").with(user("admin").roles("ADMIN")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"username\":\"  \"}"))
                .andExpect(status().isBadRequest());
    }
}
