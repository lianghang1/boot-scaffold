package com.sfi.scaffold.starter.liquibase.database;

import liquibase.database.DatabaseConnection;
import liquibase.database.core.MySQLDatabase;
import liquibase.exception.DatabaseException;

/**
 * OceanBase（MySQL 模式）的 Liquibase Database 借壳实现，与脚手架 DatabaseType 的
 * OCEANBASE → MySQL 系折叠口径一致。
 */
public class OceanBaseDatabase extends MySQLDatabase {

    public OceanBaseDatabase() {
        super();
    }

    @Override
    public String getShortName() {
        return "oceanbase";
    }

    @Override
    public boolean isCorrectDatabaseImplementation(DatabaseConnection conn) throws DatabaseException {
        return conn.getDatabaseProductName().startsWith("OceanBase");
    }

    @Override
    public String getDefaultDriver(String url) {
        if (url != null && url.startsWith("jdbc:oceanbase:")) {
            return "com.oceanbase.jdbc.Driver";
        }
        return null;
    }
}
