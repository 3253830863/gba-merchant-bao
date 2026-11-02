package com.gba.merchantbao.test;

import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.env.EnumerablePropertySource;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class RuntimeConfigurationTest {
    @Test
    void productionSeparatesMigrationCredentialsAndPackagesTheJarInJava21Image() throws Exception {
        var compose = new YamlPropertySourceLoader().load("compose", new FileSystemResource(
                "docs/prod-profile/opt/gba/docker-compose.yml")).getFirst();
        var dependency = new java.util.Properties();
        try (var metadata = new ClassPathResource("META-INF/maven/org.flywaydb/flyway-core/pom.properties").getInputStream()) {
            dependency.load(metadata);
        }
        assertThat(compose.getProperty("services.migrate.image"))
                .isEqualTo("flyway/flyway:" + dependency.getProperty("version"));
        assertThat(compose.getProperty("services.migrate.environment.FLYWAY_USER")).isEqualTo("flyway_deploy");
        assertThat(compose.getProperty("services.migrate.environment.FLYWAY_LOCATIONS"))
                .isEqualTo("filesystem:/flyway/sql/migration,filesystem:/flyway/sql/seed/common");
        assertThat(compose.getProperty("services.migrate.environment.FLYWAY_BASELINE_VERSION")).isEqualTo("1");
        for (String flag : new String[]{"BASELINE_ON_MIGRATE", "OUT_OF_ORDER"}) {
            assertThat(compose.getProperty("services.migrate.environment.FLYWAY_" + flag)).isEqualTo("false");
        }
        for (String flag : new String[]{"CLEAN_DISABLED", "VALIDATE_ON_MIGRATE"}) {
            assertThat(compose.getProperty("services.migrate.environment.FLYWAY_" + flag)).isEqualTo("true");
        }
        assertThat(compose.getProperty("services.app.environment.DB_USER")).isEqualTo("app");
        assertThat(compose.getProperty("services.app.environment.SPRING_FLYWAY_ENABLED")).isEqualTo("false");
        assertThat(compose.getProperty("services.app.environment.SPRING_PROFILES_ACTIVE")).isEqualTo("prod");
        assertThat(compose.getProperty("services.app.build.context")).isEqualTo("./app");
        String definition = Files.readString(Path.of("docs/prod-profile/opt/gba/app/Dockerfile"));
        assertThat(definition).contains("21-jre", "COPY app.jar /app/app.jar");
        assertThat(Files.readString(Path.of("docs/prod-profile/opt/gba/docker-compose.yml")))
                .doesNotContain("app.jar:");
        assertThat(((EnumerablePropertySource<?>) compose).getPropertyNames()).noneMatch(name -> name.startsWith("services.migrate.environment.")
                && (name.endsWith("APP_DB_PASSWORD") || name.endsWith("MYSQL_ROOT_PASSWORD") || name.endsWith("REDIS_PASSWORD")));
    }

    @Test
    void profilesEnforceTheMigrationAndSeedMatrix() throws Exception {
        var loader = new YamlPropertySourceLoader();
        var base = loader.load("base", new ClassPathResource("application.yml")).getFirst();
        var test = loader.load("test", new ClassPathResource("application-test.yml")).getFirst();
        var prod = loader.load("prod", new ClassPathResource("application-prod.yml")).getFirst();
        assertThat(base.getProperty("spring.flyway.baseline-version").toString()).isEqualTo("1");
        assertThat(base.getProperty("spring.flyway.baseline-on-migrate")).isEqualTo(false);
        assertThat(base.getProperty("spring.flyway.validate-on-migrate")).isEqualTo(true);
        assertThat(base.getProperty("spring.flyway.clean-disabled")).isEqualTo(true);
        assertThat(base.getProperty("spring.flyway.out-of-order")).isEqualTo(false);
        assertThat(test.getProperty("spring.flyway.locations").toString())
                .isEqualTo("classpath:db/migration,classpath:db/seed/common,classpath:db/seed/nonprod");
        assertThat(prod.getProperty("spring.flyway.enabled")).isEqualTo(false);
        assertThat(Files.exists(Path.of("src/main/resources/application-test.yml"))).isFalse();
        assertThat(new ClassPathResource("application-local.yml").exists()).isFalse();
        String dev = Files.readString(Path.of("src/main/resources/application-dev.yml.example"));
        assertThat(dev).contains("classpath:db/devdata", "classpath:db/seed/nonprod");
        assertThat(dev).doesNotContain("REDIS_PASSWORD", "requirepass");
    }
}
