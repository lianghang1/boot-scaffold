package com.sfi.scaffold.query;

import com.sfi.scaffold.common.exception.BizException;
import com.sfi.scaffold.query.filter.OperatorX;
import com.sfi.scaffold.query.filter.QueryFilterX;
import com.sfi.scaffold.query.planpck.UserVO;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(classes = {TestApp.class, QueryGuardTest.InterceptorConfig.class}, properties = {
        "spring.datasource.url=jdbc:h2:mem:queryguard;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "scaffold.query.plan-packages=com.sfi.scaffold.query.planpck",
        "scaffold.query.max-rows=3",
        "scaffold.query.max-page-size=2",
        "scaffold.query.slow-sql-millis=0"
})
class QueryGuardTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    static final List<String> BEFORE = new CopyOnWriteArrayList<>();
    static final List<String> AFTER = new CopyOnWriteArrayList<>();

    @org.springframework.context.annotation.Configuration
    static class InterceptorConfig {
        @org.springframework.context.annotation.Bean
        QueryInterceptor recordingInterceptor() {
            return new QueryInterceptor() {
                @Override
                public void before(QueryMeta meta) {
                    BEFORE.add(meta.getOp() + ":" + meta.getBeanClassName());
                }

                @Override
                public void after(QueryMeta meta, long elapsedMillis, long rowCount) {
                    AFTER.add(meta.getOp() + ":" + elapsedMillis + ":" + rowCount);
                }
            };
        }
    }

    @BeforeAll
    static void createTables(@Autowired JdbcTemplate jdbcTemplate) {
        jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS t_user (" +
                "id BIGINT PRIMARY KEY, name VARCHAR(100), age INT, status INT, dept_id BIGINT, deleted_at TIMESTAMP NULL)");
        jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS t_dept (" +
                "id BIGINT PRIMARY KEY, dept_name VARCHAR(100))");
    }

    @BeforeEach
    void cleanAndInsert() {
        jdbcTemplate.update("DELETE FROM t_user");
        jdbcTemplate.update("DELETE FROM t_dept");
        jdbcTemplate.update("INSERT INTO t_dept VALUES (1, '研发部'), (2, '市场部')");
        jdbcTemplate.update("INSERT INTO t_user VALUES " +
                "(1, '张三', 25, 1, 1, NULL)," +
                "(2, '张四', 30, 1, 1, NULL)," +
                "(3, '李五', 35, 0, 2, NULL)," +
                "(4, '王六', 28, 1, 2, NULL)");
    }

    private List<QueryFilterX> filters(QueryFilterX... items) {
        return new ArrayList<>(Arrays.asList(items));
    }

    @Test
    void 未声明字段被拒绝() {
        BizException e = assertThrows(BizException.class, () ->
                QueryUtilsX.doListQuery(filters(new QueryFilterX("hacker_field", OperatorX.EQ, "x")), UserVO.class));
        assertTrue(e.getMessage().contains("hacker_field"));
    }

    @Test
    void 排序未声明字段被拒绝() {
        assertThrows(BizException.class, () ->
                QueryUtilsX.doListQuery(filters(new QueryFilterX("status", OperatorX.EQ, 1)),
                        org.springframework.data.domain.Sort.by("hack_order"), UserVO.class));
    }

    @Test
    void 前端参数直通同样受字段校验约束() {
        Map<String, Object> params = new java.util.HashMap<>();
        params.put("EQ_hacker_field", "x");
        assertThrows(BizException.class, () ->
                QueryUtilsX.doQuery(QueryUtilsX.parse(params), PageRequest.of(0, 10), UserVO.class));
    }

    @Test
    void 分页大小超限被拒绝() {
        BizException e = assertThrows(BizException.class, () ->
                QueryUtilsX.doQuery(filters(), PageRequest.of(0, 5), UserVO.class));
        assertTrue(e.getMessage().contains("分页大小超限"));
    }

    @Test
    void 未分页结果超行数上限被拒绝() {
        // max-rows=3，库里有 4 行
        BizException e = assertThrows(BizException.class, () ->
                QueryUtilsX.doListQuery(filters(), UserVO.class));
        assertTrue(e.getMessage().contains("行数上限"));
    }

    @Test
    void 分页页大小恰在上限内正常返回() {
        List<UserVO> list = QueryUtilsX.doListQuery(filters(), PageRequest.of(0, 2), UserVO.class);
        assertEquals(2, list.size());
    }

    @Test
    void 拦截器与计时生效() {
        BEFORE.clear();
        AFTER.clear();
        QueryUtilsX.doListQuery(filters(new QueryFilterX("status", OperatorX.EQ, 1)), UserVO.class);
        assertEquals(1, BEFORE.size());
        assertTrue(BEFORE.get(0).startsWith("list-query:"));
        assertEquals(1, AFTER.size());
        // rowCount: status=1 的共 3 行
        assertTrue(AFTER.get(0).contains(":3"));
    }

    @Test
    void 原生查询行数上限生效() {
        assertThrows(BizException.class, () ->
                QueryUtilsX.doNativeQuery("SELECT id AS id, name AS name FROM t_user", null, UserVO.class));
    }
}
