package com.sfi.scaffold.query;

import com.sfi.scaffold.common.result.PageResult;
import com.sfi.scaffold.query.plan.annotation.QueryPlanX;
import com.sfi.scaffold.query.planpck.UserVO;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link QuerySupport} 统一派发：1 基分页参数、page/size 别名、有 plan 走计划、无 plan 走默认实体。
 *
 * @author lianghang
 */
@SpringBootTest(classes = TestApp.class, properties = {
        "spring.datasource.url=jdbc:h2:mem:qsupportdb;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password="
})
class QuerySupportTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private final QuerySupport support = new QuerySupport();

    @QueryPlanX({UserVO.class})
    static class DemoController {
    }

    private final DemoController controller = new DemoController();

    @BeforeAll
    static void createTables(@Autowired JdbcTemplate jdbc) {
        jdbc.execute("CREATE TABLE IF NOT EXISTS t_dept (id BIGINT PRIMARY KEY, dept_name VARCHAR(100))");
        jdbc.execute("CREATE TABLE IF NOT EXISTS t_user (" +
                "id BIGINT PRIMARY KEY, name VARCHAR(100), age INT, status INT, dept_id BIGINT, deleted_at TIMESTAMP NULL)");
    }

    @BeforeEach
    void cleanAndInsert() {
        jdbcTemplate.update("DELETE FROM t_user");
        jdbcTemplate.update("DELETE FROM t_dept");
        jdbcTemplate.update("INSERT INTO t_dept VALUES (1,'研发部'),(2,'市场部')");
        jdbcTemplate.update("INSERT INTO t_user VALUES " +
                "(1,'张三',25,1,1,NULL),(2,'张四',30,1,1,NULL),(3,'李五',35,0,2,NULL),(4,'王六',28,1,2,NULL)");
    }

    private Map<String, Object> params(Object... kv) {
        Map<String, Object> m = new HashMap<>();
        for (int i = 0; i < kv.length; i += 2) {
            m.put((String) kv[i], kv[i + 1]);
        }
        return m;
    }

    @Test
    void defaultEntityWithOneBasedPage() {
        PageResult<UserVO> r = support.query(controller,
                params("pageNum", "2", "pageSize", "2"), UserVO.class);
        assertEquals(4, r.getTotal());
        assertEquals(2, r.getPageNum());
        assertEquals(2, r.getPageSize());
        assertEquals(2, r.getList().size());
    }

    @Test
    void pageNumOneIsFirstPage() {
        PageResult<UserVO> r = support.query(controller,
                params("pageNum", "1", "pageSize", "2"), UserVO.class);
        assertEquals(2, r.getList().size());
        assertNotEquals(r.getList().get(0).getId(), support.query(controller,
                params("pageNum", "2", "pageSize", "2"), UserVO.class).getList().get(0).getId());
    }

    @Test
    void pageAndSizeAliasesAccepted() {
        PageResult<UserVO> r = support.query(controller,
                params("page", "1", "size", "3"), UserVO.class);
        assertEquals(1, r.getPageNum());
        assertEquals(3, r.getPageSize());
        assertEquals(3, r.getList().size());
    }

    @Test
    void servletStringArrayValuesAccepted() {
        PageResult<UserVO> r = support.query(controller,
                params("pageNum", new String[]{"1"}, "pageSize", new String[]{"2"}), UserVO.class);
        assertEquals(2, r.getList().size());
    }

    @Test
    void planParamSelectsPlanFromWhitelist() {
        PageResult<UserVO> r = support.query(controller,
                params("plan", "UserVO", "pageNum", "1", "pageSize", "2"), UserVO.class);
        assertEquals(4, r.getTotal());
    }

    @Test
    void illegalPlanRejected() {
        assertThrows(com.sfi.scaffold.common.exception.BizException.class,
                () -> support.query(controller, params("plan", "NoSuchPlan"), UserVO.class));
    }
}
