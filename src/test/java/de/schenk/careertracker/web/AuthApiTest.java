package de.schenk.careertracker.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Registration and login against the real security chain, including a real signed token.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AuthApiTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private ResultActions register(String username, String password) throws Exception {
        return mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"%s\",\"password\":\"%s\"}".formatted(username, password)));
    }

    private ResultActions login(String username, String password) throws Exception {
        return mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"%s\",\"password\":\"%s\"}".formatted(username, password)));
    }

    @Test
    void registerCreatesUser() throws Exception {
        register("Max", "supersecret1")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.username").value("max"));
    }

    @Test
    void registerRejectsDuplicateUsernameCaseInsensitively() throws Exception {
        register("max", "supersecret1").andExpect(status().isCreated());

        register("MAX", "anotherSecret2").andExpect(status().isConflict());
    }

    @Test
    void registerRejectsWeakPasswordAndInvalidUsername() throws Exception {
        register("max", "short").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.password").exists());
        register("no spaces!", "supersecret1").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.username").exists());
    }

    @Test
    void loginReturnsBearerToken() throws Exception {
        register("max", "supersecret1").andExpect(status().isCreated());

        login("max", "supersecret1")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.expiresInSeconds").value(3600));
    }

    @Test
    void loginWithWrongPasswordOrUnknownUserReturns401() throws Exception {
        register("max", "supersecret1").andExpect(status().isCreated());

        login("max", "wrong-password").andExpect(status().isUnauthorized());
        login("ghost", "supersecret1").andExpect(status().isUnauthorized());
    }

    @Test
    void issuedTokenGrantsAccessToProtectedEndpoints() throws Exception {
        register("max", "supersecret1").andExpect(status().isCreated());
        String response = login("max", "supersecret1").andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode json = objectMapper.readTree(response);
        String token = json.get("accessToken").asText();
        assertThat(token).isNotBlank();

        mockMvc.perform(get("/api/applications").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void tamperedTokenIsRejected() throws Exception {
        register("max", "supersecret1").andExpect(status().isCreated());
        String response = login("max", "supersecret1").andReturn().getResponse().getContentAsString();
        String token = objectMapper.readTree(response).get("accessToken").asText();
        String tampered = token.substring(0, token.length() - 2) + (token.endsWith("AA") ? "BB" : "AA");

        mockMvc.perform(get("/api/applications").header(HttpHeaders.AUTHORIZATION, "Bearer " + tampered))
                .andExpect(status().isUnauthorized());
    }
}
