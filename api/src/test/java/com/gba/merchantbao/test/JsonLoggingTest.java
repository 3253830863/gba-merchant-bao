package com.gba.merchantbao.test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gba.merchantbao.common.privacy.PhoneMasker;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.boot.logging.LoggingInitializationContext;
import org.springframework.boot.logging.LoggingSystem;
import org.springframework.mock.env.MockEnvironment;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class JsonLoggingTest {
    @Test
    void jsonFilesPreserveCorrelationAndKeepAuditSeparate() throws Exception {
        Path directory = Path.of(System.getProperty("LOG_DIR"), "json-probe");
        var system = LoggingSystem.get(getClass().getClassLoader());
        ((ch.qos.logback.classic.LoggerContext) LoggerFactory.getILoggerFactory()).reset();
        system.beforeInitialize();
        system.initialize(new LoggingInitializationContext(new MockEnvironment()
                .withProperty("logging.file.path", directory.toAbsolutePath().toString())),
                "classpath:logback-spring.xml", null);
        String marker = UUID.randomUUID().toString();
        try {
            MDC.put("requestId", marker);
            MDC.put("module", "foundation");
            MDC.put("errorCode", "10000");
            LoggerFactory.getLogger("com.gba.merchantbao.test.probe")
                    .info("phone={}", PhoneMasker.maskPhone("13800138000"));
            LoggerFactory.getLogger("com.gba.merchantbao.legacy.audit")
                    .info("audit-probe {}", marker);
            var mapper = new ObjectMapper();
            String application = Files.readString(directory.resolve("application.json"));
            var event = application.lines().map(line -> {
                try { return mapper.readTree(line); }
                catch (Exception error) { throw new AssertionError("Invalid JSON log", error); }
            }).filter(node -> marker.equals(node.path("requestId").asText())).findFirst().orElseThrow();
            assertThat(event.path("module").asText()).isEqualTo("foundation");
            assertThat(event.path("errorCode").asText()).isEqualTo("10000");
            assertThat(event.path("message").asText()).contains("138****8000").doesNotContain("13800138000");
            assertThat(application).doesNotContain("audit-probe " + marker);
            assertThat(Files.readString(directory.resolve("audit.json"))).contains(marker);
            String config = Files.readString(Path.of("src/main/resources/logback-spring.xml"));
            assertThat(config).contains("SizeAndTimeBasedRollingPolicy", "%i.json.gz", "100MB",
                    "<maxHistory>7</maxHistory>", "<maxHistory>30</maxHistory>",
                    "<totalSizeCap>2GB</totalSizeCap>", "<totalSizeCap>1GB</totalSizeCap>");
            assertThat(config).doesNotContain("error.json");
        } finally {
            MDC.clear();
            system.cleanUp();
            ((ch.qos.logback.classic.LoggerContext) LoggerFactory.getILoggerFactory()).reset();
            system.beforeInitialize();
            system.initialize(new LoggingInitializationContext(new MockEnvironment()
                    .withProperty("logging.file.path", System.getProperty("LOG_DIR"))),
                    "classpath:logback-spring.xml", null);
        }
    }
}
