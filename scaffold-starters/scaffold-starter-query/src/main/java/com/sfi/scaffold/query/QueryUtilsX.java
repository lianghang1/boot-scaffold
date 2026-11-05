package com.sfi.scaffold.query;

import cn.zhxu.bs.BeanSearcher;
import cn.zhxu.bs.MapSearcher;
import cn.zhxu.bs.SearchResult;
import cn.zhxu.bs.util.MapBuilder;
import cn.zhxu.bs.util.MapUtils;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sfi.scaffold.common.exception.BizException;
import com.sfi.scaffold.common.exception.GlobalErrorCode;
import com.sfi.scaffold.query.config.QueryProperties;
import com.sfi.scaffold.query.filter.OperatorX;
import com.sfi.scaffold.query.filter.QueryFilterX;
import com.sfi.scaffold.query.plan.annotation.IgnorePropertyX;
import com.sfi.scaffold.query.plan.annotation.QueryPlanX;
import com.sfi.scaffold.query.plan.annotation.TableX;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.jdbc.core.BeanPropertyRowMapper;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * 动态检索统一入口（静态门面，底层 Bean Searcher）。
 *
 * <p>由 {@code QueryAutoConfiguration} 在启动时注入检索器；同事务：
 * Bean Searcher 默认使用 SpringSqlExecutor，在 {@code @Transactional} 方法内
 * 自动复用事务连接。</p>
 *
 * <p>门面内置收口约束：字段严格校验（只认显式声明字段）、分页大小上限、
 * 非分页行数上限、慢检索日志、异常统一翻译、{@link QueryInterceptor} SPI。</p>
 *
 * <pre>{@code
 * List<QueryFilterX> filters = new ArrayList<>();
 * filters.add(new QueryFilterX("status", OperatorX.EQ, 1));
 *
 * Page<UserVO> page = QueryUtilsX.doQuery(filters, PageRequest.of(0, 15), UserVO.class);
 * List<UserVO> list = QueryUtilsX.doListQuery(filters, UserVO.class);
 * long count       = QueryUtilsX.doCount(filters, UserVO.class);
 *
 * // QueryPlanX 计划类（@TableX 声明，计划类即结果类；直接传类执行）
 * Page<DeptUserSummaryPlan> p = QueryUtilsX.doQuery(filters, PageRequest.of(0, 15), DeptUserSummaryPlan.class);
 *
 * // 通用查询端点：@QueryPlanX({UserPlan.class, OrderPlan.class}) 贴 controller，
 * // 前端传计划 SimpleName，白名单内解析
 * Class<?> planClass = QueryUtilsX.resolvePlan(this, "UserPlan");
 * }</pre>
 *
 * @author lianghang
 */
public final class QueryUtilsX {

    private static final Logger log = LoggerFactory.getLogger(QueryUtilsX.class);

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    private static volatile BeanSearcher beanSearcher;
    private static volatile MapSearcher mapSearcher;
    private static volatile NamedParameterJdbcTemplate namedJdbc;
    private static volatile QueryProperties properties;
    private static volatile List<QueryInterceptor> interceptors;

    /** 检索类 → 允许检索的字段名集合（启动后只读缓存） */
    private static final Map<Class<?>, Set<String>> FIELD_CACHE = new ConcurrentHashMap<>();

    private QueryUtilsX() {
    }

    /** 由自动配置调用，应用内不要使用 */
    public static void configure(BeanSearcher beanSearcher, MapSearcher mapSearcher) {
        QueryUtilsX.beanSearcher = beanSearcher;
        QueryUtilsX.mapSearcher = mapSearcher;
    }

    /** 由自动配置调用（spring-jdbc 环境存在时），应用内不要使用 */
    public static void configureNative(NamedParameterJdbcTemplate namedJdbc) {
        QueryUtilsX.namedJdbc = namedJdbc;
    }

    /** 由自动配置调用，应用内不要使用 */
    public static void configure(QueryProperties properties) {
        QueryUtilsX.properties = properties;
    }

    /** 由自动配置调用，应用内不要使用 */
    public static void configureInterceptors(List<QueryInterceptor> interceptors) {
        QueryUtilsX.interceptors = interceptors == null || interceptors.isEmpty()
                ? null : new ArrayList<>(interceptors);
    }

