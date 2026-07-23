package com.quipux.api.integration;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.quipux.api.dto.response.RecommendedSongResponse;
import com.quipux.api.entity.PlayList;
import com.quipux.api.entity.Song;
import com.quipux.api.exception.AiServiceException;
import com.quipux.api.service.AiRecommendationService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class GeminiAiRecommendationService implements AiRecommendationService {

    private static final int MAX_SONGS_IN_CONTEXT = 20;
    private static final Duration TIMEOUT = Duration.ofSeconds(10);
    private static final String GEMINI_API_URL =
            "https://generativelanguage.googleapis.com";

    private final WebClient webClient;
    private final ObjectMapper objectMapper;
    private final String apiKey;
    private final String promptTemplate;

    public GeminiAiRecommendationService(
            WebClient.Builder webClientBuilder,
            ObjectMapper objectMapper,
            @Value("${gemini.api-key}") String apiKey) throws IOException {
        this.webClient = webClientBuilder.baseUrl(GEMINI_API_URL).build();
        this.objectMapper = objectMapper;
        this.apiKey = apiKey;
        this.promptTemplate = loadPromptTemplate();
    }

    @Override
    public List<RecommendedSongResponse> recommend(PlayList playList) {
        String prompt = buildPrompt(playList);
        String rawResponse = callGemini(prompt);
        return parseResponse(rawResponse);
    }

    private String loadPromptTemplate() throws IOException {
        ClassPathResource resource = new ClassPathResource("prompts/recommendations.txt");
        return resource.getContentAsString(StandardCharsets.UTF_8);
    }

    private String buildPrompt(PlayList playList) {
        List<Song> songs = playList.getSongs();
        int limit = Math.min(songs.size(), MAX_SONGS_IN_CONTEXT);

        String songsList = songs.subList(0, limit).stream()
                .map(s -> String.format("- %s — %s (%s)", s.getTitulo(), s.getArtista(), s.getGenero()))
                .collect(Collectors.joining("\n"));

        if (songsList.isBlank()) {
            songsList = "(La lista está vacía, recomienda canciones populares de diferentes géneros)";
        }

        return promptTemplate.replace("{{SONGS_LIST}}", songsList);
    }

    private String callGemini(String prompt) {
        Map<String, Object> requestBody = Map.of(
                "contents", List.of(
                        Map.of("parts", List.of(
                                Map.of("text", prompt)
                        ))
                )
        );

        try {
            String responseBody = webClient.post()
                    .uri(uriBuilder -> uriBuilder
                            .path("/v1beta/models/gemini-2.0-flash:generateContent")
                            .queryParam("key", apiKey)
                            .build())
                    .bodyValue(requestBody)
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(TIMEOUT)
                    .block();

            if (responseBody == null || responseBody.isBlank()) {
                throw new AiServiceException(
                        "El servicio de recomendaciones no está disponible en este momento. Inténtelo más tarde.");
            }
            return responseBody;

        } catch (AiServiceException e) {
            throw e;
        } catch (WebClientResponseException e) {
            throw new AiServiceException(
                    "El servicio de recomendaciones no está disponible en este momento. Inténtelo más tarde.", e);
        } catch (Exception e) {
            throw new AiServiceException(
                    "El servicio de recomendaciones no está disponible en este momento. Inténtelo más tarde.", e);
        }
    }

    private List<RecommendedSongResponse> parseResponse(String responseBody) {
        try {
            JsonNode root = objectMapper.readTree(responseBody);
            String text = root
                    .path("candidates").get(0)
                    .path("content")
                    .path("parts").get(0)
                    .path("text")
                    .asText();

            // Extracción defensiva: buscar primer '[' y último ']'
            int start = text.indexOf('[');
            int end = text.lastIndexOf(']');
            if (start == -1 || end == -1 || end <= start) {
                throw new AiServiceException(
                        "El servicio de recomendaciones no está disponible en este momento. Inténtelo más tarde.");
            }

            String jsonArray = text.substring(start, end + 1);
            return objectMapper.readValue(jsonArray, new TypeReference<List<RecommendedSongResponse>>() {});

        } catch (AiServiceException e) {
            throw e;
        } catch (Exception e) {
            throw new AiServiceException(
                    "El servicio de recomendaciones no está disponible en este momento. Inténtelo más tarde.", e);
        }
    }
}
