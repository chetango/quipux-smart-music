package com.quipux.api.controller;

import com.quipux.api.dto.response.RecommendedSongResponse;
import com.quipux.api.exception.AiServiceException;
import com.quipux.api.exception.ResourceNotFoundException;
import com.quipux.api.security.JwtService;
import com.quipux.api.service.RecommendationService;
import com.quipux.api.service.UserDetailsServiceImpl;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.hamcrest.Matchers.hasSize;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Tests del endpoint GET /lists/{listName}/recommendations.
 * Usa @WebMvcTest para cargar solo la capa web sin base de datos real.
 * El proveedor de IA está completamente mockeado: ningún test llama a Gemini.
 */
@WebMvcTest(RecommendationController.class)
class RecommendationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    // Dependencia del controlador
    @MockBean
    private RecommendationService recommendationService;

    // Beans requeridos por SecurityConfig para construir JwtAuthFilter
    @MockBean
    private JwtService jwtService;
    @MockBean
    private UserDetailsServiceImpl userDetailsService;

    // -------------------------------------------------------------------------
    // Escenario 1: proveedor de IA responde correctamente → 200
    // -------------------------------------------------------------------------

    @Test
    @WithMockUser
    void getRecommendations_returns200_whenAiRespondsSuccessfully() throws Exception {
        List<RecommendedSongResponse> recommendations = List.of(
                new RecommendedSongResponse("Stairway to Heaven", "Led Zeppelin", "rock"),
                new RecommendedSongResponse("Hotel California", "Eagles", "rock"),
                new RecommendedSongResponse("Bohemian Rhapsody", "Queen", "rock"),
                new RecommendedSongResponse("Back in Black", "AC/DC", "hard-rock"),
                new RecommendedSongResponse("Smells Like Teen Spirit", "Nirvana", "grunge")
        );
        when(recommendationService.getRecommendations("rock-clasico"))
                .thenReturn(recommendations);

        mockMvc.perform(get("/lists/rock-clasico/recommendations"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(5)))
                .andExpect(jsonPath("$[0].titulo").value("Stairway to Heaven"))
                .andExpect(jsonPath("$[0].artista").value("Led Zeppelin"))
                .andExpect(jsonPath("$[0].genero").value("rock"))
                .andExpect(jsonPath("$[4].titulo").value("Smells Like Teen Spirit"));
    }

    // -------------------------------------------------------------------------
    // Escenario 2: lista no encontrada → 404
    // -------------------------------------------------------------------------

    @Test
    @WithMockUser
    void getRecommendations_returns404_whenListDoesNotExist() throws Exception {
        when(recommendationService.getRecommendations("lista-inexistente"))
                .thenThrow(new ResourceNotFoundException("Lista no encontrada: lista-inexistente"));

        mockMvc.perform(get("/lists/lista-inexistente/recommendations"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("Lista no encontrada: lista-inexistente"));
    }

    // -------------------------------------------------------------------------
    // Escenario 3: proveedor de IA lanza AiServiceException → 503 AI_UNAVAILABLE
    // -------------------------------------------------------------------------

    @Test
    @WithMockUser
    void getRecommendations_returns503_whenAiThrowsException() throws Exception {
        when(recommendationService.getRecommendations("rock-clasico"))
                .thenThrow(new AiServiceException(
                        "El servicio de recomendaciones no está disponible en este momento. Inténtelo más tarde."));

        mockMvc.perform(get("/lists/rock-clasico/recommendations"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.error").value("AI_UNAVAILABLE"))
                .andExpect(jsonPath("$.message").exists());
    }

    // -------------------------------------------------------------------------
    // Escenario 4: timeout del proveedor → AiServiceException con causa de timeout → 503
    // (GeminiAiRecommendationService envuelve el TimeoutException en AiServiceException)
    // -------------------------------------------------------------------------

    @Test
    @WithMockUser
    void getRecommendations_returns503_whenAiTimesOut() throws Exception {
        Throwable timeoutCause = new java.util.concurrent.TimeoutException("Read timeout after 10000ms");
        when(recommendationService.getRecommendations("rock-clasico"))
                .thenThrow(new AiServiceException(
                        "El servicio de recomendaciones no está disponible en este momento. Inténtelo más tarde.",
                        timeoutCause));

        mockMvc.perform(get("/lists/rock-clasico/recommendations"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.error").value("AI_UNAVAILABLE"));
    }

    // -------------------------------------------------------------------------
    // Escenario 5: petición sin token JWT → 401 Unauthorized
    // -------------------------------------------------------------------------

    @Test
    void getRecommendations_returns401_whenNoToken() throws Exception {
        mockMvc.perform(get("/lists/rock-clasico/recommendations"))
                .andExpect(status().isUnauthorized());
    }

    // -------------------------------------------------------------------------
    // Escenario 6: estructura del fallback — error field = "AI_UNAVAILABLE"
    // Verifica que la respuesta 503 contiene los campos error y message
    // (GlobalExceptionHandler debe producir ErrorResponse, no un 500 genérico)
    // -------------------------------------------------------------------------

    @Test
    @WithMockUser
    void getRecommendations_fallbackResponse_containsErrorAndMessageFields() throws Exception {
        when(recommendationService.getRecommendations("cualquier-lista"))
                .thenThrow(new AiServiceException("El servicio de recomendaciones no está disponible."));

        mockMvc.perform(get("/lists/cualquier-lista/recommendations"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.error").value("AI_UNAVAILABLE"))
                .andExpect(jsonPath("$.message").isNotEmpty());
    }

    // -------------------------------------------------------------------------
    // Escenario 7: fallo de IA no produce 500 (verificación de aislamiento)
    // Un error del proveedor no debe colapsar la aplicación ni producir un error
    // genérico 500 — siempre devuelve 503 con la estructura controlada.
    // -------------------------------------------------------------------------

    @Test
    @WithMockUser
    void getRecommendations_aiFallure_doesNotProduce500() throws Exception {
        when(recommendationService.getRecommendations("mi-lista"))
                .thenThrow(new AiServiceException("Proveedor no disponible"));

        mockMvc.perform(get("/lists/mi-lista/recommendations"))
                .andExpect(status().isServiceUnavailable())  // 503, nunca 500
                .andExpect(jsonPath("$.error").value("AI_UNAVAILABLE"));
    }
}