    private static BeanSearcher searcher() {
        BeanSearcher s = beanSearcher;
        if (s == null) {
            throw new IllegalStateException("QueryUtilsX 未初始化：请引入 scaffold-starter-query 并保持 scaffold.query.enabled=true");
        }
        return s;
    }

    private static MapSearcher mapSearcher() {
        MapSearcher s = mapSearcher;
        if (s == null) {
            throw new IllegalStateException("QueryUtilsX 未初始化：请引入 scaffold-starter-query 并保持 scaffold.query.enabled=true");
        }
        return s;
    }

    private static QueryProperties props() {
        return properties;
    }

    // ==================== 统一执行管道：拦截器 + 计时 + 慢日志 + 异常翻译 ====================

    private static <T> T execute(String op, Class<?> beanClass, Supplier<T> body) {
        List<QueryInterceptor> its = interceptors;
        QueryMeta meta = new QueryMeta(op, beanClass.getName());
        if (its != null) {
            for (QueryInterceptor it : its) {
                it.before(meta);
            }
        }
        long start = System.currentTimeMillis();
        T result;
        try {
            result = body.get();
        } catch (BizException e) {
            throw e;
        } catch (DataAccessException e) {
            throw translate(e);
        }
        long elapsed = System.currentTimeMillis() - start;
        long rowCount = (result instanceof List) ? ((List<?>) result).size() : -1L;
        if (its != null) {
            for (QueryInterceptor it : its) {
                it.after(meta, elapsed, rowCount);
            }
        }
        long threshold = props() == null ? 1000L : props().getSlowSqlMillis();
        if (threshold > 0 && elapsed >= threshold) {
            log.warn("[query] 慢检索: op={} class={} 耗时={}ms", op, beanClass.getSimpleName(), elapsed);
        }
        return result;
    }

    private static RuntimeException translate(DataAccessException e) {
        return new BizException(GlobalErrorCode.SYSTEM_ERROR,
                "数据检索失败: " + e.getMostSpecificCause().getMessage(), e);
    }

    // ==================== 分页查询（返回 Page） ====================

    public static <T> Page<T> doQuery(List<QueryFilterX> filters, Pageable pageable, Class<T> beanClass) {
        return execute("page-query", beanClass, () -> doQuery0(beanClass, filters, pageable));
    }

    private static <T> Page<T> doQuery0(Class<T> beanClass, List<QueryFilterX> filters, Pageable pageable) {
        MapBuilder mb = builder(beanClass, filters, pageable);
        if (isPaged(pageable)) {
            SearchResult<T> result = searcher().search(beanClass, mb.build());
            return new PageImpl<>(result.getDataList(), pageable, result.getTotalCount().longValue());
        }
        // 未分页：全量 + 计数
        long total = doCount0(beanClass, filters);
        List<T> list = searchAllCapped(beanClass, mb);
        return new PageImpl<>(list, Pageable.unpaged(), total);
    }

    // ==================== 列表查询 ====================

    public static <T> List<T> doListQuery(List<QueryFilterX> filters, Class<T> beanClass) {
        return execute("list-query", beanClass, () -> doListQuery0(beanClass, filters, null));
    }

    /**
     * 分页列表查询。pageable 不要传 null（会与 orFilters/Sort 重载产生歧义），
     * 不分页请用二参版 {@link #doListQuery(List, Class)}。
     */
    public static <T> List<T> doListQuery(List<QueryFilterX> filters, Pageable pageable, Class<T> beanClass) {
        return execute("list-query", beanClass, () -> doListQuery0(beanClass, filters, pageable));
    }

    private static <T> List<T> doListQuery0(Class<T> beanClass, List<QueryFilterX> filters, Pageable pageable) {
        MapBuilder mb = builder(beanClass, filters, pageable);
        if (isPaged(pageable)) {
            return searcher().searchList(beanClass, mb.build());
        }
        return searchAllCapped(beanClass, mb);
    }

    // ==================== 计数 ====================

    public static long doCount(List<QueryFilterX> filters, Class<?> beanClass) {
        return execute("count", beanClass, () -> doCount0(beanClass, filters));
    }

