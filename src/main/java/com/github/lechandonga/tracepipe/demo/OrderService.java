package com.github.lechandonga.tracepipe.demo;

import com.github.lechandonga.tracepipe.tracing.ActiveSpan;
import com.github.lechandonga.tracepipe.tracing.Tracer;
import com.github.lechandonga.tracepipe.tracing.TracerProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.concurrent.CompletableFuture;

@Service
public class OrderService {

    private static final Logger log = LoggerFactory.getLogger(OrderService.class);

    private final Tracer tracer = TracerProvider.tracer();

    public String createOrder(String orderId) {
        log.info("creating order {}", orderId);
        validate(orderId);
        reserveInventory(orderId);
        chargePayment(orderId);
        log.info("order {} created", orderId);
        return "order-created:" + orderId;
    }

    public String createFailingOrder(String orderId) {
        log.warn("processing order that will fail {}", orderId);
        ActiveSpan span = tracer.startSpan("business.charge");
        try {
            throw new IllegalStateException("payment gateway rejected order " + orderId);
        } catch (RuntimeException e) {
            span.close(e);
            throw e;
        } finally {
            span.close();
        }
    }

    @Async
    public CompletableFuture<String> createOrderAsync(String orderId) {
        // 运行在线程池工作线程：上下文由 TraceTaskDecorator 传播，
        // 这里开启的子 span 与发起它的 HTTP 请求同属一条 trace。
        log.info("async creating order {}", orderId);
        try (ActiveSpan span = tracer.startSpan("order.async-processing")) {
            reserveInventory(orderId);
            return CompletableFuture.completedFuture("async-order-created:" + orderId);
        }
    }

    private void validate(String orderId) {
        try (ActiveSpan span = tracer.startSpan("order.validate")) {
            if (orderId == null || orderId.isBlank()) {
                throw new IllegalArgumentException("orderId must not be blank");
            }
        }
    }

    private void reserveInventory(String orderId) {
        try (ActiveSpan span = tracer.startSpan("inventory.reserve")) {
            log.debug("inventory reserved for {}", orderId);
        }
    }

    private void chargePayment(String orderId) {
        try (ActiveSpan span = tracer.startSpan("payment.charge")) {
            log.debug("payment charged for {}", orderId);
        }
    }
}
