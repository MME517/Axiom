package com.workhub;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.workhub.repository.JobRepository;
import com.workhub.repository.TaskRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ProjectApiIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private TaskRepository taskRepository;

    @Autowired
    private JobRepository jobRepository;

    @Test
    void shouldExecuteProjectAndTaskCrudFlow() throws Exception {
        LoginContext login = loginAsTenantAdmin();
        String projectId = createProject(login.token(), "Phase1 Project");

        mockMvc.perform(get("/projects")
                        .header("Authorization", "Bearer " + login.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].projectId").exists());

        mockMvc.perform(get("/projects/{id}", projectId)
                        .header("Authorization", "Bearer " + login.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.projectId").value(projectId));

        String taskId = createTask(login.token(), projectId, false, "TODO");

        mockMvc.perform(patch("/tasks/{id}", taskId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + login.token())
                        .content("{" +
                                "\"status\":\"DONE\"" +
                                "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DONE"));
    }

    @Test
    void shouldRollbackWhenFailureIsSimulatedMidTransaction() throws Exception {
        LoginContext login = loginAsTenantAdmin();
        String projectId = createProject(login.token(), "Rollback Project");

        mockMvc.perform(post("/projects/{id}/tasks", projectId)
                        .queryParam("simulateFailure", "true")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + login.token())
                        .content("{" +
                                "\"status\":\"IN_PROGRESS\"" +
                                "}"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.error")
                        .value("Simulated transactional failure"));

        assertThat(taskRepository
                .findAllByProjectIdAndTenantId(projectId, login.tenantId())).isEmpty();
        assertThat(jobRepository
                .findAllByProjectIdAndTenantId(projectId, login.tenantId())).isEmpty();

        mockMvc.perform(get("/projects/{id}", projectId)
                        .header("Authorization", "Bearer " + login.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tasks").isArray())
                .andExpect(jsonPath("$.tasks.length()").value(0));
    }

    private LoginContext loginAsTenantAdmin() throws Exception {
        MvcResult result = mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{" +
                                "\"email\":\"admin@acme.com\"," +
                                "\"password\":\"admin123\"" +
                                "}"))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode json = objectMapper.readTree(result.getResponse().getContentAsString());
        return new LoginContext(
                json.get("token").asText(),
                json.get("tenantId").asText(),
                json.get("userId").asText()
        );
    }

    private String createProject(String token, String name) throws Exception {
        MvcResult result = mockMvc.perform(post("/projects")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + token)
                        .content("{" +
                                "\"name\":\"" + name + "\"" +
                                "}"))
                .andExpect(status().isCreated())
                .andReturn();

        JsonNode json = objectMapper.readTree(result.getResponse().getContentAsString());
        return json.get("projectId").asText();
    }

    private String createTask(String token,
                              String projectId,
                              boolean simulateFailure,
                              String statusValue) throws Exception {
        MvcResult result = mockMvc.perform(post("/projects/{id}/tasks", projectId)
                        .queryParam("simulateFailure", String.valueOf(simulateFailure))
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + token)
                        .content("{" +
                                "\"status\":\"" + statusValue + "\"" +
                                "}"))
                .andExpect(status().isCreated())
                .andReturn();

        JsonNode json = objectMapper.readTree(result.getResponse().getContentAsString());
        return json.get("taskId").asText();
    }

    private record LoginContext(String token, String tenantId, String userId) {
    }
}