    private static long doCount0(Class<?> beanClass, List<QueryFilterX> filters) {
        MapBuilder mb = builder(beanClass, filters, null);
        SearchResult<?> result = searcher().search(beanClass, mb.build());
        return result.getTotalCount().longValue();
    }

    // ==================== orFilters 重载 ====================

    public static <T> Page<T> doQuery(List<QueryFilterX> filters, List<QueryFilterX> orFilters,
                                      Pageable pageable, Class<T> beanClass) {
        return execute("page-query", beanClass, () -> {
            MapBuilder mb = builder(beanClass, filters, orFilters, pageable, pageable == null ? null : pageable.getSort());
            if (isPaged(pageable)) {
                SearchResult<T> result = searcher().search(beanClass, mb.build());
                return new PageImpl<>(result.getDataList(), pageable, result.getTotalCount().longValue());
            }
            long total = doCount0(beanClass, mergeOr(filters, orFilters));
            List<T> list = searchAllCapped(beanClass, mb);
            return new PageImpl<>(list, Pageable.unpaged(), total);
        });
    }

    public static <T> List<T> doListQuery(List<QueryFilterX> filters, List<QueryFilterX> orFilters, Class<T> beanClass) {
        return execute("list-query", beanClass, () -> {
            MapBuilder mb = builder(beanClass, filters, orFilters, null, null);
            return searchAllCapped(beanClass, mb);
        });
    }

    public static long doCount(List<QueryFilterX> filters, List<QueryFilterX> orFilters, Class<?> beanClass) {
        return execute("count", beanClass, () -> {
            SearchResult<?> result = searcher().search(beanClass,
                    builder(beanClass, filters, orFilters, null, null).build());
            return result.getTotalCount().longValue();
        });
    }

    // ==================== 仅排序重载 ====================

    /**
     * 只排序不分页的列表查询。
     */
    public static <T> List<T> doListQuery(List<QueryFilterX> filters, Sort sort, Class<T> beanClass) {
        return execute("list-query", beanClass, () -> {
            MapBuilder mb = builder(beanClass, filters, null, null, sort);
            return searchAllCapped(beanClass, mb);
        });
    }

    // ==================== 前端参数协议 ====================

    /**
     * 解析前端参数为过滤器列表（前缀协议）：
     * key = {@code 运算符_字段名}（如 {@code EQ_status}、{@code CN_name}），
     * value 支持 OR 分支：{@code "v1||GT_age||30"} → {@code v1 OR age>30}；
     * 无值运算符（NU/NN/BLANK/NBLANK）value 传空串；空值条件整体跳过。
     *
     * <p>解析结果同样受字段严格校验约束：未在检索类上声明的字段会在执行时被拒绝。</p>
     *
     * <pre>{@code
     * // GET /user?EQ_status=1&CN_name=张
     * List<QueryFilterX> filters = QueryUtilsX.parse(request.getParameterMap());
     * QueryUtilsX.doQuery(filters, pageable, UserVO.class);
     * }</pre>
     */
    public static List<QueryFilterX> parse(Map<String, ?> searchParams) {
        List<QueryFilterX> filters = new ArrayList<>();
        if (searchParams == null) {
            return filters;
        }
        for (Map.Entry<String, ?> entry : searchParams.entrySet()) {
            String[] parts = entry.getKey().split("_", 2);
            if (parts.length != 2 || parts[0].isEmpty() || parts[1].isEmpty()) {
                continue;
            }
            OperatorX op;
            try {
                op = OperatorX.valueOf(parts[0]);
            } catch (IllegalArgumentException e) {
                continue; // 非法运算符：跳过
            }
            Object raw = entry.getValue();
            String value = raw == null ? "" : String.valueOf(raw).trim();
            if (value.isEmpty() && !op.isValueless()) {
                continue; // 空值条件跳过
            }
            QueryFilterX filter;
            // OR 分支：v1||OP2_field2||v2
            if (value.contains("||")) {
                String[] segs = value.split("\\|\\|");
                if (segs.length >= 2) {
                    String[] orParts = segs[1].split("_", 2);
                    if (orParts.length == 2) {
                        try {
                            OperatorX orOp = OperatorX.valueOf(orParts[0]);
                            String orVal = segs.length > 2 ? segs[2].trim() : "";
                            if (!orVal.isEmpty() || orOp.isValueless()) {
                                String firstVal = op.isValueless() ? null : segs[0].trim();
                                filters.add(QueryFilterX.or(
                                        new QueryFilterX(parts[1], op, firstVal),
                                        new QueryFilterX(orParts[1], orOp, orVal.isEmpty() ? null : orVal)));
                                continue;
                            }
                        } catch (IllegalArgumentException ignored) {
                            // OR 分支非法：按普通条件处理
                        }
                    }
                }
            }
            filter = new QueryFilterX(parts[1], op, value.isEmpty() ? null : value);
            filters.add(filter);
        }
        return filters;
    }

