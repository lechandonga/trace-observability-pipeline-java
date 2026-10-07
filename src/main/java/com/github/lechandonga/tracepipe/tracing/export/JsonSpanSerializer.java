package com.github.lechandonga.tracepipe.tracing.export;

import com.github.lechandonga.tracepipe.tracing.FinishedSpan;

/**
 * 零依赖的最小 JSON 序列化器（每行一个 span，JSON Lines）。
 */
public final class JsonSpanSerializer implements SpanSerializer {

    @Override
    public String serialize(FinishedSpan span) {
        StringBuilder sb = new StringBuilder(256);
        sb.append('{');
        appendString(sb, "traceId", span.traceId());
        sb.append(',');
        appendString(sb, "spanId", span.spanId());
        sb.append(',');
        sb.append("\"parentSpanId\":").append(span.parentSpanId() == null ? "null" : quote(span.parentSpanId()));
        sb.append(',');
        appendString(sb, "name", span.name());
        sb.append(",\"startEpochNanos\":").append(span.startEpochNanos());
        sb.append(",\"endEpochNanos\":").append(span.endEpochNanos());
        sb.append(",\"durationMicros\":").append(span.durationMicros());
        sb.append(",\"sampled\":").append(span.sampled());
        sb.append(",\"error\":").append(span.error());
        sb.append(",\"errorType\":").append(span.errorType() == null ? "null" : quote(span.errorType()));
        sb.append(",\"errorMessage\":").append(span.errorMessage() == null ? "null" : quote(span.errorMessage()));
        sb.append('}');
        return sb.toString();
    }

    private static void appendString(StringBuilder sb, String key, String value) {
        sb.append('"').append(key).append("\":").append(quote(value));
    }

    private static String quote(String value) {
        StringBuilder sb = new StringBuilder(value.length() + 8);
        sb.append('"');
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
                }
            }
        }
        sb.append('"');
        return sb.toString();
    }
}
