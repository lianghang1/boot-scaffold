package com.sfi.scaffold.starter.liquibase.database;

import liquibase.database.DatabaseConnection;
import liquibase.database.core.PostgresDatabase;
import liquibase.exception.DatabaseException;

/**
 * 金仓 KingbaseES 的 Liquibase Database 借壳实现（业界路径①：金仓官方对其它框架的适配建议即配置 PG 方言）。
 *
 * <p>独立 {@link #getShortName()} 分组，避免与官方 PostgreSQLDatabase 抢同组组首；
 * 仅当 JDBC 元数据报告 KingbaseES 时命中，对主流库零干扰。
 */
public class KingbaseDatabase extends PostgresDatabase {

    public KingbaseDatabase() {
        super();
    }

    @Override
    public String getShortName() {
        return "kingbase8";
    }

    @Override
    public boolean isCorrectDatabaseImplementation(DatabaseConnection conn) throws DatabaseException {
        return conn.getDatabaseProductName().startsWith("KingbaseES");
    }

    @Override
    public String getDefaultDriver(String url) {
        if (url != null && url.startsWith("jdbc:kingbase8:")) {
            return "com.kingbase8.Driver";
        }
        return null;
    }
}
