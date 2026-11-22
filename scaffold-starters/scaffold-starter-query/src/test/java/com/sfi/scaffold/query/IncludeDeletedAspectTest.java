package com.sfi.scaffold.query;

import cn.zhxu.bs.bean.DbField;
import cn.zhxu.bs.bean.SearchBean;
import com.sfi.scaffold.query.annotation.IncludeDeleted;
import com.sfi.scaffold.query.core.IncludeDeletedHolder;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 验证 @IncludeDeleted 切面在真实 Spring AOP 代理下生效——方法进入标记豁免、
 * 返回清理，query 过滤读到标记后放行已删除数据；退出后恢复过滤。
 *
 * @author lianghang
 */
@SpringBootTest(classes = IncludeDeletedAspectTest.TestApp.class,
        properties = {
                "spring.datasource.url=jdbc:h2:mem:incdelaspectdb;DB_CLOSE_DELAY=-1",
                "spring.datasource.driver-class-name=org.h2.Driver",
                "spring.datasource.username=sa",
                "spring.datasource.password="
        })
class IncludeDeletedAspectTest {

    @SearchBean(tables = "t_adoc")
    public static class ADoc {
        @DbField("id")
        private Long id;
        @DbField("deleted")
        private Boolean deleted;
        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }
        public Boolean getDeleted() { return deleted; }
        public void setDeleted(Boolean deleted) { this.deleted = deleted; }
    }

    @Service
    public static class DocQueryService {
        public List<ADoc> normal() {
            return QueryUtilsX.doListQuery(Collections.emptyList(), ADoc.class);
        }

        @IncludeDeleted
        public List<ADoc> includingDeleted() {
            // 完全依赖切面标记——进入方法时应已 marked
            assertTrue(IncludeDeletedHolder.isMarked(), "切面应在方法进入前 mark 豁免标记");
            return QueryUtilsX.doListQuery(Collections.emptyList(), ADoc.class);
        }
    }

    @SpringBootApplication
    static class TestApp {
        @Bean
        DocQueryService docQueryService() {
            return new DocQueryService();
        }
    }

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Autowired
    DocQueryService docQueryService;

    @BeforeAll
    static void createTables(@Autowired JdbcTemplate jdbc) {
        jdbc.execute("CREATE TABLE IF NOT EXISTS t_adoc (" +
                "id BIGINT PRIMARY KEY, deleted BOOLEAN NOT NULL DEFAULT FALSE)");
    }

    @BeforeEach
    void cleanAndInsert() {
        jdbcTemplate.update("DELETE FROM t_adoc");
        jdbcTemplate.update("INSERT INTO t_adoc VALUES (1,FALSE),(2,TRUE)");
    }

    @Test
    void aspectShouldToggleFilter() {
        assertEquals(1, docQueryService.normal().size(), "普通方法应过滤已删除");
        assertEquals(2, docQueryService.includingDeleted().size(), "@IncludeDeleted 方法应含已删除");
        assertEquals(1, docQueryService.normal().size(), "切面退出后应恢复过滤");
        assertFalse(IncludeDeletedHolder.isMarked());
    }
}
