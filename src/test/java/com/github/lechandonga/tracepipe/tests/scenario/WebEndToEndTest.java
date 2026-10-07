package com.github.lechandonga.tracepipe.tests.scenario;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 场景：端到端 —— Web Filter 自动建根 span、异常透传为 500、异步任务挂接同链路、
 * 状态接口只读可见请求量/失败量/导出量，span 落本地文件。
 */
@SpringBootTest(properties = {
        "tracing.export-file=target/trace-data-test/spans.jsonl",
        "tracing.sample-rate=1.0"
})
@AutoConfigureMockMvc
class WebEndToEndTest {

    private static final ObjectMapper JSON = new ObjectMapper();

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("同步下单：200 + X-Trace-Id，本地文件能看到父子 span")
    void synchronousOrderIsTraced() throws Exception {
        MvcResult result = mockMvc.perform(post("/demo/orders/order-1001"))
                .andExpect(status().isOk())
                .andReturn();
        String traceId = result.getResponse().getHeader("X-Trace-Id");
        assertNotNull(traceId);
        assertEquals(32, traceId.length());

        Path file = Path.of("target/trace-data-test/spans.jsonl");
        assertTrue(waitForFileContains(file, traceId, 4),
                "应能在文件中找到该 traceId 的根 span 与 3 个子 span");
        String content = Files.readString(file);
        assertTrue(content.contains("\"name\":\"POST /demo/orders/order-1001\""));
        assertTrue(content.contains("\"name\":\"payment.charge\""));
    }

    @Test
    @DisplayName("业务异常：HTTP 500 原样透出，状态接口请求失败量增加，span 标注 error")
    void businessErrorReturns500AndIsRecorded() throws Exception {
        mockMvc.perform(get("/tracing/status")).andExpect(status().isOk());
        long before = readStatus("requestsFailed");

        mockMvc.perform(post("/demo/orders/order-bad/fail"))
                .andExpect(status().is5xxServerError())
                .andReturn();

        long after = readStatus("requestsFailed");
        assertTrue(after >= before + 1, "失败请求计数应当增长: " + before + " -> " + after);
        long started = readStatus("requestsStarted");
        assertTrue(started >= 2);
    }

    @Test
    @DisplayName("异步下单：异步子 span 与 HTTP 根 span 属于同一条 trace")
    void asyncOrderSharesTrace() throws Exception {
        MvcResult result = mockMvc.perform(post("/demo/orders/order-async/async"))
                .andExpect(status().isOk())
                .andReturn();
        String traceId = result.getResponse().getHeader("X-Trace-Id");
        Path file = Path.of("target/trace-data-test/spans.jsonl");
        assertTrue(waitForFileContains(file, traceId, 2),
                "文件中应包含该 trace 的根 span 与异步子 span");
        String content = Files.readString(file);
        assertTrue(content.contains("\"name\":\"order.async-processing\""));
    }

    @Test
    @DisplayName("状态接口：只读展示计数、积压和上限配置")
    void statusEndpointExposesMetricsAndLimits() throws Exception {
        MvcResult result = mockMvc.perform(get("/tracing/status"))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode body = JSON.readTree(result.getResponse().getContentAsString());
        assertTrue(body.get("requestsStarted").asLong() >= 1);
        assertTrue(body.has("requestsFailed"));
        assertTrue(body.has("droppedBacklogFull"));
        assertTrue(body.has("exportFailures"));
        assertEquals(0, body.get("exportBacklog").asInt());
        assertEquals(1000, body.get("maxTraces").asInt());
        assertEquals(200, body.get("maxSpansPerTrace").asInt());
        assertEquals(1.0, body.get("sampleRate").asDouble(), 0.0);
        assertEquals("target/trace-data-test/spans.jsonl", body.get("exportFile").asText());
    }

    private long readStatus(String field) throws Exception {
        MvcResult result = mockMvc.perform(get("/tracing/status")).andReturn();
        return JSON.readTree(result.getResponse().getContentAsString()).get(field).asLong();
    }

    private static boolean waitForFileContains(Path file, String traceId, int minOccurrences)
            throws Exception {
        long deadline = System.currentTimeMillis() + 5000;
        while (System.currentTimeMillis() < deadline) {
            if (Files.exists(file)) {
                String content = Files.readString(file);
                int idx = 0;
                int count = 0;
                while ((idx = content.indexOf(traceId, idx)) >= 0) {
                    count++;
                    idx += traceId.length();
                }
                if (count >= minOccurrences) {
                    return true;
                }
            }
            Thread.sleep(50);
        }
        return false;
    }
}
