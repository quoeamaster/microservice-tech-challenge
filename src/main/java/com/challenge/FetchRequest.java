package com.challenge;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

/**
 * Body of POST /api/photos/fetch. Both fields are optional:
 * no animal = a random animal per photo, no count = 1 photo.
 */
public record FetchRequest(
        String animal,
        @Min(value = 1, message = "count must be at least 1")
        @Max(value = 10, message = "count must be at most 10") Integer count) {
}
