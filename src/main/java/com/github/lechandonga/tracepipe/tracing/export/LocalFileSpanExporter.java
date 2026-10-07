package com.github.lechandonga.tracepipe.tracing.export;

import com.github.lechandonga.tracepipe.tracing.FinishedSpan;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;

/**
 * 本地文件导出端：把每条 span 以 JSON Lines 追加写入本地文件，
 * 不依赖任何外部服务或凭据。{@code forceFailure} 仅供测试注入导出故障。
 */
public final class LocalFileSpanExporter implements SpanExporter {

    private final Path file;
    private final SpanSerializer serializer;

    volatile boolean forceFailure;

    public LocalFileSpanExporter(Path file, SpanSerializer serializer) {
        this.file = file;
        this.serializer = serializer;
        try {
            Path parent = file.toAbsolutePath().getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
        } catch (IOException e) {
            throw new IllegalStateException("cannot create trace output directory: " + file, e);
        }
    }

    @Override
    public void export(List<FinishedSpan> batch) throws Exception {
        if (forceFailure) {
            throw new IOException("injected export failure for " + file);
        }
        StringBuilder sb = new StringBuilder(batch.size() * 160);
        for (FinishedSpan span : batch) {
            sb.append(serializer.serialize(span)).append('\n');
        }
        // 导出端变慢的模拟同样由测试通过阻塞点控制；这里仅做一次追加写。
        synchronized (this) {
            try (BufferedWriter writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND)) {
                writer.write(sb.toString());
            }
        }
    }

    public Path file() {
        return file;
    }

    /** 测试用：模拟导出端故障。 */
    public void setForceFailure(boolean forceFailure) {
        this.forceFailure = forceFailure;
    }
}
