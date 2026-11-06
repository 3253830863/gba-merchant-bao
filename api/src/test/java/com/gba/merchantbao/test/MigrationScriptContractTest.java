package com.gba.merchantbao.test;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class MigrationScriptContractTest {

    @Test
    void authMigrationIsDdlOnlyAndAuthFixturesAreNotFlywaySeeds() throws IOException {
        String migration = resource("db/migration/V4__auth_accounts.sql");
        assertThat(migration).doesNotContainPattern("(?im)^\\s*(INSERT|UPDATE|DELETE|REPLACE|TRUNCATE|DROP)\\b");
        assertThat(migration).contains("CREATE TABLE `consumer_account`", "CREATE TABLE `merchant_staff_account`");
        try (var files = java.nio.file.Files.walk(java.nio.file.Path.of("src/main/resources/db/seed"))) {
            for (var path : files.filter(path -> path.toString().endsWith(".sql")).toList()) {
                assertThat(java.nio.file.Files.readString(path)).doesNotContain("consumer_account", "merchant_staff_account", "fixture_staff");
            }
        }
        String fixture = resource("db/fixture/auth_accounts.sql");
        assertThat(fixture).contains("merchant_code = 'M_3C_DEMO'", "merchant_code = 'M_ISOLATION_TEST'", "$2b$12$");
        assertThat(resource("application-test.yml")).doesNotContain("db/fixture", "db/devdata");
        assertThat(resource("application-prod.yml")).doesNotContain("db/fixture", "db/seed/nonprod", "db/devdata");
    }

    @Test
    void legacyBaselineContainsSchemaOnlyStatements() throws IOException {
        String baseline = resource("db/migration/V1__legacy_schema_baseline.sql");
        String executableSql = baseline.lines()
                .filter(line -> !line.trim().startsWith("--"))
                .reduce("", (left, right) -> left + right + "\n");

        assertThat(executableSql).doesNotMatch("(?is).*\\b(drop|insert|update|delete)\\b.*");
        assertThat(executableSql).contains("CREATE TABLE `address_book`");
        assertThat(executableSql).contains("CREATE TABLE `user`");
    }

    @Test
    void tenantFixturesAreIdempotentAndProductionDoesNotLoadThem() throws IOException {
        String seed = resource("db/seed/nonprod/R__010_local_test_tenants.sql");
        assertThat(seed).contains("M_3C_DEMO", "M_ISOLATION_TEST", "WHERE NOT EXISTS");
        assertThat(resource("application-prod.yml")).doesNotContain("db/devdata");
        assertThat(resource("application-test.yml")).contains("classpath:db/migration,classpath:db/seed/common,classpath:db/seed/nonprod");
    }

    private String resource(String path) throws IOException {
        try (InputStream stream = getClass().getClassLoader().getResourceAsStream(path)) {
            assertThat(stream).as("resource %s", path).isNotNull();
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
