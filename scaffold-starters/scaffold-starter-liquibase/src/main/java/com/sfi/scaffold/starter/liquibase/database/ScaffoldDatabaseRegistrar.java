package com.sfi.scaffold.starter.liquibase.database;

import com.sfi.scaffold.common.database.DatabaseType;
import com.sfi.scaffold.starter.liquibase.config.LiquibaseDatabaseDeclarations;
import liquibase.database.Database;
import liquibase.database.DatabaseFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.env.Environment;

import java.util.Set;

/**
 * 按脚手架的数据库类型声明，向 Liquibase {@link DatabaseFactory} 按需注册自定义 Database：
 *
 * <ul>
 *   <li>KINGBASE / GAUSS / OCEANBASE：注册借壳实现（金仓官方对第三方框架的适配建议即 PG 方言；
 *       OceanBase 与脚手架 OCEANBASE → MySQL 系折叠口径一致）</li>
 *   <li>DAMENG：不内置实现，检查 classpath 是否已有达梦扩展（如 com.github.mengweijin:db-migration-dm，
 *       Liquibase 扩展随 classpath 自动注册进 factory）；缺失则 WARN 提示——Liquibase 对未识别库
 *       会 fallback 到 UnsupportedDatabase，后续迁移必然出错，提前提示比启动报错更友好</li>
 *   <li>主流四库（MYSQL/POSTGRESQL/ORACLE/SQLSERVER）：官方 Database 自识别，零干预</li>
 * </ul>
 *
 * <p>只在声明了对应类型时注册；自定义实现按独立 shortName 分组且只认国产库连接，
 * 对主流库的官方自识别零干扰。
 */
public final class ScaffoldDatabaseRegistrar {

    private static final Logger log = LoggerFactory.getLogger(ScaffoldDatabaseRegistrar.class);

    private ScaffoldDatabaseRegistrar() {
    }

    /** 按 Environment 中的类型声明注册（幂等：同类已注册则跳过）。 */
    public static synchronized void registerForDeclarations(Environment env) {
        Set<DatabaseType> declared = LiquibaseDatabaseDeclarations.declaredTypes(env);
        for (DatabaseType type : declared) {
            switch (type) {
                case KINGBASE:
                    registerOnce(new KingbaseDatabase());
                    break;
                case GAUSS:
                    registerOnce(new OpenGaussDatabase());
                    break;
                case OCEANBASE:
                    registerOnce(new OceanBaseDatabase());
                    break;
                case DAMENG:
                    warnForDameng();
                    break;
                default:
                    // 主流库官方自识别，零干预
                    break;
            }
        }
    }

    private static void registerOnce(Database database) {
        DatabaseFactory factory = DatabaseFactory.getInstance();
        for (Database existing : factory.getImplementedDatabases()) {
            if (existing.getClass() == database.getClass()) {
                return;
            }
        }
        factory.register(database);
        log.info("scaffold-starter-liquibase: 已注册 Liquibase Database [{}]（shortName={}）",
                database.getClass().getSimpleName(), database.getShortName());
    }

    /**
     * 达梦不受 Liquibase 内置支持：若 classpath 有达梦扩展（如 com.github.mengweijin:db-migration-dm，
     * Liquibase 扩展随 classpath 自动注册进 factory）则直接可用；否则 Liquibase 会 fallback 到
     * UnsupportedDatabase，迁移无法正确执行。无法在启动期可靠探测扩展是否在场，故声明 DAMENG 即提示。
     */
    private static void warnForDameng() {
        log.warn("scaffold-starter-liquibase: 声明了 DAMENG。达梦不受 Liquibase 内置支持，请确认已引入达梦扩展"
                + "（如 com.github.mengweijin:db-migration-dm），否则 Liquibase 将 fallback 到 UnsupportedDatabase，"
                + "迁移无法正确执行；也可改用显式 SQL 脚本管理达梦 schema");
    }
}
