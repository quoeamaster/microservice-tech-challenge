package com.challenge;

/** One row of the fetched_photos table. */
public record Photo(long id, AnimalType animalType, byte[] imageBinary, String contentType,
                    String timestamp, String sourceWebsite) {
}
