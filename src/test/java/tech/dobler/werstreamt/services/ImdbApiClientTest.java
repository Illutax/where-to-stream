package tech.dobler.werstreamt.services;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ImdbApiClientTest {

    @Test
    void search() {
        ImdbApiClient imdbApiClient = new ImdbApiClient();
        imdbApiClient.search("ls031620199");
    }
}