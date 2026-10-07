package com.github.lechandonga.traceobservabilitypipelinejava.tests.trace;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 测试场景日志辅助：让测试输出能看出覆盖了哪些场景、每个用例与结果。
 */
final class ScenarioLog {

    private static final Logger log = LoggerFactory.getLogger(ScenarioLog.class);

    private ScenarioLog() {}

    static void scenario(String name) {
        log.info("[SCENARIO] {}", name);
    }

    static void pass(String caseName) {
        log.info("[PASS] {}", caseName);
    }
}
