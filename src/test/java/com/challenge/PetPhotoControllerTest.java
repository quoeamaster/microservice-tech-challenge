package com.challenge;

import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.client.RestTemplate;

/** Whole application with a real SQLite file; only the external photo APIs are mocked. */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:sqlite:target/controller-test.db",
        "app.cors.allowed-origins=http://localhost:5173"
})
@AutoConfigureMockMvc
class PetPhotoControllerTest {

    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G'};

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private RestTemplate restTemplate;
    @Autowired
    private JdbcTemplate jdbc;

    private MockRestServiceServer server;

    @BeforeEach
    void setUp() {
        jdbc.update("DELETE FROM fetched_photos");
        server = MockRestServiceServer.bindTo(restTemplate).build();
    }

    @Test
    void fetchThenGetLatestImageAndMeta() throws Exception {
        server.expect(once(), requestTo(startsWith("https://placebear.com/"))).andRespond(withSuccess(PNG, MediaType.IMAGE_PNG));

        mockMvc.perform(post("/api/photos/fetch").contentType(MediaType.APPLICATION_JSON).content("{\"animal\": \"bear\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("success"))
                .andExpect(jsonPath("$.photos[0].animal").value("BEAR"))
                .andExpect(jsonPath("$.photos[0].imageUrl").exists());

        mockMvc.perform(get("/api/photos/latest/image"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.IMAGE_PNG)) // real type, not always JPEG
                .andExpect(content().bytes(PNG));
        mockMvc.perform(get("/api/photos/latest/meta").param("animal", "bear"))
                .andExpect(jsonPath("$.sourceWebsite").value(startsWith("https://placebear.com/")))
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    void fetchWithoutBodyStillWorks() throws Exception {
        // No body = one photo of a random animal (the original behaviour). Accept any source.
        server.expect(once(), requestTo(startsWith("https://"))).andRespond(withSuccess(PNG, MediaType.IMAGE_PNG));

        mockMvc.perform(post("/api/photos/fetch"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.photos.length()").value(1));
    }

    @Test
    void fetchesRequestedCount() throws Exception {
        server.expect(org.springframework.test.web.client.ExpectedCount.times(3), requestTo(startsWith("https://place.dog/")))
                .andRespond(withSuccess(PNG, MediaType.IMAGE_PNG));

        mockMvc.perform(post("/api/photos/fetch").contentType(MediaType.APPLICATION_JSON).content("{\"animal\": \"dog\", \"count\": 3}"))
                .andExpect(jsonPath("$.photos.length()").value(3));
    }

    @Test
    void returns502WhenAllSourcesFail() throws Exception {
        server.expect(once(), requestTo(startsWith("https://placebear.com/"))).andRespond(withServerError());

        mockMvc.perform(post("/api/photos/fetch").contentType(MediaType.APPLICATION_JSON).content("{\"animal\": \"bear\"}"))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.detail").value(startsWith("All 1 BEAR photo source(s) failed")));
    }

    @Test
    void returns400ForBadInput() throws Exception {
        mockMvc.perform(post("/api/photos/fetch").contentType(MediaType.APPLICATION_JSON).content("{\"animal\": \"unicorn\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Unknown animal 'unicorn'. Supported: [CAT, DOG, BEAR]"));
        mockMvc.perform(post("/api/photos/fetch").contentType(MediaType.APPLICATION_JSON).content("{\"count\": 11}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("count must be at most 10"));
        mockMvc.perform(post("/api/photos/fetch").contentType(MediaType.APPLICATION_JSON).content("not json"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void returns404WhenNothingSaved() throws Exception {
        mockMvc.perform(get("/api/photos/latest/image"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("No photo saved yet"));
        mockMvc.perform(get("/api/photos/999/image")).andExpect(status().isNotFound());
    }

    @Test
    void unknownUrlIs404NotA500() throws Exception {
        mockMvc.perform(get("/does-not-exist")).andExpect(status().isNotFound());
    }

    @Test
    void corsAllowsConfiguredOriginOnly() throws Exception {
        mockMvc.perform(options("/api/photos/fetch")
                        .header(HttpHeaders.ORIGIN, "http://localhost:5173")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "http://localhost:5173"));
        mockMvc.perform(options("/api/photos/fetch")
                        .header(HttpHeaders.ORIGIN, "https://evil.example")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST"))
                .andExpect(status().isForbidden());
    }
}
