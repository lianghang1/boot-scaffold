package com.sfi.scaffold.starter.tenant.bs;

import com.sfi.scaffold.starter.security.identity.LoginContext;
import com.sfi.scaffold.starter.security.identity.LoginUser;
import com.sfi.scaffold.starter.tenant.annotation.TenantIgnore;
import com.sfi.scaffold.query.plan.annotation.ColumnX;
import com.sfi.scaffold.query.plan.annotation.TableX;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Bean Searcher 通路集成测试（H2）：计划类含 tenantId 字段时检索自动注入租户条件、
 * 平台视角放行、@TenantIgnore 计划类豁免、ignore-tables 豁免名单生效。
 *
 * @author lianghang
 */
@SpringBootTest(classes = BsTenantFilterTest.TestApp.class, properties = {
        "scaffold.tenant.enabled=true",
        "scaffold.tenant.ignore-tables[0]=TBL_TENANT_IGNORE"})
class BsTenantFilterTest {

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Autowired
    cn.zhxu.bs.MapSearcher mapSearcher;

    @BeforeEach
    void setUp() {
        jdbcTemplate.execute("DROP TABLE IF EXISTS TBL_TENANT_ORDER");
        jdbcTemplate.execute("CREATE TABLE TBL_TENANT_ORDER (id BIGINT PRIMARY KEY, tenant_id VARCHAR(64), amount BIGINT)");
        jdbcTemplate.execute("DROP TABLE IF EXISTS TBL_TENANT_IGNORE");
        jdbcTemplate.execute("CREATE TABLE TBL_TENANT_IGNORE (id BIGINT PRIMARY KEY, tenant_id VARCHAR(64), name VARCHAR(64))");
        seed();
    }

    @AfterEach
    void tearDown() {
        LoginContext.clear();
    }

    private void seed() {
        jdbcTemplate.update("INSERT INTO TBL_TENANT_ORDER VALUES (1, 't1', 100)");
        jdbcTemplate.update("INSERT INTO TBL_TENANT_ORDER VALUES (2, 't1', 200)");
        jdbcTemplate.update("INSERT INTO TBL_TENANT_ORDER VALUES (3, 't2', 300)");
        jdbcTemplate.update("INSERT INTO TBL_TENANT_IGNORE VALUES (1, 't1', '字典A')");
        jdbcTemplate.update("INSERT INTO TBL_TENANT_IGNORE VALUES (2, 't2', '字典B')");
    }

    @Test
    void planSearchShouldInjectTenantCondition() {
        login("t1");
        cn.zhxu.bs.SearchResult<Map<String, Object>> result = mapSearcher.search(TenantOrderPlan.class, new HashMap<>());
        assertThat(result.getTotalCount().longValue()).isEqualTo(2);
        assertThat(result.getDataList())
                .extracting(r -> r.get("tenantId"))
                .containsOnly("t1");

        login("t2");
        assertThat(mapSearcher.search(TenantOrderPlan.class, new HashMap<>()).getTotalCount().longValue()).isEqualTo(1);
    }

    @Test
    void platformViewShouldSeeAllRows() {
        assertThat(mapSearcher.search(TenantOrderPlan.class, new HashMap<>()).getTotalCount().longValue()).isEqualTo(3);
    }

    @Test
    void tenantIgnorePlanClassShouldBypassInjection() {
        login("t1");
        // 计划类标注 @TenantIgnore：不注入租户条件
        assertThat(mapSearcher.search(IgnoredOrderPlan.class, new HashMap<>()).getTotalCount().longValue()).isEqualTo(3);
    }

    @Test
    void ignoreTablesConfigShouldBypassInjection() {
        login("t1");
        // 计划类主表命中 scaffold.tenant.ignore-tables：不注入
        assertThat(mapSearcher.search(IgnoredDictPlan.class, new HashMap<>()).getTotalCount().longValue()).isEqualTo(2);
    }

    private void login(String tenantId) {
        LoginUser user = new LoginUser();
        user.setAccountId(1L);
        user.setTenantId(tenantId);
        LoginContext.set(user);
    }

    @SpringBootApplication
    static class TestApp {
    }

    @TableX(name = "TBL_TENANT_ORDER", alias = "o")
    static class TenantOrderPlan {
        @ColumnX("o.id")
        private Long id;
        @ColumnX("o.tenant_id")
        private String tenantId;
        @ColumnX("o.amount")
        private Long amount;

        public Long getId() {
            return id;
        }

        public String getTenantId() {
            return tenantId;
        }

        public Long getAmount() {
            return amount;
        }
    }

    @TenantIgnore
    @TableX(name = "TBL_TENANT_ORDER", alias = "o")
    static class IgnoredOrderPlan {
        @ColumnX("o.id")
        private Long id;
        @ColumnX("o.tenant_id")
        private String tenantId;

        public Long getId() {
            return id;
        }
    }

    @TableX(name = "TBL_TENANT_IGNORE", alias = "d")
    static class IgnoredDictPlan {
        @ColumnX("d.id")
        private Long id;
        @ColumnX("d.tenant_id")
        private String tenantId;
        @ColumnX("d.name")
        private String name;

        public Long getId() {
            return id;
        }

        public String getName() {
            return name;
        }
    }
}
