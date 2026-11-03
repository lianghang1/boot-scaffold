package com.sfi.scaffold.query;

import com.sfi.scaffold.query.filter.OperatorX;
import com.sfi.scaffold.query.filter.QueryFilterX;
import com.sfi.scaffold.query.planpck.UserVO;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(classes = TestApp.class, properties = {
        "spring.datasource.url=jdbc:h2:mem:searchdb;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "scaffold.query.plan-packages=com.sfi.scaffold.query.planpck"
})
class QueryUtilsXTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @BeforeAll
    static void createTables(@Autowired JdbcTemplate jdbcTemplate) {
        jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS t_dept (" +
                "id BIGINT PRIMARY KEY, dept_name VARCHAR(100))");
        jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS t_user (" +
                "id BIGINT PRIMARY KEY, name VARCHAR(100), age INT, status INT, dept_id BIGINT, deleted_at TIMESTAMP NULL)");
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
    void testEqAndPage() {
        Page<UserVO> page = QueryUtilsX.doQuery(filters(
                new QueryFilterX("status", OperatorX.EQ, 1)), PageRequest.of(0, 2), UserVO.class);
        assertEquals(3, page.getTotalElements());
        assertEquals(2, page.getContent().size());
        assertEquals(2, page.getTotalPages());
    }

    @Test
    void testContainWithLambdaField() {
        List<UserVO> list = QueryUtilsX.doListQuery(filters(
                new QueryFilterX(UserVO::getName, OperatorX.CN, "张")), UserVO.class);
        assertEquals(2, list.size());
        assertTrue(list.stream().allMatch(u -> u.getName().startsWith("张")));
    }

    @Test
    void testOperators() {
        assertEquals(1, QueryUtilsX.doCount(filters(
                new QueryFilterX("age", OperatorX.GT, 30)), UserVO.class));
        assertEquals(3, QueryUtilsX.doCount(filters(
                new QueryFilterX("age", OperatorX.BT, Arrays.asList(25, 30))), UserVO.class));
        assertEquals(2, QueryUtilsX.doCount(filters(
                new QueryFilterX("id", OperatorX.IN, Arrays.asList(1L, 3L, 99L))), UserVO.class));
        assertEquals(1, QueryUtilsX.doCount(filters(
                new QueryFilterX("name", OperatorX.LL, "李")), UserVO.class));
        assertEquals(1, QueryUtilsX.doCount(filters(
                new QueryFilterX("name", OperatorX.RL, "六")), UserVO.class));
        assertEquals(4, QueryUtilsX.doCount(filters(
                new QueryFilterX("deletedAt", OperatorX.NU)), UserVO.class));
        jdbcTemplate.update("UPDATE t_user SET deleted_at = '2026-01-01' WHERE id = 1");
        assertEquals(1, QueryUtilsX.doCount(filters(
                new QueryFilterX("deletedAt", OperatorX.NN)), UserVO.class));
        assertEquals(3, QueryUtilsX.doCount(filters(
                new QueryFilterX("deletedAt", OperatorX.NU)), UserVO.class));
    }

    @Test
    void testOrGroup() {
        long count = QueryUtilsX.doCount(filters(
                QueryFilterX.or(
                        new QueryFilterX("name", OperatorX.EQ, "张三"),
                        new QueryFilterX("name", OperatorX.EQ, "李五"))), UserVO.class);
        assertEquals(2, count);
    }

    @Test
    void testSort() {
        Page<UserVO> page = QueryUtilsX.doQuery(filters(), PageRequest.of(0, 10,
                Sort.by(Sort.Direction.DESC, "age")), UserVO.class);
        assertEquals(35, page.getContent().get(0).getAge());
    }

    @Test
    void testJoinField() {
        List<UserVO> list = QueryUtilsX.doListQuery(filters(
                new QueryFilterX(UserVO::getDeptName, OperatorX.EQ, "研发部")), UserVO.class);
        assertEquals(2, list.size());
        assertEquals("研发部", list.get(0).getDeptName());
    }

    /**
     * 事务感知验证：未提交事务内插入的数据，QueryUtilsX 立即可见（同连接同事务）。
     */
    @Test
    void testParticipatesInSpringTransaction() {
        transactionTemplate.executeWithoutResult(status -> {
            jdbcTemplate.update("INSERT INTO t_user VALUES (99, '事务内', 40, 1, 1, NULL)");
            long count = QueryUtilsX.doCount(filters(
                    new QueryFilterX("name", OperatorX.EQ, "事务内")), UserVO.class);
            assertEquals(1, count);
        });
        // 事务回滚提交后（此处提交）连接已归还，外部再次可见
        assertEquals(1, QueryUtilsX.doCount(filters(
                new QueryFilterX("name", OperatorX.EQ, "事务内")), UserVO.class));
        jdbcTemplate.update("DELETE FROM t_user WHERE id = 99");
    }
}
