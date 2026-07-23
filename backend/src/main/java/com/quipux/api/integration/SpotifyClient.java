package com.quipux.api.integration;

import com.quipux.api.exception.SpotifyServiceException;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.List;
import java.util.Map;

@Component
public class SpotifyClient {

    private final WebClient webClient;
    private final SpotifyTokenClient spotifyTokenClient;

    public SpotifyClient(WebClient.Builder webClientBuilder, SpotifyTokenClient spotifyTokenClient) {
        this.webClient = webClientBuilder
                .baseUrl("https://api.spotify.com")
                .build();
        this.spotifyTokenClient = spotifyTokenClient;
    }

    @SuppressWarnings("unchecked")
    public List<String> fetchGenres() {
        String token = spotifyTokenClient.getAccessToken();

        Map<String, Object> response = webClient.get()
                .uri("/v1/recommendations/available-genre-seeds")
                .header("Authorization", "Bearer " + token)
                .retrieve()
                .bodyToMono(Map.class)
                .block();

        if (response == null || !response.containsKey("genres")) {
            throw new SpotifyServiceException("Respuesta inesperada de la API de Spotify");
        }
        return (List<String>) response.get("genres");
    }
}
