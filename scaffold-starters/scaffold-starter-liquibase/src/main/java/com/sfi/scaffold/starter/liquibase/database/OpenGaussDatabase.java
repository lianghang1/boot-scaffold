package com.sfi.scaffold.starter.liquibase.database;

import liquibase.database.DatabaseConnection;
import liquibase.database.core.PostgresDatabase;
import liquibase.exception.DatabaseException;

/**
 * openGauss 的 Liquibase Database 借壳实现（openGauss 为 PG 内核，PG 方言直接复用）。
 */
public class OpenGaussDatabase extends PostgresDatabase {

    public OpenGaussDatabase() {
        super();
    }

    @Override
    public String getShortName() {
        return "opengauss";
    }

    @Override
    public boolean isCorrectDatabaseImplementation(DatabaseConnection conn) throws DatabaseException {
        return conn.getDatabaseProductName().startsWith("openGauss");
    }

    @Override
    public String getDefaultDriver(String url) {
        if (url != null && url.startsWith("jdbc:opengauss:")) {
            return "org.opengauss.Driver";
        }
        return null;
    }
}
