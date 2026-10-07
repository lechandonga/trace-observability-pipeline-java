package com.github.lechandonga.tracepipe.tests;

import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 场景化日志：每个用例执行前后打印“场景 / 用例 / 结果”，
 * 让 mvn test 的控制台日志直接反映覆盖了哪些场景及每条用例的结果。
 */
public class ScenarioLoggingExtension implements BeforeEachCallback, AfterEachCallback {

    private static final Logger log = LoggerFactory.getLogger("SCENARIO");

    @Override
    public void beforeEach(ExtensionContext context) {
        log.info(">>> 场景[{}] 用例开始: {}", scenario(context), context.getDisplayName());
    }

    @Override
    public void afterEach(ExtensionContext context) {
        boolean failed = context.getExecutionException().isPresent();
        String result = failed ? "失败(FAIL)" : "通过(PASS)";
        log.info("<<< 场景[{}] 用例结束: {} => {}", scenario(context), context.getDisplayName(), result);
    }

    private String scenario(ExtensionContext context) {
        return context.getTestClass().map(Class::getSimpleName).orElse("unknown");
    }
}
