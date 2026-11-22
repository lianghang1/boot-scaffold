package com.sfi.scaffold.query;

import cn.zhxu.bs.bean.DbField;
import cn.zhxu.bs.bean.SearchBean;
import com.sfi.scaffold.query.annotation.IncludeDeleted;
import com.sfi.scaffold.query.core.IncludeDeletedHolder;
import com.sfi.scaffold.query.filter.OperatorX;
import com.sfi.scaffold.query.filter.QueryFilterX;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 逻辑删除自动过滤：有 deleted 字段的检索类注入 deleted=false、前端不可篡改；
 * 无 deleted 字段的检索类放行；@IncludeDeleted 豁免期放行且可嵌套。
 *
 * @author lianghang
 */
@SpringBootTest(classes = TestApp.class, properties = {
        "spring.datasource.url=jdbc:h2:mem:deletedfilterdb;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password="
})
class DeletedParamFilterTest {

    /** 带 deleted 字段的检索类：应被自动注入 deleted=false */
    @SearchBean(tables = "t_doc")
    public static class DocVO {
        @DbField("id")
        private Long id;
        @DbField("title")
        private String title;
        @DbField("deleted")
        private Boolean deleted;

        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }
        public String getTitle() { return title; }
        public void setTitle(String title) { this.title = title; }
        public Boolean getDeleted() { return deleted; }
        public void setDeleted(Boolean deleted) { this.deleted = deleted; }
    }

    /** 无 deleted 字段的检索类：不应被注入任何条件 */
    @SearchBean(tables = "t_doc")
    public static class DocNoDeletedVO {
        @DbField("id")
        private Long id;
        @DbField("title")
        private String title;
        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }
        public String getTitle() { return title; }
        public void setTitle(String title) { this.title = title; }
    }

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeAll
    static void createTables(@Autowired JdbcTemplate jdbc) {
        jdbc.execute("CREATE TABLE IF NOT EXISTS t_doc (" +
                "id BIGINT PRIMARY KEY, title VARCHAR(100), deleted BOOLEAN NOT NULL DEFAULT FALSE)");
    }

    @BeforeEach
    void cleanAndInsert() {
        jdbcTemplate.update("DELETE FROM t_doc");
        jdbcTemplate.update("INSERT INTO t_doc VALUES (1,'存活A',FALSE),(2,'存活B',FALSE),(3,'已删C',TRUE)");
        IncludeDeletedHolder.clear();
    }

    /** 有 deleted 字段：默认只查到存活记录 */
    @Test
    void shouldFilterLogicalDeletedAutomatically() {
        List<DocVO> list = QueryUtilsX.doListQuery(Collections.emptyList(), DocVO.class);
        assertEquals(2, list.size());
        assertTrue(list.stream().allMatch(d -> Boolean.FALSE.equals(d.getDeleted())));
    }

    /** 前端传 deleted=true 想捞已删除：被固定条件覆盖，仍只剩存活记录 */
    @Test
    void frontEndDeletedParamShouldBeOverridden() {
        List<QueryFilterX> filters = new ArrayList<>();
        filters.add(new QueryFilterX("deleted", OperatorX.EQ, true));
        List<DocVO> list = QueryUtilsX.doListQuery(filters, DocVO.class);
        assertEquals(2, list.size());
        assertTrue(list.stream().allMatch(d -> Boolean.FALSE.equals(d.getDeleted())));
    }

    /** 无 deleted 字段的检索类：三条记录都返回，不被注入 */
    @Test
    void classWithoutDeletedFieldShouldPassThrough() {
        List<DocNoDeletedVO> list = QueryUtilsX.doListQuery(Collections.emptyList(), DocNoDeletedVO.class);
        assertEquals(3, list.size());
    }

    /** @IncludeDeleted 豁免：标记期间查到全部（含已删除），退出后恢复过滤 */
    @Test
    void includeDeletedShouldBypassFilter() {
        annotatedIncludeDeleted(() -> {
            List<DocVO> list = QueryUtilsX.doListQuery(Collections.emptyList(), DocVO.class);
            assertEquals(3, list.size(), "豁免期应含已删除");
        });
        // 退出后恢复
        List<DocVO> after = QueryUtilsX.doListQuery(Collections.emptyList(), DocVO.class);
        assertEquals(2, after.size());
    }

    /** 嵌套豁免：内层退出只减一层，外层仍生效 */
    @Test
    void nestedIncludeDeletedShouldKeepOuterActive() {
        annotatedIncludeDeleted(() -> {
            annotatedIncludeDeleted(() -> {
                assertEquals(3, QueryUtilsX.doListQuery(Collections.emptyList(), DocVO.class).size());
            });
            assertEquals(3, QueryUtilsX.doListQuery(Collections.emptyList(), DocVO.class).size(), "内层退出后外层仍生效");
        });
        assertEquals(2, QueryUtilsX.doListQuery(Collections.emptyList(), DocVO.class).size());
    }

    /** 直接操作 Holder 模拟切面 mark/clear（切面本身在 AOP 代理下生效，此处验证过滤器读标记） */
    @IncludeDeleted
    private void annotatedIncludeDeleted(Runnable body) {
        IncludeDeletedHolder.mark();
        try {
            body.run();
        } finally {
            IncludeDeletedHolder.clear();
        }
    }
}
