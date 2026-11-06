package com.gba.merchantbao.auth.config;

import org.flywaydb.core.api.callback.*;
import org.springframework.boot.autoconfigure.flyway.FlywayProperties;
import org.springframework.context.annotation.*;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;

/** The auth repeatable sorts before local_test_tenants on an empty dev database.
 * Reapply its insert-only SQL after all migrations, preserving existing migration names/history. */
@Configuration(proxyBeanMethods=false)
@Profile("dev & !prod")
public class AuthDevDataConfiguration {
    @Bean Callback authDemoAfterMigrate(FlywayProperties properties) {
        return new Callback() {
            public boolean supports(Event event,Context context) {
                return event==Event.AFTER_MIGRATE && properties.getLocations().contains("classpath:db/devdata");
            }
            public boolean canHandleInTransaction(Event event,Context context) {return true;}
            public void handle(Event event,Context context) {
                ScriptUtils.executeSqlScript(context.getConnection(),new ClassPathResource("db/devdata/R__auth_demo_accounts.sql"));
            }
            public String getCallbackName(){return "authDemoAfterMigrate";}
        };
    }
}
