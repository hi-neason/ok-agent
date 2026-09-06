package io.okagent.config;

import static org.assertj.core.api.Assertions.*;
import java.sql.DriverManager;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;

/** Explicit integration gate against a disposable local MySQL service, never the application database. */
class MySqlMigrationIT {
    @Test void migratesAnEmptyDatabase() throws Exception { verifyMigration(false); }
    @Test void upgradesVersion53AndPreservesExistingAccounts() throws Exception { verifyMigration(true); }

    private void verifyMigration(boolean upgrade) throws Exception {
        String base = System.getenv("OK_AGENT_MIGRATION_TEST_URL");
        assertThat(base).isNotNull().matches("jdbc:mysql://127\\.0\\.0\\.1:[0-9]+/");
        String password = System.getenv("OK_AGENT_MIGRATION_TEST_PASSWORD");
        assertThat(password).isNotBlank();
        String schema = "ok_agent_migration_test_" + UUID.randomUUID().toString().replace("-", "");
        try (var connection = DriverManager.getConnection(base + "?connectTimeout=5000", "root", password);
                var statement = connection.createStatement()) {
            statement.execute("CREATE DATABASE " + schema);
            try {
                String url = base + schema;
                if (upgrade) {
                    Flyway.configure().dataSource(url, "root", password).locations("classpath:db/migration")
                            .target("53").load().migrate();
                    statement.execute("INSERT INTO " + schema + ".app_user "
                            + "(id,user_id,username,display_name,enabled,created_at,updated_at) "
                            + "VALUES (UNHEX(REPLACE(UUID(),'-','')),'migration-test-user','migration-test-user','Synthetic',true,NOW(),NOW())");
                }
                var flyway = Flyway.configure().dataSource(url, "root", password)
                        .locations("classpath:db/migration").load();
                flyway.migrate();
                flyway.validate();
                assertThat(flyway.info().pending()).isEmpty();
                try (var columns = statement.executeQuery("SELECT security_version FROM " + schema + ".app_user WHERE user_id='migration-test-user'")) {
                    if (upgrade) { assertThat(columns.next()).isTrue(); assertThat(columns.getLong(1)).isZero(); }
                }
            } finally {
                statement.execute("DROP DATABASE " + schema);
            }
        }
    }
}
