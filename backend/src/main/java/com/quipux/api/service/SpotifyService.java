package com.quipux.api.service;

import com.quipux.api.integration.SpotifyClient;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class SpotifyService {

    private final SpotifyClient spotifyClient;

    public SpotifyService(SpotifyClient spotifyClient) {
        this.spotifyClient = spotifyClient;
    }

    public List<String> getAvailableGenres() {
        return spotifyClient.fetchGenres();
    }
}
