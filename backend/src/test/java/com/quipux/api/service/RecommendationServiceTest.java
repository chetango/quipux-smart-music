package com.quipux.api.service;

import com.quipux.api.dto.response.RecommendedSongResponse;
import com.quipux.api.entity.PlayList;
import com.quipux.api.entity.Song;
import com.quipux.api.exception.AiServiceException;
import com.quipux.api.exception.ResourceNotFoundException;
import com.quipux.api.repository.PlayListRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests unitarios de RecommendationService.
 * El proveedor de IA (AiRecommendationService) está completamente mockeado.
 * Nota: @Cacheable no actúa en tests unitarios puros (sin contexto Spring).
 * La lógica de caché se valida a nivel de integración.
 */
@ExtendWith(MockitoExtension.class)
class RecommendationServiceTest {

    @Mock
    private PlayListRepository playListRepository;

    @Mock
    private AiRecommendationService aiRecommendationService;

    @InjectMocks
    private RecommendationService recommendationService;

    private PlayList playList;

    @BeforeEach
    void setUp() {
        playList = new PlayList();
        playList.setListName("rock-clasico");
        playList.setDescription("Lo mejor del rock clásico");

        Song song = new Song();
        song.setTitulo("Stairway to Heaven");
        song.setArtista("Led Zeppelin");
        song.setAlbum("Led Zeppelin IV");
        song.setAnno("1971");
        song.setGenero("rock");
        song.setPlayList(playList);

        playList.setSongs(new ArrayList<>(List.of(song)));
    }

    // -------------------------------------------------------------------------
    // Escenario 1: lista existe y IA responde → devuelve recomendaciones
    // -------------------------------------------------------------------------

    @Test
    void getRecommendations_returnsRecommendations_whenListExistsAndAiSucceeds() {
        List<RecommendedSongResponse> expected = List.of(
                new RecommendedSongResponse("Hotel California", "Eagles", "rock"),
                new RecommendedSongResponse("Back in Black", "AC/DC", "hard-rock")
        );
        when(playListRepository.findByListName("rock-clasico"))
                .thenReturn(Optional.of(playList));
        when(aiRecommendationService.recommend(playList))
                .thenReturn(expected);

        List<RecommendedSongResponse> result = recommendationService.getRecommendations("rock-clasico");

        assertThat(result).hasSize(2);
        assertThat(result.get(0).titulo()).isEqualTo("Hotel California");
        assertThat(result.get(0).artista()).isEqualTo("Eagles");
        assertThat(result.get(0).genero()).isEqualTo("rock");
        verify(aiRecommendationService).recommend(playList);
    }

    // -------------------------------------------------------------------------
    // Escenario 2: lista no encontrada → ResourceNotFoundException
    // -------------------------------------------------------------------------

    @Test
    void getRecommendations_throwsResourceNotFoundException_whenListNotFound() {
        when(playListRepository.findByListName("no-existe"))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> recommendationService.getRecommendations("no-existe"))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("no-existe");

        // Si la lista no existe, no se debe llamar al proveedor de IA
        verify(aiRecommendationService, never()).recommend(any());
    }

    // -------------------------------------------------------------------------
    // Escenario 3: lista existe pero la IA lanza AiServiceException → propaga
    // (no debe ser capturada ni transformada por RecommendationService)
    // -------------------------------------------------------------------------

    @Test
    void getRecommendations_propagatesAiServiceException_whenAiThrowsException() {
        when(playListRepository.findByListName("rock-clasico"))
                .thenReturn(Optional.of(playList));
        when(aiRecommendationService.recommend(any()))
                .thenThrow(new AiServiceException(
                        "El servicio de recomendaciones no está disponible en este momento."));

        assertThatThrownBy(() -> recommendationService.getRecommendations("rock-clasico"))
                .isInstanceOf(AiServiceException.class)
                .hasMessageContaining("servicio de recomendaciones");
    }

    // -------------------------------------------------------------------------
    // Escenario 4: timeout del proveedor → AiServiceException con causa → propaga
    // GeminiAiRecommendationService ya envuelve el timeout en AiServiceException.
    // RecommendationService no debe interceptar ni modificar esa excepción.
    // -------------------------------------------------------------------------

    @Test
    void getRecommendations_propagatesAiServiceException_whenAiTimesOut() {
        Throwable timeoutCause = new java.util.concurrent.TimeoutException("Read timeout");
        when(playListRepository.findByListName("rock-clasico"))
                .thenReturn(Optional.of(playList));
        when(aiRecommendationService.recommend(any()))
                .thenThrow(new AiServiceException(
                        "El servicio de recomendaciones no está disponible en este momento.", timeoutCause));

        assertThatThrownBy(() -> recommendationService.getRecommendations("rock-clasico"))
                .isInstanceOf(AiServiceException.class)
                .hasCause(timeoutCause);
    }

    // -------------------------------------------------------------------------
    // Escenario 5: lista vacía → IA recibe el playList (la lógica del prompt
    // maneja la lista vacía internamente con un mensaje por defecto)
    // -------------------------------------------------------------------------

    @Test
    void getRecommendations_callsAi_evenWhenPlaylistIsEmpty() {
        playList.setSongs(new ArrayList<>());
        List<RecommendedSongResponse> expected = List.of(
                new RecommendedSongResponse("Bohemian Rhapsody", "Queen", "rock")
        );
        when(playListRepository.findByListName("lista-vacia"))
                .thenReturn(Optional.of(playList));
        when(aiRecommendationService.recommend(playList))
                .thenReturn(expected);

        List<RecommendedSongResponse> result = recommendationService.getRecommendations("lista-vacia");

        assertThat(result).hasSize(1);
        verify(aiRecommendationService).recommend(playList);
    }
}
