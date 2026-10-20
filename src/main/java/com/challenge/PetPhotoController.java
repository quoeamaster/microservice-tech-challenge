package com.challenge;

import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/photos")
public class PetPhotoController {
    private final PetPhotoService petPhotoService;

    public PetPhotoController(PetPhotoService petPhotoService) {
        this.petPhotoService = petPhotoService;
    }

    // A. Fetch random photo(s) from the public APIs, save to SQLite, and return their metadata.
    //    Body (optional): {"animal": "cat|dog|bear", "count": 1-10}
    @PostMapping("/fetch")
    public ResponseEntity<Map<String, Object>> fetchAndSave(@Valid @RequestBody(required = false) FetchRequest request) {
        AnimalType animal = request == null || request.animal() == null || request.animal().isBlank()
                ? null : AnimalType.fromString(request.animal());
        int count = request == null || request.count() == null ? 1 : request.count();
        List<Map<String, Object>> photos = petPhotoService.fetchAndSave(animal, count).stream()
                .map(PetPhotoController::metadata)
                .toList();
        return ResponseEntity.ok(Map.of("status", "success", "photos", photos));
    }

    // B. Fetch the latest saved image binary directly from the SQLite table for browser rendering.
    //    Optional ?animal=cat|dog|bear for the latest of one animal.
    @GetMapping("/latest/image")
    public ResponseEntity<byte[]> getLatestImageBinary(@RequestParam(required = false) String animal) {
        Photo latest = latest(animal);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(latest.contentType()))
                .body(latest.imageBinary());
    }

    @GetMapping("/{id:\\d+}/image")
    public ResponseEntity<byte[]> getImageById(@PathVariable long id) {
        Photo photo = petPhotoService.getPhoto(id)
                .orElseThrow(() -> new PhotoNotFoundException("Photo " + id + " not found"));
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(photo.contentType()))
                .body(photo.imageBinary());
    }

    @GetMapping("/latest/meta")
    public Map<String, Object> getLatestMeta(@RequestParam(required = false) String animal) {
        return metadata(latest(animal));
    }

    private Photo latest(String animal) {
        AnimalType animalType = animal == null || animal.isBlank() ? null : AnimalType.fromString(animal);
        return petPhotoService.getLatestPhoto(animalType)
                .orElseThrow(() -> new PhotoNotFoundException(
                        animalType == null ? "No photo saved yet" : "No " + animal + " photo saved yet"));
    }

    private static Map<String, Object> metadata(Photo photo) {
        return Map.of(
                "id", photo.id(),
                "animal", photo.animalType(),
                "contentType", photo.contentType(),
                "sizeBytes", photo.imageBinary().length,
                "timestamp", photo.timestamp(),
                "sourceWebsite", photo.sourceWebsite(),
                "imageUrl", "/api/photos/" + photo.id() + "/image");
    }
}
