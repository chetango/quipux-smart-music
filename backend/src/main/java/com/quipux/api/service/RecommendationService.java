package com.quipux.api.service;

import com.quipux.api.dto.response.RecommendedSongResponse;
import com.quipux.api.entity.PlayList;
import com.quipux.api.exception.ResourceNotFoundException;
import com.quipux.api.repository.PlayListRepository;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class RecommendationService {

    private final PlayListRepository playListRepository;
    private final AiRecommendationService aiRecommendationService;

    public RecommendationService(
            PlayListRepository playListRepository,
            AiRecommendationService aiRecommendationService) {
        this.playListRepository = playListRepository;
        this.aiRecommendationService = aiRecommendationService;
    }

    @Cacheable(value = "recommendations", key = "#listName")
    @Transactional(readOnly = true)
    public List<RecommendedSongResponse> getRecommendations(String listName) {
        PlayList playList = playListRepository.findByListName(listName)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Lista no encontrada: " + listName));
        return aiRecommendationService.recommend(playList);
    }
}
