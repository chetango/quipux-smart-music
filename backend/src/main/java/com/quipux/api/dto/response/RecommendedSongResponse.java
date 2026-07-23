package com.quipux.api.dto.response;

public record RecommendedSongResponse(
        String titulo,
        String artista,
        String genero
) {}
