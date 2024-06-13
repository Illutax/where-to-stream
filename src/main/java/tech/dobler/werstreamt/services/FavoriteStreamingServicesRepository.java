package tech.dobler.werstreamt.services;

import org.springframework.stereotype.Service;

import java.util.Set;

@Service
public class FavoriteStreamingServicesRepository {
    Set<String> favoriteServices = Set.of("Prime Video", "Google Play", "Netflix", "Disney", "YouTube");

    public Set<String> getFavoriteServices() {
        return favoriteServices;
    }
}
