package com.challenge;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class PetPhotoApplication {

    public static void main(String[] args) throws IOException {
        // SQLite creates the database file itself, but not its folder.
        Files.createDirectories(Path.of("data"));
        SpringApplication.run(PetPhotoApplication.class, args);
    }
}
