package com.challenge;

/** Thrown when no photo source could deliver an image. */
public class PhotoFetchException extends RuntimeException {

    public PhotoFetchException(String message) {
        super(message);
    }
}
