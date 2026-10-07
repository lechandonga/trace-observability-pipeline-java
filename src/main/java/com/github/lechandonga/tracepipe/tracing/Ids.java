package com.github.lechandonga.tracepipe.tracing;

import java.security.SecureRandom;
import java.util.HexFormat;

/**
 * TraceId（128bit/32 hex）与 SpanId（64bit/16 hex）。
 */
final class Ids {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final HexFormat HEX = HexFormat.of();

    private Ids() {
    }

    static String traceId() {
        byte[] bytes = new byte[16];
        RANDOM.nextBytes(bytes);
        return HEX.formatHex(bytes);
    }

    static String spanId() {
        byte[] bytes = new byte[8];
        RANDOM.nextBytes(bytes);
        return HEX.formatHex(bytes);
    }
}
