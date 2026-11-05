package com.sfi.scaffold.starter.liquibase.database;

import liquibase.database.DatabaseConnection;
import liquibase.exception.DatabaseException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** 三个自定义 Liquibase Database 的识别规则：仅认国产库产品名/URL 前缀，不抢占主流库。 */
class DatabaseMatchTest {

    private static DatabaseConnection conn(String productName) throws DatabaseException {
        DatabaseConnection conn = mock(DatabaseConnection.class);
        when(conn.getDatabaseProductName()).thenReturn(productName);
        return conn;
    }

    // ---- KingbaseDatabase ----

    @Test
    void kingbaseMatchesKingbaseESProductName() throws Exception {
        assertThat(new KingbaseDatabase().isCorrectDatabaseImplementation(conn("KingbaseES V009R001")))
                .isTrue();
        assertThat(new KingbaseDatabase().isCorrectDatabaseImplementation(conn("PostgreSQL")))
                .isFalse();
        assertThat(new KingbaseDatabase().isCorrectDatabaseImplementation(conn("MySQL")))
                .isFalse();
    }

    @Test
    void kingbaseDriverOnlyForKingbase8Url() {
        KingbaseDatabase db = new KingbaseDatabase();
        assertThat(db.getDefaultDriver("jdbc:kingbase8://127.0.0.1:54321/demo"))
                .isEqualTo("com.kingbase8.Driver");
        assertThat(db.getDefaultDriver("jdbc:postgresql://127.0.0.1:5432/demo")).isNull();
        assertThat(db.getDefaultDriver(null)).isNull();
        assertThat(db.getShortName()).isEqualTo("kingbase8");
    }

    // ---- OpenGaussDatabase ----

    @Test
    void openGaussMatchesProductName() throws Exception {
        assertThat(new OpenGaussDatabase().isCorrectDatabaseImplementation(conn("openGauss"))).isTrue();
        assertThat(new OpenGaussDatabase().isCorrectDatabaseImplementation(conn("PostgreSQL"))).isFalse();
    }

    @Test
    void openGaussDriverOnlyForOpenGaussUrl() {
        OpenGaussDatabase db = new OpenGaussDatabase();
        assertThat(db.getDefaultDriver("jdbc:opengauss://127.0.0.1:5432/db"))
                .isEqualTo("org.opengauss.Driver");
        assertThat(db.getDefaultDriver("jdbc:postgresql://db")).isNull();
        assertThat(db.getShortName()).isEqualTo("opengauss");
    }

    // ---- OceanBaseDatabase ----

    @Test
    void oceanBaseMatchesProductName() throws Exception {
        assertThat(new OceanBaseDatabase().isCorrectDatabaseImplementation(conn("OceanBase"))).isTrue();
        assertThat(new OceanBaseDatabase().isCorrectDatabaseImplementation(conn("MySQL"))).isFalse();
    }

    @Test
    void oceanBaseDriverOnlyForOceanBaseUrl() {
        OceanBaseDatabase db = new OceanBaseDatabase();
        assertThat(db.getDefaultDriver("jdbc:oceanbase://127.0.0.1:2881/db"))
                .isEqualTo("com.oceanbase.jdbc.Driver");
        assertThat(db.getDefaultDriver("jdbc:mysql://db")).isNull();
        assertThat(db.getShortName()).isEqualTo("oceanbase");
    }
}
