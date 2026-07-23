package com.quipux.api.service;

import com.quipux.api.dto.response.RecommendedSongResponse;
import com.quipux.api.entity.PlayList;

import java.util.List;

public interface AiRecommendationService {

    List<RecommendedSongResponse> recommend(PlayList playList);
}
