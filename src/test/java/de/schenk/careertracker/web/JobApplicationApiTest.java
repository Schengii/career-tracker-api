package de.schenk.careertracker.web;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end tests through the real Spring context and an in-memory H2 database.
 * Each test runs in a transaction that is rolled back afterwards.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class JobApplicationApiTest {

    private static final String BASE = "/api/applications";

    @Autowired
    private MockMvc mockMvc;

    private long createApplication(String company, String position) throws Exception {
        String body = """
                {"company": "%s", "position": "%s"}
                """.formatted(company, position);
        MvcResult result = mockMvc.perform(post(BASE).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andReturn();
        Matcher matcher = Pattern.compile("\"id\":(\\d+)").matcher(result.getResponse().getContentAsString());
        assertThat(matcher.find()).isTrue();
        return Long.parseLong(matcher.group(1));
    }

    @Test
    void createReturns201WithLocationAndDefaults() throws Exception {
        mockMvc.perform(post(BASE).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"company\":\"Bechtle\",\"position\":\"Junior Developer\"}"))
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location"))
                .andExpect(jsonPath("$.status").value("APPLIED"))
                .andExpect(jsonPath("$.company").value("Bechtle"));
    }

    @Test
    void createWithBlankCompanyReturns400() throws Exception {
        mockMvc.perform(post(BASE).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"company\":\"\",\"position\":\"Dev\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.company").exists());
    }

    @Test
    void getUnknownIdReturns404() throws Exception {
        mockMvc.perform(get(BASE + "/999999"))
                .andExpect(status().isNotFound());
    }

    @Test
    void validStatusTransitionIsAccepted() throws Exception {
        long id = createApplication("ACME", "Developer");

        mockMvc.perform(put(BASE + "/" + id).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"company\":\"ACME\",\"position\":\"Developer\",\"status\":\"INTERVIEW\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("INTERVIEW"));
    }

    @Test
    void invalidStatusTransitionReturns409() throws Exception {
        long id = createApplication("ACME", "Developer");

        mockMvc.perform(put(BASE + "/" + id).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"company\":\"ACME\",\"position\":\"Developer\",\"status\":\"ACCEPTED\"}"))
                .andExpect(status().isConflict());
    }

    @Test
    void listCanBeFilteredByStatus() throws Exception {
        long id = createApplication("ACME", "Developer");
        createApplication("Other", "Tester");
        mockMvc.perform(put(BASE + "/" + id).contentType(MediaType.APPLICATION_JSON)
                .content("{\"company\":\"ACME\",\"position\":\"Developer\",\"status\":\"INTERVIEW\"}"));

        mockMvc.perform(get(BASE).param("status", "INTERVIEW"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].company").value("ACME"));
    }

    @Test
    void statsReportsCountsPerStatus() throws Exception {
        createApplication("A", "X");
        createApplication("B", "Y");

        mockMvc.perform(get(BASE + "/stats"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.APPLIED").value(2))
                .andExpect(jsonPath("$.OFFER").value(0));
    }

    @Test
    void deleteRemovesApplication() throws Exception {
        long id = createApplication("ACME", "Developer");

        mockMvc.perform(delete(BASE + "/" + id)).andExpect(status().isNoContent());
        mockMvc.perform(get(BASE + "/" + id)).andExpect(status().isNotFound());
    }
}
