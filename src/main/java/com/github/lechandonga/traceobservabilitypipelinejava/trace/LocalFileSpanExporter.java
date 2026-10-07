package com.github.lechandonga.traceobservabilitypipelinejava.trace;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * 本地文件导出端：JSON Lines 追加写，不依赖任何外部服务或凭据。
 */
public class LocalFileSpanExporter implements SpanExporter {

    private final BufferedWriter writer;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public LocalFileSpanExporter(String exportFile) throws IOException {
        Path path = Path.of(exportFile).toAbsolutePath();
        if (path.getParent() != null) {
            Files.createDirectories(path.getParent());
        }
        this.writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.APPEND);
    }

    @Override
    public synchronized void export(Tracer.CompletedTrace trace) throws IOException {
        ObjectNode root = objectMapper.createObjectNode();
        root.put("traceId", trace.traceId());
        ArrayNode spansNode = root.putArray("spans");
        for (Span span : trace.spans()) {
            ObjectNode node = spansNode.addObject();
            node.put("spanId", span.getSpanId());
            if (span.getParentSpanId() != null) {
                node.put("parentSpanId", span.getParentSpanId());
            }
            node.put("name", span.getName());
            node.put("startEpochMillis", span.getStartEpochMillis());
            node.put("durationMillis", span.getDurationMillis());
            node.put("status", span.getStatus().name());
            if (span.getErrorType() != null) {
                node.put("errorType", span.getErrorType());
                node.put("errorMessage", span.getErrorMessage());
            }
        }
        writer.write(objectMapper.writeValueAsString(root));
        writer.newLine();
        writer.flush();
    }

    @Override
    public synchronized void close() {
        try {
            writer.close();
        } catch (IOException ignored) {
        }
    }
}
