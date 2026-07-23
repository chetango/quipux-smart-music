package com.quipux.api.controller;

import com.quipux.api.dto.response.RecommendedSongResponse;
import com.quipux.api.service.RecommendationService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/lists")
public class RecommendationController {

    private final RecommendationService recommendationService;

    public RecommendationController(RecommendationService recommendationService) {
        this.recommendationService = recommendationService;
    }

    @GetMapping("/{listName}/recommendations")
    public ResponseEntity<List<RecommendedSongResponse>> getRecommendations(
            @PathVariable String listName) {
        return ResponseEntity.ok(recommendationService.getRecommendations(listName));
    }
}
