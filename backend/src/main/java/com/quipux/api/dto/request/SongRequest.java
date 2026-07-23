package com.quipux.api.dto.request;

import jakarta.validation.constraints.NotBlank;

public record SongRequest(
        @NotBlank String titulo,
        @NotBlank String artista,
        @NotBlank String album,
        @NotBlank String anno,
        @NotBlank String genero
) {}
