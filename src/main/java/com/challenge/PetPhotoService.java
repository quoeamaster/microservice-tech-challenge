package com.challenge;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

@Service
public class PetPhotoService {

    private static final Logger log = LoggerFactory.getLogger(PetPhotoService.class);

    private static final int MIN_SIZE = 200;
    private static final int MAX_SIZE = 600;
    private static final String INSERT_SQL =
            "INSERT INTO fetched_photos (animal_type, image_binary, content_type, timestamp, source_website) VALUES (?, ?, ?, ?, ?)";
    private static final String SELECT_COLUMNS =
            "SELECT id, animal_type, image_binary, content_type, timestamp, source_website FROM fetched_photos ";
    private static final RowMapper<Photo> PHOTO_MAPPER = (rs, rowNum) -> new Photo(
            rs.getLong("id"),
            AnimalType.valueOf(rs.getString("animal_type")),
            rs.getBytes("image_binary"),
            rs.getString("content_type"),
            rs.getString("timestamp"),
            rs.getString("source_website"));

    private final JdbcTemplate jdbcTemplate;
    private final RestTemplate restTemplate;

    public PetPhotoService(JdbcTemplate jdbcTemplate, RestTemplate restTemplate) {
        this.jdbcTemplate = jdbcTemplate;
        this.restTemplate = restTemplate;
    }

    /**
     * Fetches {@code count} photos of the given animal (a random animal per photo if {@code animal} is null)
     * and saves them. All photos are downloaded before anything is saved, and saving happens in one
     * transaction, so a failure never leaves a partial batch.
     */
    @Transactional
    public List<Photo> fetchAndSave(AnimalType animal, int count) {
        List<Photo> fetched = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            AnimalType animalType = animal != null ? animal : randomAnimal();
            fetched.add(fetchOne(animalType));
        }
        List<Photo> saved = new ArrayList<>(count);
        for (Photo photo : fetched) {
            jdbcTemplate.update(INSERT_SQL, photo.animalType().name(), photo.imageBinary(), photo.contentType(),
                    photo.timestamp(), photo.sourceWebsite());
            long id = jdbcTemplate.queryForObject("SELECT last_insert_rowid()", Long.class);
            saved.add(new Photo(id, photo.animalType(), photo.imageBinary(), photo.contentType(),
                    photo.timestamp(), photo.sourceWebsite()));
        }
        return saved;
    }

    /** Tries each source once, in order, and gives up with a clear error instead of retrying forever. */
    private Photo fetchOne(AnimalType animalType) {
        List<String> templates = animalType.sourceTemplates();
        String lastError = "";
        for (String template : templates) {
            String url = template.replace("{w}", String.valueOf(randomSize())).replace("{h}", String.valueOf(randomSize()));
            try {
                ResponseEntity<byte[]> response = restTemplate.getForEntity(url, byte[].class);
                MediaType contentType = response.getHeaders().getContentType();
                byte[] body = response.getBody();
                if (contentType != null && "image".equals(contentType.getType()) && body != null && body.length > 0) {
                    return new Photo(0, animalType, body, normalize(contentType), Instant.now().toString(), url);
                }
                lastError = url + " did not return an image (content type: " + contentType + ")";
            } catch (RestClientException e) {
                lastError = url + " failed: " + e.getMessage();
            }
            log.warn("{} photo source failed, trying next if available: {}", animalType, lastError);
        }
        throw new PhotoFetchException("All " + templates.size() + " " + animalType + " photo source(s) failed. Last error: " + lastError);
    }

    /** The latest saved photo, optionally of one animal. Database errors are not hidden as "not found". */
    public Optional<Photo> getLatestPhoto(AnimalType animal) {
        List<Photo> rows = animal == null
                ? jdbcTemplate.query(SELECT_COLUMNS + "ORDER BY id DESC LIMIT 1", PHOTO_MAPPER)
                : jdbcTemplate.query(SELECT_COLUMNS + "WHERE animal_type = ? ORDER BY id DESC LIMIT 1", PHOTO_MAPPER, animal.name());
        return rows.stream().findFirst();
    }

    public Optional<Photo> getPhoto(long id) {
        return jdbcTemplate.query(SELECT_COLUMNS + "WHERE id = ?", PHOTO_MAPPER, id).stream().findFirst();
    }

    private static AnimalType randomAnimal() {
        AnimalType[] animals = AnimalType.values();
        return animals[ThreadLocalRandom.current().nextInt(animals.length)];
    }

    private static int randomSize() {
        return ThreadLocalRandom.current().nextInt(MIN_SIZE, MAX_SIZE + 1);
    }

    /** Some sources send the non-standard "image/jpg"; store the standard "image/jpeg". */
    private static String normalize(MediaType contentType) {
        String value = contentType.getType() + "/" + contentType.getSubtype();
        return "image/jpg".equalsIgnoreCase(value) ? MediaType.IMAGE_JPEG_VALUE : value;
    }
}