    // ==================== 原生 SQL ====================

    /**
     * 原生 SQL 查询（结果按驼峰属性映射到 returnType，需有对应 setter）。
     */
    public static <T> List<T> doNativeQuery(String sql, Map<String, Object> params, Class<T> returnType) {
        return doNativeQuery(sql, params, null, returnType);
    }

    /**
     * 原生 SQL 分页查询：分页片段按 {@code scaffold.database.db-type} 派生
     * （MySQL/PG/金仓/openGauss/OceanBase 用 LIMIT 组，Oracle/SQL Server/达梦用 OFFSET-FETCH 组），
     * 未声明时维持 LIMIT 语法（MySQL 系）。
     */
    public static <T> List<T> doNativeQuery(String sql, Map<String, Object> params,
                                            Pageable pageable, Class<T> returnType) {
        return execute("native-query", returnType, () -> doNativeQuery0(sql, params, pageable, returnType));
    }

    private static <T> List<T> doNativeQuery0(String sql, Map<String, Object> params,
                                              Pageable pageable, Class<T> returnType) {
        NamedParameterJdbcTemplate jdbc = namedJdbc;
        if (jdbc == null) {
            throw new IllegalStateException("原生 SQL 查询需要 spring-jdbc 环境（未检测到 NamedParameterJdbcTemplate）");
        }
        Map<String, Object> bind = params == null ? new java.util.HashMap<>() : new java.util.HashMap<>(params);
        String execSql = sql;
        // 分页片段按 scaffold.database.db-type 派生（LIMIT 组 / OFFSET-FETCH 组），未声明时维持 LIMIT（MySQL 系）
        com.sfi.scaffold.common.database.DatabaseType dbType =
                com.sfi.scaffold.query.config.DatabaseTypes.resolve(null);
        if (isPaged(pageable)) {
            execSql = com.sfi.scaffold.query.config.PaginationDialect.paginate(dbType, sql);
            bind.put("__limit", pageable.getPageSize());
            bind.put("__offset", pageable.getOffset());
        } else {
            int max = props() == null ? 10000 : props().getMaxRows();
            execSql = com.sfi.scaffold.query.config.PaginationDialect.cap(dbType, sql);
            bind.put("__limit", max + 1);
        }
        List<T> rows = jdbc.query(execSql, bind, BeanPropertyRowMapper.newInstance(returnType));
        if (!isPaged(pageable)) {
            assertRowsWithinCap(rows.size());
        }
        return rows;
    }

    /**
     * 原生 SQL 计数：{@code SELECT COUNT(*) FROM (sql)}。
     */
    public static long doNativeCount(String sql, Map<String, Object> params) {
        return execute("native-count", Object.class, () -> doNativeCount0(sql, params));
    }

    private static long doNativeCount0(String sql, Map<String, Object> params) {
        NamedParameterJdbcTemplate jdbc = namedJdbc;
        if (jdbc == null) {
            throw new IllegalStateException("原生 SQL 查询需要 spring-jdbc 环境（未检测到 NamedParameterJdbcTemplate）");
        }
        Long count = jdbc.queryForObject("SELECT COUNT(*) FROM (" + sql + ") __scaffold_cnt",
                params == null ? new java.util.HashMap<>() : params, Long.class);
        return count == null ? 0L : count;
    }

    // ==================== 内部实现 ====================

