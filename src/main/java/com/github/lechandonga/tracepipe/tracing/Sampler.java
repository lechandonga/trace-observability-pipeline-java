package com.github.lechandonga.tracepipe.tracing;

import java.util.concurrent.atomic.AtomicLong;

/**
 * 确定性采样器：按 rate（0.0~1.0）做固定比例采样。
 *
 * <p>采样决策由根 Span 做一次，之后随 {@link TraceContext} 在整条链路
 * （含异步、定时子任务）中传播，因此同一条链路的采样结果始终一致。
 */
public final class Sampler {

    private final double rate;
    private final AtomicLong sequence = new AtomicLong();

    public Sampler(double rate) {
        if (rate < 0.0 || rate > 1.0 || Double.isNaN(rate)) {
            throw new IllegalArgumentException("sampleRate must be within [0,1], got " + rate);
        }
        this.rate = rate;
    }

    public boolean sample() {
        if (rate <= 0.0) {
            return false;
        }
        if (rate >= 1.0) {
            return true;
        }
        // 每 100 次决策中取前 sampled 次，小样本下比例也稳定贴合 rate
        long period = 100;
        long sampled = Math.round(rate * period);
        long n = sequence.getAndIncrement();
        return Math.floorMod(n, period) < sampled;
    }

    public double rate() {
        return rate;
    }
}
