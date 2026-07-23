package com.quipux.api.dto.response;

public record SongResponse(
        Long id,
        String titulo,
        String artista,
        String album,
        String anno,
        String genero
) {}
