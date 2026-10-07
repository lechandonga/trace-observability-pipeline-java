package com.github.lechandonga.traceobservabilitypipelinejava.trace;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * 基于 ThreadLocal 的链路上下文栈。支持快照/恢复，用于跨线程传递；
 * 恢复时整体替换当前线程的栈，避免线程复用时串上下文或残留。
 */
public final class TraceContext {

    public record CurrentSpan(String traceId, String spanId, boolean sampled) {}

    /** 不可变快照，用于跨线程传递。 */
    public record Snapshot(List<CurrentSpan> spans) {
        public static Snapshot empty() { return new Snapshot(List.of()); }
        public CurrentSpan top() { return spans.isEmpty() ? null : spans.get(spans.size() - 1); }
    }

    private static final ThreadLocal<Deque<CurrentSpan>> STACK = ThreadLocal.withInitial(ArrayDeque::new);

    private TraceContext() {}

    public static CurrentSpan current() {
        Deque<CurrentSpan> stack = STACK.get();
        return stack.isEmpty() ? null : stack.peek();
    }

    static void push(CurrentSpan span) {
        STACK.get().push(span);
    }

    static void pop() {
        Deque<CurrentSpan> stack = STACK.get();
        if (!stack.isEmpty()) {
            stack.pop();
        }
        if (stack.isEmpty()) {
            STACK.remove();
        }
    }

    /** 清空当前线程全部上下文，防止线程复用时残留。 */
    public static void clear() {
        STACK.remove();
    }

    public static Snapshot snapshot() {
        Deque<CurrentSpan> stack = STACK.get();
        if (stack.isEmpty()) {
            return Snapshot.empty();
        }
        List<CurrentSpan> copy = new ArrayList<>(stack);
        // Deque 迭代顺序为栈顶->栈底，翻转为底->顶便于恢复
        List<CurrentSpan> ordered = new ArrayList<>(copy.size());
        for (int i = copy.size() - 1; i >= 0; i--) {
            ordered.add(copy.get(i));
        }
        return new Snapshot(List.copyOf(ordered));
    }

    /** 用快照整体替换当前线程上下文（先清空，杜绝残留）。 */
    public static void restore(Snapshot snapshot) {
        STACK.remove();
        if (snapshot == null) {
            return;
        }
        Deque<CurrentSpan> stack = STACK.get();
        for (CurrentSpan span : snapshot.spans()) {
            stack.push(span);
        }
        if (stack.isEmpty()) {
            STACK.remove();
        }
    }
}
