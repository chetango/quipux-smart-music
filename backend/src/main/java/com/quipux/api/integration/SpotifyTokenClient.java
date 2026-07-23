package com.quipux.api.integration;

import com.quipux.api.exception.SpotifyServiceException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;

@Component
public class SpotifyTokenClient {

    private final WebClient webClient;
    private final String clientId;
    private final String clientSecret;

    public SpotifyTokenClient(
            WebClient.Builder webClientBuilder,
            @Value("${spotify.client-id}") String clientId,
            @Value("${spotify.client-secret}") String clientSecret) {
        this.webClient = webClientBuilder
                .baseUrl("https://accounts.spotify.com")
                .build();
        this.clientId = clientId;
        this.clientSecret = clientSecret;
    }

    @Cacheable("spotifyToken")
    public String getAccessToken() {
        String credentials = clientId + ":" + clientSecret;
        String encoded = Base64.getEncoder()
                .encodeToString(credentials.getBytes(StandardCharsets.UTF_8));

        MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
        body.add("grant_type", "client_credentials");

        @SuppressWarnings("unchecked")
        Map<String, Object> response = webClient.post()
                .uri("/api/token")
                .header("Authorization", "Basic " + encoded)
                .header("Content-Type", "application/x-www-form-urlencoded")
                .body(BodyInserters.fromFormData(body))
                .retrieve()
                .bodyToMono(Map.class)
                .block();

        if (response == null || !response.containsKey("access_token")) {
            throw new SpotifyServiceException("No se pudo obtener el token de Spotify");
        }
        return (String) response.get("access_token");
    }
}