    /**
     * 未分页查询统一走行数上限保护：LIMIT maxRows+1 拉取，超限拒绝。
     */
    @SuppressWarnings("unchecked")
    private static <T> List<T> searchAllCapped(Class<?> beanClass, MapBuilder mb) {
        int max = props() == null ? 10000 : props().getMaxRows();
        List<?> list = searcher().searchList(beanClass, mb.limit(0, max + 1).build());
        assertRowsWithinCap(list.size());
        return (List<T>) list;
    }

    private static void assertRowsWithinCap(int size) {
        int max = props() == null ? 10000 : props().getMaxRows();
        if (size > max) {
            throw BizException.of(GlobalErrorCode.BAD_REQUEST,
                    "查询结果超过行数上限 " + max + "，请增加过滤条件或改用分页查询");
        }
    }

    private static List<QueryFilterX> mergeOr(List<QueryFilterX> filters, List<QueryFilterX> orFilters) {
        List<QueryFilterX> merged = new ArrayList<>(filters == null ? 0 : filters.size()
                + (orFilters == null ? 0 : orFilters.size()));
        if (filters != null) {
            merged.addAll(filters);
        }
        if (orFilters != null && !orFilters.isEmpty()) {
            merged.add(QueryFilterX.or(orFilters.toArray(new QueryFilterX[0])));
        }
        return merged;
    }

    /**
     * 构建参数：动态条件 + OR 条件 + 分页 + 排序。
     * 固定条件（@TableX.filters）不再在此注入——由 FixedFilterParamFilter
     * 统一处理（避免与动态条件同字段时被覆盖，且对前端直通参数同样生效）。
     */
    private static MapBuilder builder(Class<?> beanClass, List<QueryFilterX> filters,
                                      List<QueryFilterX> orFilters, Pageable pageable, Sort sort) {
        validateFields(beanClass, filters);
        if (orFilters != null) {
            validateFields(beanClass, orFilters);
        }
        validateSort(beanClass, sort);
        MapBuilder mb = MapUtils.builder();
        FilterParams.applyAll(mb, filters);
        if (orFilters != null && !orFilters.isEmpty()) {
            FilterParams.apply(mb, QueryFilterX.or(orFilters.toArray(new QueryFilterX[0])));
        }
        if (isPaged(pageable)) {
            int maxPageSize = props() == null ? 200 : props().getMaxPageSize();
            if (pageable.getPageSize() > maxPageSize) {
                throw BizException.of(GlobalErrorCode.BAD_REQUEST,
                        "分页大小超限: " + pageable.getPageSize() + "（上限 " + maxPageSize + "）");
            }
            mb.limit(pageable.getOffset(), pageable.getPageSize());
        }
        if (sort != null && sort.isSorted()) {
            applySort(mb, sort);
        }
        return mb;
    }

    private static MapBuilder builder(Class<?> beanClass, List<QueryFilterX> filters, Pageable pageable) {
        return builder(beanClass, filters, null, pageable, pageable == null ? null : pageable.getSort());
    }

    private static void applySort(MapBuilder mb, Sort sort) {
        if (sort == null || sort.isUnsorted()) {
            return;
        }
        for (Sort.Order order : sort) {
            mb.orderBy(order.getProperty(), order.isDescending() ? "desc" : "asc");
        }
    }

    private static void validateSort(Class<?> beanClass, Sort sort) {
        if (props() == null || !props().isStrictFields() || sort == null || sort.isUnsorted()) {
            return;
        }
        Set<String> allowed = allowedFields(beanClass);
        for (Sort.Order order : sort) {
            if (!allowed.contains(order.getProperty())) {
                throw BizException.of(GlobalErrorCode.BAD_REQUEST,
                        "排序字段未声明: " + order.getProperty() + "（class=" + beanClass.getSimpleName() + "）");
            }
        }
    }

