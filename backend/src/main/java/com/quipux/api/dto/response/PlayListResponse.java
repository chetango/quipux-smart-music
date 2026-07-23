package com.quipux.api.dto.response;

import java.util.List;

public record PlayListResponse(
        String listName,
        String description,
        List<SongResponse> songs
) {}
