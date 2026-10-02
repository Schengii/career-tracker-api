package de.schenk.careertracker.web;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end tests through the real Spring context and an in-memory H2 database.
 * Authentication is simulated with the spring-security-test {@code jwt()} post-processor.
 * Each test runs in a transaction that is rolled back afterwards.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class JobApplicationApiTest {

    private static final String BASE = "/api/applications";

    @Autowired
    private MockMvc mockMvc;

    private static RequestPostProcessor as(String username) {
        return jwt().jwt(token -> token.subject(username));
    }

    private long createApplication(String user, String company, String position) throws Exception {
        String body = """
                {"company": "%s", "position": "%s"}
                """.formatted(company, position);
        MvcResult result = mockMvc.perform(post(BASE).with(as(user))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andReturn();
        Matcher matcher = Pattern.compile("\"id\":(\\d+)").matcher(result.getResponse().getContentAsString());
        assertThat(matcher.find()).isTrue();
        return Long.parseLong(matcher.group(1));
    }

    @Test
    void createReturns201WithLocationAndDefaults() throws Exception {
        mockMvc.perform(post(BASE).with(as("alice")).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"company\":\"Bechtle\",\"position\":\"Junior Developer\"}"))
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location"))
                .andExpect(jsonPath("$.status").value("APPLIED"))
                .andExpect(jsonPath("$.company").value("Bechtle"));
    }

    @Test
    void createWithBlankCompanyReturns400() throws Exception {
        mockMvc.perform(post(BASE).with(as("alice")).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"company\":\"\",\"position\":\"Dev\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.company").exists());
    }

    @Test
    void getUnknownIdReturns404() throws Exception {
        mockMvc.perform(get(BASE + "/999999").with(as("alice")))
                .andExpect(status().isNotFound());
    }

    @Test
    void validStatusTransitionIsAccepted() throws Exception {
        long id = createApplication("alice", "ACME", "Developer");

        mockMvc.perform(put(BASE + "/" + id).with(as("alice")).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"company\":\"ACME\",\"position\":\"Developer\",\"status\":\"INTERVIEW\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("INTERVIEW"));
    }

    @Test
    void invalidStatusTransitionReturns409() throws Exception {
        long id = createApplication("alice", "ACME", "Developer");

        mockMvc.perform(put(BASE + "/" + id).with(as("alice")).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"company\":\"ACME\",\"position\":\"Developer\",\"status\":\"ACCEPTED\"}"))
                .andExpect(status().isConflict());
    }

    @Test
    void listCanBeFilteredByStatus() throws Exception {
        long id = createApplication("alice", "ACME", "Developer");
        createApplication("alice", "Other", "Tester");
        mockMvc.perform(put(BASE + "/" + id).with(as("alice")).contentType(MediaType.APPLICATION_JSON)
                .content("{\"company\":\"ACME\",\"position\":\"Developer\",\"status\":\"INTERVIEW\"}"));

        mockMvc.perform(get(BASE).with(as("alice")).param("status", "INTERVIEW"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].company").value("ACME"))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void listIsPaged() throws Exception {
        createApplication("alice", "A", "X");
        createApplication("alice", "B", "X");
        createApplication("alice", "C", "X");

        mockMvc.perform(get(BASE).with(as("alice")).param("size", "2").param("page", "0")
                        .param("sort", "company,asc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(2)))
                .andExpect(jsonPath("$.content[0].company").value("A"))
                .andExpect(jsonPath("$.content[1].company").value("B"))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(2))
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.totalPages").value(2));

        mockMvc.perform(get(BASE).with(as("alice")).param("size", "2").param("page", "1")
                        .param("sort", "company,asc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].company").value("C"));
    }

    @Test
    void sortingByUnknownPropertyReturns400() throws Exception {
        mockMvc.perform(get(BASE).with(as("alice")).param("sort", "owner,asc"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get(BASE).with(as("alice")).param("sort", "doesNotExist"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void statsReportsCountsPerStatus() throws Exception {
        createApplication("alice", "A", "X");
        createApplication("alice", "B", "Y");
        createApplication("bob", "C", "Z");

        mockMvc.perform(get(BASE + "/stats").with(as("alice")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.APPLIED").value(2))
                .andExpect(jsonPath("$.OFFER").value(0));
    }

    @Test
    void deleteRemovesApplication() throws Exception {
        long id = createApplication("alice", "ACME", "Developer");

        mockMvc.perform(delete(BASE + "/" + id).with(as("alice"))).andExpect(status().isNoContent());
        mockMvc.perform(get(BASE + "/" + id).with(as("alice"))).andExpect(status().isNotFound());
    }

    @Test
    void usersCannotSeeOrModifyEachOthersApplications() throws Exception {
        long id = createApplication("alice", "ACME", "Developer");

        mockMvc.perform(get(BASE + "/" + id).with(as("bob"))).andExpect(status().isNotFound());
        mockMvc.perform(put(BASE + "/" + id).with(as("bob")).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"company\":\"Evil\",\"position\":\"Dev\"}"))
                .andExpect(status().isNotFound());
        mockMvc.perform(delete(BASE + "/" + id).with(as("bob"))).andExpect(status().isNotFound());
        mockMvc.perform(get(BASE).with(as("bob")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(0)));

        mockMvc.perform(get(BASE + "/" + id).with(as("alice")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.company").value("ACME"));
    }

    @Test
    void requestsWithoutTokenAreRejectedWith401() throws Exception {
        mockMvc.perform(get(BASE)).andExpect(status().isUnauthorized());
        mockMvc.perform(post(BASE).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"company\":\"A\",\"position\":\"B\"}"))
                .andExpect(status().isUnauthorized());
    }
}