    /**
     * 字段严格校验：条件字段必须是检索类上声明的字段（前端直通参数经 parse()
     * 后同样走此校验，未声明字段在执行前被拒绝）。
     */
    private static void validateFields(Class<?> beanClass, List<QueryFilterX> filters) {
        if (props() == null || !props().isStrictFields() || filters == null || filters.isEmpty()) {
            return;
        }
        Set<String> allowed = allowedFields(beanClass);
        for (QueryFilterX filter : filters) {
            if (filter.isOrGroup()) {
                validateFields(beanClass, filter.getOrBranches());
                continue;
            }
            if (!allowed.contains(filter.getFieldName())) {
                throw BizException.of(GlobalErrorCode.BAD_REQUEST,
                        "检索字段未声明: " + filter.getFieldName()
                                + "（class=" + beanClass.getSimpleName() + "），只允许显式声明的字段");
            }
        }
    }

    private static Set<String> allowedFields(Class<?> beanClass) {
        return FIELD_CACHE.computeIfAbsent(beanClass, c -> {
            Set<String> fields = new HashSet<>();
            // 计划类（@TableX）仅按字段声明校验；普通 VO 额外补充 getter 属性
            boolean plan = c.isAnnotationPresent(TableX.class);
            for (Class<?> k = c; k != null && k != Object.class; k = k.getSuperclass()) {
                for (Field f : k.getDeclaredFields()) {
                    if (Modifier.isStatic(f.getModifiers())) {
                        continue;
                    }
                    if (f.isAnnotationPresent(IgnorePropertyX.class)) {
                        continue;
                    }
                    fields.add(f.getName());
                }
            }
            if (!plan) {
                try {
                    for (java.beans.PropertyDescriptor pd
                            : java.beans.Introspector.getBeanInfo(c).getPropertyDescriptors()) {
                        if (pd.getReadMethod() != null) {
                            fields.add(pd.getName());
                        }
                    }
                } catch (java.beans.IntrospectionException ignored) {
                    // 退化为仅字段声明
                }
            }
            return fields;
        });
    }

    private static boolean isPaged(Pageable pageable) {
        return pageable != null && pageable.isPaged();
    }

    /**
     * 从 controller 的 {@code @QueryPlanX} 声明中匹配计划类。
     *
     * @param controller     controller 实例（其类上或声明处需有 @QueryPlanX）
     * @param planSimpleName 前端传入的计划名（计划类 SimpleName，忽略大小写）
     * @return 匹配到的计划类
     * @throws BizException 未声明 @QueryPlanX、或 plan 名不在白名单内
     */
    public static Class<?> resolvePlan(Object controller, String planSimpleName) {
        return resolvePlan(controller.getClass(), planSimpleName);
    }

    public static Class<?> resolvePlan(Class<?> controllerType, String planSimpleName) {
        QueryPlanX api = controllerType.getAnnotation(QueryPlanX.class);
        if (api == null) {
            throw BizException.of(GlobalErrorCode.BAD_REQUEST,
                    "该端点未声明 @QueryPlanX，不开放查询计划");
        }
        for (Class<?> planClass : api.value()) {
            if (planClass.getSimpleName().equalsIgnoreCase(planSimpleName)) {
                return planClass;
            }
        }
        throw BizException.of(GlobalErrorCode.BAD_REQUEST,
                "未知或未授权的查询计划: " + planSimpleName);
    }

    /**
     * 计划类上声明的固定条件（@TableX.filters），总是生效。
     */
    static List<QueryFilterX> fixedFilters(Class<?> beanClass) {
        TableX table = beanClass.getAnnotation(TableX.class);
        if (table == null || table.filters().length == 0) {
            return null;
        }
        List<QueryFilterX> filters = new ArrayList<>();
        for (com.sfi.scaffold.query.plan.annotation.FixedFilter f : table.filters()) {
            filters.add(new QueryFilterX(f.name(), f.op(), parseFixedValue(f.value(), f.type())));
        }
        return filters;
    }

    private static Object parseFixedValue(String raw, Class<?> type) {
        if (raw == null || raw.isEmpty() || String.class == type) {
            return raw;
        }
        if (type == Long.class || type == long.class) {
            return Long.parseLong(raw);
        }
        if (type == Integer.class || type == int.class) {
            return Integer.parseInt(raw);
        }
        if (type == Boolean.class || type == boolean.class) {
            return Boolean.parseBoolean(raw);
        }
        if (type == Double.class || type == double.class) {
            return Double.parseDouble(raw);
        }
        return raw;
    }
}
