package com.github.lechandonga.traceobservabilitypipelinejava.tests.trace;

import java.util.Map;

import com.github.lechandonga.traceobservabilitypipelinejava.trace.TraceContext;
import com.github.lechandonga.traceobservabilitypipelinejava.trace.TraceMetrics;

import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 场景：Web 集成 —— 请求自动进入链路、状态端点只读可见、请求结束后上下文无残留。
 */
@SpringBootTest
@AutoConfigureMockMvc
class TraceFilterWebTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private TraceMetrics metrics;

    @Test
    void requestTracedAndStatusEndpointExposesMetrics() throws Exception {
        ScenarioLog.scenario("Web 集成：请求自动串链路，/trace/status 输出运行状态");

        long before = metrics.getRequestsTotal();
        mockMvc.perform(get("/trace/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.requestsTotal").exists())
                .andExpect(jsonPath("$.tracesDropped").exists())
                .andExpect(jsonPath("$.exportBacklog").exists())
                .andExpect(jsonPath("$.limits.exportQueueCapacity").exists());
        assertTrue(metrics.getRequestsTotal() > before, "请求应被计数");
        ScenarioLog.pass("GET /trace/status 返回请求量/丢弃量/积压/上限，requestsTotal 递增");

        assertNull(TraceContext.current(), "请求结束后上下文应清理干净");
        ScenarioLog.pass("请求结束后当前线程无上下文残留");
    }
}
