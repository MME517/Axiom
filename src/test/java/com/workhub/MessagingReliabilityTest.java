package com.workhub;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.workhub.entity.Job;
import com.workhub.messaging.ReportJobMessage;
import com.workhub.messaging.ReportProducer;
import com.workhub.repository.JobRepository;
import com.workhub.repository.ProcessedMessageRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * <h2>Messaging Reliability Test — Step 5</h2>
 *
 * Verified properties:
 * <ol>
 *   <li><b>Happy path:</b> POST /projects/{id}/generate-report → message consumed →
 *       DB updated to reportStatus=DONE within 15 s.</li>
 *   <li><b>Idempotency:</b> the same AMQP messageId delivered twice only
 *       produces one {@code ProcessedMessage} row.</li>
 *   <li><b>Correlation ID propagation:</b> X-Correlation-ID header is echoed back
 *       and stored on the Job record.</li>
 * </ol>
 *
 * <p>The RabbitMQ broker is provided by Testcontainers so the test is fully
 * hermetic — no external broker required.</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers
class MessagingReliabilityTest {

    // ── Testcontainers – isolated RabbitMQ broker ──────────────────────────
    @Container
    static final RabbitMQContainer RABBIT =
            new RabbitMQContainer("rabbitmq:3.13-management")
                    .withExposedPorts(5672);

