package com.sfi.scaffold.starter.data.jpa.config;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 动态数据源配置项，前缀 {@code scaffold.datasource.dynamic}。
 *
 * <p>以 {@code @Bean + @ConfigurationProperties} 方式声明，IDE 依据返回类型校验配置键；
 * additional-spring-configuration-metadata.json 补充 {name} 占位说明。</p>
 *
 * @author lianghang
 */
public class DynamicDataSourceProperties {

    /**
     * 默认数据源名：无 {@code @TargetDataSource} 上下文时路由到这里，
     * 必须是 {@code datasources} 中的 key。
     */
    private String primary = "master";

    /**
     * 数据源名 -> 连接配置，至少配置两个（单库场景直接用 spring.datasource，无需本组件）。
     */
    private Map<String, Conf> datasources = new LinkedHashMap<>();

    public String getPrimary() {
        return primary;
    }

    public void setPrimary(String primary) {
        this.primary = primary;
    }

    public Map<String, Conf> getDatasources() {
        return datasources;
    }

    public void setDatasources(Map<String, Conf> datasources) {
        this.datasources = datasources;
    }

    /**
     * 单个数据源的连接配置，逐个透传给 {@link org.springframework.boot.jdbc.DataSourceBuilder}。
     */
    public static class Conf {

        private String url;

        private String username;

        private String password;

        private String driverClassName;

        /**
         * 数据库类型（国产库必填，主流库可省略）；
         * 缺省回落全局 {@code scaffold.database.db-type}，再缺省按 MySQL 系处理。
         * 消费方：JPA dialect 派生、Bean Searcher 方言桥接、doNativeQuery 分页。
         */
        private com.sfi.scaffold.common.database.DatabaseType dbType;

        /**
         * Liquibase 迁移配置占位（多库场景 {@code liquibase.change-log} 内联于数据源块）。
         * <p>键空间归 scaffold-starter-liquibase 消费——该 starter 启动时直接从 Environment
         * 绑定执行迁移；本字段仅作结构声明让 IDE 配置校验通过，data-jpa 不读取、不使用。</p>
         */
        private Liquibase liquibase = new Liquibase();

        public Liquibase getLiquibase() {
            return liquibase;
        }

        public void setLiquibase(Liquibase liquibase) {
            this.liquibase = liquibase;
        }

        /** Liquibase 内联配置键位声明，行为见 scaffold-starter-liquibase。 */
        public static class Liquibase {

            /** changelog 主文件位置（classpath:/file:），仅声明，消费方为 scaffold-starter-liquibase。 */
            private String changeLog;

            public String getChangeLog() {
                return changeLog;
            }

            public void setChangeLog(String changeLog) {
                this.changeLog = changeLog;
            }
        }

        public String getUrl() {
            return url;
        }

        public void setUrl(String url) {
            this.url = url;
        }

        public String getUsername() {
            return username;
        }

        public void setUsername(String username) {
            this.username = username;
        }

        public String getPassword() {
            return password;
        }

        public void setPassword(String password) {
            this.password = password;
        }

        public String getDriverClassName() {
            return driverClassName;
        }

        public void setDriverClassName(String driverClassName) {
            this.driverClassName = driverClassName;
        }

        public com.sfi.scaffold.common.database.DatabaseType getDbType() {
            return dbType;
        }

        public void setDbType(com.sfi.scaffold.common.database.DatabaseType dbType) {
            this.dbType = dbType;
        }
    }
}
