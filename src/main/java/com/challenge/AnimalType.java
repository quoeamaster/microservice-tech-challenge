package com.challenge;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * Supported animals and their photo sources, tried in order (primary first, then backups).
 * {w} and {h} are replaced with a random size per request: these services return the same
 * photo for the same size, so a fixed size would always give the same picture.
 */
public enum AnimalType {
    // placekitten.com (from the brief) is offline, so cats come from cataas.com, with placecats.com as backup.
    CAT(List.of("https://cataas.com/cat?width={w}&height={h}", "https://placecats.com/{w}/{h}")),
    DOG(List.of("https://place.dog/{w}/{h}", "https://placedog.net/{w}/{h}")),
    BEAR(List.of("https://placebear.com/{w}/{h}"));

    private final List<String> sourceTemplates;

    AnimalType(List<String> sourceTemplates) {
        this.sourceTemplates = sourceTemplates;
    }

    public List<String> sourceTemplates() {
        return sourceTemplates;
    }

    /** Case-insensitive: accepts "cat", "Cat" or "CAT". */
    public static AnimalType fromString(String value) {
        try {
            return valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new IllegalArgumentException(
                    "Unknown animal '" + value + "'. Supported: " + Arrays.toString(values()));
        }
    }
}