    @DynamicPropertySource
    static void rabbitProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.rabbitmq.host", RABBIT::getHost);
        registry.add("spring.rabbitmq.port", RABBIT::getAmqpPort);
        registry.add("spring.rabbitmq.username", () -> "guest");
        registry.add("spring.rabbitmq.password", () -> "guest");
    }

    // ── Spring context ─────────────────────────────────────────────────────
    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private JobRepository jobRepository;
    @Autowired private ProcessedMessageRepository processedMessageRepository;
    @Autowired private ReportProducer reportProducer;
    @Autowired private RabbitTemplate rabbitTemplate;

    @Value("${workhub.rabbitmq.report-exchange}")
    private String reportExchange;

    @Value("${workhub.rabbitmq.report-routing-key}")
    private String reportRoutingKey;

    @BeforeEach
    void clearIdempotencyTable() {
        processedMessageRepository.deleteAll();
    }

    // ══════════════════════════════════════════════════════════════════════
    // Test 1 – Full end-to-end happy path
    // ══════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("T1 – POST generate-report triggers async processing; DB updated to DONE")
    void happyPath_reportStatusBecomeDone() throws Exception {
        LoginContext login = loginAsTenantAdmin();
        String projectId = createProject(login.token(), "ReliabilityProject-HappyPath");
        String correlationId = "test-corr-" + UUID.randomUUID();

        // Trigger report generation
        MvcResult result = mockMvc.perform(
                        post("/projects/{id}/generate-report", projectId)
                                .header("Authorization", "Bearer " + login.token())
                                .header("X-Correlation-ID", correlationId))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.jobId").exists())
                .andExpect(jsonPath("$.reportStatus").value("PENDING"))
                .andReturn();

        // Verify correlation ID echoed in response header
        String echoed = result.getResponse().getHeader("X-Correlation-ID");
        assertThat(echoed).isEqualTo(correlationId);

        JsonNode json = objectMapper.readTree(result.getResponse().getContentAsString());
        String jobId = json.get("jobId").asText();

        // ── Awaitility: wait up to 15 s for the consumer to finish ────────
        await("reportStatus=DONE for job " + jobId)
                .atMost(15, TimeUnit.SECONDS)
                .pollInterval(500, TimeUnit.MILLISECONDS)
                .untilAsserted(() -> {
                    Job job = jobRepository.findById(jobId).orElseThrow();
                    assertThat(job.getReportStatus()).isEqualTo("DONE");
                    assertThat(job.getStatus()).isEqualTo("COMPLETED");
                    assertThat(job.getCorrelationId()).isEqualTo(correlationId);
                });

        // Verify ProcessedMessage record created (idempotency guard is live)
        assertThat(processedMessageRepository.findAll()).hasSize(1);
    }

    // ══════════════════════════════════════════════════════════════════════
    // Test 2 – Idempotency: same messageId delivered twice
    // ══════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("T2 – Duplicate message delivery is detected and skipped; only one ProcessedMessage row")
    void idempotency_duplicateMessageSkipped() throws Exception {
        LoginContext login = loginAsTenantAdmin();
        String projectId = createProject(login.token(), "ReliabilityProject-Idempotency");

        // Create a Job row directly so the consumer can find it
        Job job = jobRepository.save(Job.builder()
                .tenantId(login.tenantId())
                .projectId(projectId)
                .status("PENDING")
                .reportStatus("PENDING")
                .correlationId("idem-test")
                .build());

        String sharedMessageId = UUID.randomUUID().toString();

        ReportJobMessage payload = ReportJobMessage.builder()
                .jobId(job.getJobId())
                .projectId(projectId)
                .tenantId(login.tenantId())
                .correlationId("idem-test")
                .build();

        // Publish the first copy
        publishWithMessageId(payload, sharedMessageId);

        // Wait for first processing to complete
        await("first message processed")
                .atMost(15, TimeUnit.SECONDS)
                .pollInterval(500, TimeUnit.MILLISECONDS)
                .untilAsserted(() -> {
                    Job updated = jobRepository.findById(job.getJobId()).orElseThrow();
                    assertThat(updated.getReportStatus()).isEqualTo("DONE");
                });

        // Publish exact duplicate (same messageId)
        publishWithMessageId(payload, sharedMessageId);

        // Give consumer time to pick up the duplicate
        await("duplicate message settled")
                .atMost(10, TimeUnit.SECONDS)
                .pollInterval(300, TimeUnit.MILLISECONDS)
                .until(() -> true);   // just let time pass

        // Only one ProcessedMessage row should exist (duplicate skipped)
        assertThat(processedMessageRepository.existsByMessageId(sharedMessageId)).isTrue();
        assertThat(processedMessageRepository.findAll()).hasSize(1);
    }

    // ══════════════════════════════════════════════════════════════════════
    // Test 3 – Correlation ID propagated to Job row
    // ══════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("T3 – X-Correlation-ID header auto-generated when absent; stored on Job")
    void correlationId_autoGeneratedWhenAbsent() throws Exception {
        LoginContext login = loginAsTenantAdmin();
        String projectId = createProject(login.token(), "ReliabilityProject-CorrId");

        // No X-Correlation-ID header – server mints one
        MvcResult result = mockMvc.perform(
                        post("/projects/{id}/generate-report", projectId)
                                .header("Authorization", "Bearer " + login.token()))
                .andExpect(status().isAccepted())
                .andReturn();

        // Auto-generated correlation ID must be in the response header
        String autoCorrelationId = result.getResponse().getHeader("X-Correlation-ID");
        assertThat(autoCorrelationId).isNotBlank();

        JsonNode json = objectMapper.readTree(result.getResponse().getContentAsString());
        String jobId = json.get("jobId").asText();

        // Wait for consumer to finish and verify correlationId stored on Job
        await("job DONE with correlationId")
                .atMost(15, TimeUnit.SECONDS)
                .pollInterval(500, TimeUnit.MILLISECONDS)
                .untilAsserted(() -> {
                    Job updated = jobRepository.findById(jobId).orElseThrow();
                    assertThat(updated.getReportStatus()).isEqualTo("DONE");
                    assertThat(updated.getCorrelationId()).isEqualTo(autoCorrelationId);
                });
    }

    // ══════════════════════════════════════════════════════════════════════
    // Helpers
    // ══════════════════════════════════════════════════════════════════════

    private void publishWithMessageId(ReportJobMessage payload, String messageId) {
        rabbitTemplate.convertAndSend(
                reportExchange,
                reportRoutingKey,
                payload,
                msg -> {
                    MessageProperties props = msg.getMessageProperties();
                    props.setMessageId(messageId);
                    props.setContentType(MessageProperties.CONTENT_TYPE_JSON);
                    return msg;
                });
    }

    private LoginContext loginAsTenantAdmin() throws Exception {
        MvcResult result = mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"admin@acme.com\",\"password\":\"admin123\"}"))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode json = objectMapper.readTree(result.getResponse().getContentAsString());
        return new LoginContext(
                json.get("token").asText(),
                json.get("tenantId").asText(),
                json.get("userId").asText());
    }

    private String createProject(String token, String name) throws Exception {
        MvcResult result = mockMvc.perform(post("/projects")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + token)
                        .content("{\"name\":\"" + name + "\"}"))
                .andExpect(status().isCreated())
                .andReturn();

        JsonNode json = objectMapper.readTree(result.getResponse().getContentAsString());
        return json.get("projectId").asText();
    }

    private record LoginContext(String token, String tenantId, String userId) {}
}
