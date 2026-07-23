package com.quipux.api.dto.request;

import jakarta.validation.constraints.NotBlank;

public record PlayListRequest(
        @NotBlank String listName,
        @NotBlank String description
) {}
