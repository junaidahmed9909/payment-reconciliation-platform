package com.junaid.recon.api;

import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/** End-to-end tests through HTTP, Flyway and the database (H2 in PostgreSQL mode). Kafka is off. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ReconciliationApiTest {

    private static final String HEADER = "reference,account_id,amount,currency,value_date,description\n";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper json;

    private static MockMultipartFile csv(String name, String body) {
        return new MockMultipartFile(name, name + ".csv", "text/csv", (HEADER + body).getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void batchRunReturnsSummaryAndExceptionsAndCanBeFetchedAgain() throws Exception {
        MockMultipartFile bank = csv("bank", """
                T1,ACC1,100.00,USD,2026-03-01,ok
                T2,ACC1,50.00,USD,2026-03-01,short
                T3,ACC2,10.00,USD,2026-03-01,missing in ledger
                BAD,ACC1,oops,USD,2026-03-01,bad row
                """);
        MockMultipartFile ledger = csv("ledger", """
                T1,ACC1,100.00,USD,2026-03-01,ok
                T2,ACC1,55.00,USD,2026-03-01,short
                T4,ACC3,20.00,USD,2026-03-01,missing in bank
                """);

        MvcResult created = mvc.perform(multipart("/api/reconciliations").file(bank).file(ledger))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.summary.total").value(4))
                .andExpect(jsonPath("$.summary.countsByStatus.MATCHED").value(1))
                .andExpect(jsonPath("$.summary.countsByStatus.AMOUNT_MISMATCH").value(1))
                .andExpect(jsonPath("$.summary.countsByStatus.UNMATCHED_BANK").value(1))
                .andExpect(jsonPath("$.summary.countsByStatus.UNMATCHED_LEDGER").value(1))
                .andExpect(jsonPath("$.parseErrors", hasSize(1)))
                .andExpect(jsonPath("$.exceptions", hasSize(3)))
                .andReturn();

        JsonNode body = json.readTree(created.getResponse().getContentAsString());
        String runId = body.get("runId").asText();

        mvc.perform(get("/api/reconciliations/{id}", runId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.summary.total").value(4))
                .andExpect(jsonPath("$.exceptions", hasSize(3)));

        mvc.perform(get("/api/exceptions").param("status", "AMOUNT_MISMATCH"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalItems").value(greaterThan(0)))
                .andExpect(jsonPath("$.items[0].status").value("AMOUNT_MISMATCH"));

        mvc.perform(get("/api/reconciliations"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].runId").exists());
    }

    @Test
    void unknownRunIsNotFound() throws Exception {
        mvc.perform(get("/api/reconciliations/{id}", "does-not-exist"))
                .andExpect(status().isNotFound());
    }

    @Test
    void emptyFileIsRejected() throws Exception {
        MockMultipartFile empty = new MockMultipartFile("bank", "bank.csv", "text/csv", new byte[0]);
        mvc.perform(multipart("/api/reconciliations").file(empty).file(csv("ledger", "")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void streamingPairsTransactionsAsTheyArrive() throws Exception {
        String bankTxn = """
                {"source":"BANK","reference":"RT-1","accountId":"ACC1","amount":42.00,"currency":"USD","valueDate":"2026-03-01"}
                """;
        String ledgerTxn = """
                {"source":"LEDGER","reference":"rt-1","accountId":"ACC1","amount":42.00,"currency":"USD","valueDate":"2026-03-02"}
                """;

        mvc.perform(post("/api/transactions").contentType(MediaType.APPLICATION_JSON).content(bankTxn))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING"));

        mvc.perform(post("/api/transactions").contentType(MediaType.APPLICATION_JSON).content(ledgerTxn))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RECONCILED"))
                .andExpect(jsonPath("$.result.status").value("MATCHED"));
    }

    @Test
    void invalidTransactionIsRejected() throws Exception {
        mvc.perform(post("/api/transactions").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"source\":\"BANK\",\"amount\":1}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void healthEndpointIsUp() throws Exception {
        mvc.perform(get("/actuator/health")).andExpect(status().isOk());
    }
}
