package com.challenge;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.nio.file.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

/** Service against a mocked HTTP server and a real SQLite file built from schema.sql. */
class PetPhotoServiceTest {

    private static final byte[] JPEG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF};

    @TempDir
    Path tempDir;

    private MockRestServiceServer server;
    private PetPhotoService service;
    private JdbcTemplate jdbc;

    @BeforeEach
    void setUp() {
        SingleConnectionDataSource dataSource = new SingleConnectionDataSource("jdbc:sqlite:" + tempDir.resolve("test.db"), true);
        new ResourceDatabasePopulator(new ClassPathResource("schema.sql")).execute(dataSource);
        jdbc = new JdbcTemplate(dataSource);
        RestTemplate restTemplate = new RestTemplate();
        server = MockRestServiceServer.bindTo(restTemplate).build();
        service = new PetPhotoService(jdbc, restTemplate);
    }

    @Test
    void savesPhotoFromPrimarySource() {
        server.expect(once(), requestTo(startsWith("https://place.dog/"))).andRespond(withSuccess(JPEG, MediaType.IMAGE_JPEG));

        Photo saved = service.fetchAndSave(AnimalType.DOG, 1).get(0);

        assertThat(saved.id()).isPositive();
        assertThat(saved.sourceWebsite()).startsWith("https://place.dog/");
        assertThat(saved.contentType()).isEqualTo("image/jpeg");
        assertThat(service.getLatestPhoto(null)).get().extracting(Photo::id).isEqualTo(saved.id());
        server.verify();
    }

    @Test
    void fallsBackToBackupWhenPrimaryFails() {
        server.expect(once(), requestTo(startsWith("https://cataas.com/"))).andRespond(withServerError());
        server.expect(once(), requestTo(startsWith("https://placecats.com/"))).andRespond(withSuccess(JPEG, MediaType.IMAGE_JPEG));

        assertThat(service.fetchAndSave(AnimalType.CAT, 1).get(0).sourceWebsite()).startsWith("https://placecats.com/");
        server.verify();
    }

    /** Regression test: the old version looped forever (while (true)) when every source failed. */
    @Test
    void givesUpAfterTryingEachSourceOnceWhenAllFail() {
        server.expect(once(), requestTo(startsWith("https://cataas.com/"))).andRespond(withServerError());
        server.expect(once(), requestTo(startsWith("https://placecats.com/"))).andRespond(withServerError());

        assertThatThrownBy(() -> service.fetchAndSave(AnimalType.CAT, 1))
                .isInstanceOf(PhotoFetchException.class)
                .hasMessageContaining("All 2 CAT photo source(s) failed");
        server.verify(); // exactly 2 requests, no endless retries
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM fetched_photos", Long.class)).isZero();
    }

    @Test
    void rejectsResponsesThatAreNotImages() {
        server.expect(once(), requestTo(startsWith("https://placebear.com/")))
                .andRespond(withSuccess("<html>error page</html>", MediaType.TEXT_HTML));

        assertThatThrownBy(() -> service.fetchAndSave(AnimalType.BEAR, 1))
                .isInstanceOf(PhotoFetchException.class)
                .hasMessageContaining("did not return an image");
    }

    @Test
    void savesNothingWhenOnePhotoInABatchFails() {
        server.expect(once(), requestTo(startsWith("https://placebear.com/"))).andRespond(withSuccess(JPEG, MediaType.IMAGE_JPEG));
        server.expect(once(), requestTo(startsWith("https://placebear.com/"))).andRespond(withServerError());

        assertThatThrownBy(() -> service.fetchAndSave(AnimalType.BEAR, 2)).isInstanceOf(PhotoFetchException.class);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM fetched_photos", Long.class)).isZero();
    }

    @Test
    void storesRealContentTypeAndNormalizesImageJpg() {
        server.expect(once(), requestTo(startsWith("https://place.dog/")))
                .andRespond(withSuccess(JPEG, MediaType.parseMediaType("image/jpg")));
        server.expect(once(), requestTo(startsWith("https://place.dog/")))
                .andRespond(withSuccess(new byte[] {1}, MediaType.IMAGE_PNG));

        assertThat(service.fetchAndSave(AnimalType.DOG, 2)).extracting(Photo::contentType)
                .containsExactly("image/jpeg", "image/png");
    }

    @Test
    void usesRandomSizesSoPhotosDiffer() {
        server.expect(once(), requestTo(startsWith("https://placebear.com/"))).andRespond(withSuccess(JPEG, MediaType.IMAGE_JPEG));
        String url = service.fetchAndSave(AnimalType.BEAR, 1).get(0).sourceWebsite();

        assertThat(url).matches("https://placebear\\.com/\\d{3}/\\d{3}");
    }

    @Test
    void latestIsPerAnimalAndEmptyWhenNothingSaved() {
        assertThat(service.getLatestPhoto(null)).isEmpty();
        server.expect(once(), requestTo(startsWith("https://place.dog/"))).andRespond(withSuccess(JPEG, MediaType.IMAGE_JPEG));
        service.fetchAndSave(AnimalType.DOG, 1);

        assertThat(service.getLatestPhoto(AnimalType.DOG)).isPresent();
        assertThat(service.getLatestPhoto(AnimalType.CAT)).isEmpty();
    }
}
