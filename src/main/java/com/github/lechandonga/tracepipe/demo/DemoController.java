package com.github.lechandonga.tracepipe.demo;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

@RestController
@RequestMapping("/demo")
public class DemoController {

    private final OrderService orderService;

    public DemoController(OrderService orderService) {
        this.orderService = orderService;
    }

    @PostMapping("/orders/{orderId}")
    public Map<String, String> create(@PathVariable String orderId) {
        return Map.of("result", orderService.createOrder(orderId));
    }

    @PostMapping("/orders/{orderId}/fail")
    public Map<String, String> fail(@PathVariable String orderId) {
        return Map.of("result", orderService.createFailingOrder(orderId));
    }

    @PostMapping("/orders/{orderId}/async")
    public CompletableFuture<Map<String, String>> createAsync(@PathVariable String orderId) {
        return orderService.createOrderAsync(orderId)
                .thenApply(result -> Map.of("result", result));
    }
}
