package com.sfi.scaffold.query.config;

import com.sfi.scaffold.common.database.DatabaseType;

/**
 * doNativeQuery 分页片段生成：按 {@link DatabaseType#paginationGroup()} 拼
 * LIMIT 组（MySQL/PG/金仓/openGauss/OceanBase）或 FETCH 组（Oracle/SQL Server/达梦）。
 *
 * <p>命名参数 {@code :__limit} / {@code :__offset} 由调用方（NamedParameterJdbcTemplate）绑定。</p>
 *
 * <p>注意：FETCH 组在 SQL Server 2012 下要求语句带 ORDER BY（达梦 / Oracle 无此限制），
 * 手写 SQL 命中 SQL Server 时请保证含排序子句。</p>
 *
 * @author lianghang
 */
public final class PaginationDialect {

    private PaginationDialect() {
    }

    /** 追加分页片段：LIMIT 组 {@code LIMIT :__limit OFFSET :__offset}；FETCH 组 {@code OFFSET :__offset ROWS FETCH NEXT :__limit ROWS ONLY}。 */
    public static String paginate(DatabaseType type, String sql) {
        if (type.paginationGroup() == DatabaseType.PaginationGroup.FETCH) {
            return sql + " OFFSET :__offset ROWS FETCH NEXT :__limit ROWS ONLY";
        }
        return sql + " LIMIT :__limit OFFSET :__offset";
    }

    /** 追加行数上限片段（非分页保护，多取一行由调用方判断超限）。 */
    public static String cap(DatabaseType type, String sql) {
        if (type.paginationGroup() == DatabaseType.PaginationGroup.FETCH) {
            return sql + " FETCH FIRST :__limit ROWS ONLY";
        }
        return sql + " LIMIT :__limit";
    }
}
