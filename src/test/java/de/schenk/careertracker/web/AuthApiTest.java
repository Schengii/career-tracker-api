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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
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

    private JsonNode readTokens(ResultActions actions) throws Exception {
        return objectMapper.readTree(actions.andReturn().getResponse().getContentAsString());
    }

    private ResultActions postJson(String path, String json) throws Exception {
        return mockMvc.perform(post(path).contentType(MediaType.APPLICATION_JSON).content(json));
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
                .andExpect(jsonPath("$.refreshToken").isNotEmpty())
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

    @Test
    void refreshRotatesTokensAndRejectsReuse() throws Exception {
        register("max", "supersecret1").andExpect(status().isCreated());
        String first = readTokens(login("max", "supersecret1").andExpect(status().isOk()))
                .get("refreshToken").asText();

        JsonNode second = readTokens(postJson("/api/auth/refresh", "{\"refreshToken\":\"%s\"}".formatted(first))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty()));
        assertThat(second.get("refreshToken").asText()).isNotEqualTo(first);

        // the consumed token is dead ...
        postJson("/api/auth/refresh", "{\"refreshToken\":\"%s\"}".formatted(first))
                .andExpect(status().isUnauthorized());
        // ... and replaying it burns the whole token family (theft detection)
        postJson("/api/auth/refresh", "{\"refreshToken\":\"%s\"}".formatted(second.get("refreshToken").asText()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void refreshWithUnknownTokenReturns401() throws Exception {
        postJson("/api/auth/refresh", "{\"refreshToken\":\"does-not-exist\"}")
                .andExpect(status().isUnauthorized());
    }

    @Test
    void logoutRevokesRefreshToken() throws Exception {
        register("max", "supersecret1").andExpect(status().isCreated());
        String refresh = readTokens(login("max", "supersecret1")).get("refreshToken").asText();
        String body = "{\"refreshToken\":\"%s\"}".formatted(refresh);

        postJson("/api/auth/logout", body).andExpect(status().isNoContent());
        postJson("/api/auth/refresh", body).andExpect(status().isUnauthorized());
        postJson("/api/auth/logout", body).andExpect(status().isNoContent());
    }

    @Test
    void loginIsRateLimitedAfterRepeatedFailures() throws Exception {
        String user = "brute" + System.nanoTime();
        register(user, "supersecret1").andExpect(status().isCreated());

        for (int i = 0; i < 5; i++) {
            login(user, "wrong-password").andExpect(status().isUnauthorized());
        }

        // even the correct password is refused while the limit is active
        login(user, "supersecret1")
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists(HttpHeaders.RETRY_AFTER));
    }
}
