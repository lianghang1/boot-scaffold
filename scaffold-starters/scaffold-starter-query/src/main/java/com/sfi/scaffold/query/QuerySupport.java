package com.sfi.scaffold.query;

import com.sfi.scaffold.common.request.PageParam;
import com.sfi.scaffold.common.result.PageResult;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

import java.util.Map;

/**
 * 统一查询派发收口——「列表 / 分页 / 多表检索」端点的唯一入口。
 *
 * <p>把三处曾经散落在各 controller 的样板收拢到一处：</p>
 * <ol>
 *   <li><b>检索模型选择</b>：请求带 plan 参数 → 经 {@link QueryUtilsX#resolvePlan} 在
 *       {@link com.sfi.scaffold.query.plan.annotation.QueryPlanX} 白名单内匹配计划类（通路③）；
 *       否则用调用方给定的默认 JPA 实体类（通路②）。两者全程自洽，不在一次查询里混用。</li>
 *   <li><b>分页参数统一</b>：controller 从 request 提取参数 map 直接传入，页码一律 1 基
 *       （对齐 {@link PageParam} / {@link PageResult}），内部转 0 基 PageRequest。</li>
 *   <li><b>返回结构统一</b>：一律返回 {@link PageResult}，不再让 Spring 的 {@code Page} 沿调用链外泄。</li>
 * </ol>
 *
 * <p>本类不依赖 servlet——controller 负责把 {@code request.getParameterMap()} 传进来，
 * 保持 query starter 的轻量与可测试性。</p>
 *
 * <pre>{@code
 * // controller（GET /api/xxx/list）
 * R<PageResult<SysDictData>> list(HttpServletRequest request) {
 *     return R.ok(querySupport.query(this, request.getParameterMap(), SysDictData.class));
 * }
 * }</pre>
 *
 * @author lianghang
 */
public final class QuerySupport {

    /** 前端传计划类 SimpleName 的参数名。 */
    public static final String PLAN_PARAM = "plan";
    public static final String PAGE_NUM_PARAM = "pageNum";
    public static final String PAGE_SIZE_PARAM = "pageSize";

    /** 页码别名（前端习惯 page / size 也接受，统一从 pageNum/pageSize 归一）。 */
    private static final String PAGE_NUM_ALIAS = "page";
    private static final String PAGE_SIZE_ALIAS = "size";

    public QuerySupport() {
    }

    /**
     * 统一查询派发。
     *
     * @param controller      controller 实例，供 {@code @QueryPlanX} 白名单解析
     * @param params          request.getParameterMap()（值可为 String[]）
     * @param defaultBeanType 无 plan 时的默认检索模型（通路②的 JPA 实体）
     * @return 1 基分页结果
     */
    public <T> PageResult<T> query(Object controller, Map<String, ?> params, Class<T> defaultBeanType) {
        Class<?> target = resolveTarget(controller, params, defaultBeanType);
        PageParam page = parsePage(params);

        int pageNum = page.getPageNum();
        int pageSize = page.getPageSize();
        Page<?> result;
        if (page.isUnpaged()) {
            result = QueryUtilsX.doQuery(QueryUtilsX.parse(params),
                    org.springframework.data.domain.Pageable.unpaged(), target);
        } else {
            // 框架统一 1 基 → Spring Data 0 基
            result = QueryUtilsX.doQuery(QueryUtilsX.parse(params),
                    PageRequest.of(pageNum - 1, pageSize), target);
        }
        @SuppressWarnings("unchecked")
        java.util.List<T> content = (java.util.List<T>) result.getContent();
        long total = result.getTotalElements();
        return PageResult.of(content, total, pageNum, page.isUnpaged() ? total : pageSize);
    }

    /** 选择检索模型：有 plan 走白名单，无 plan 走默认实体。 */
    @SuppressWarnings("unchecked")
    private <T> Class<?> resolveTarget(Object controller, Map<String, ?> params, Class<T> defaultBeanType) {
        String plan = first(params, PLAN_PARAM);
        if (plan == null || plan.isEmpty()) {
            return defaultBeanType;
        }
        return QueryUtilsX.resolvePlan(controller, plan);
    }

    /** 分页参数归一：pageNum/pageSize（兼容 page/size 别名），1 基，越界取默认。 */
    private PageParam parsePage(Map<String, ?> params) {
        PageParam page = new PageParam();
        String num = first(params, PAGE_NUM_PARAM);
        if (num == null || num.isEmpty()) {
            num = first(params, PAGE_NUM_ALIAS); // 兼容 page（前端旧习惯，1 基语义）
        }
        String size = first(params, PAGE_SIZE_PARAM);
        if (size == null || size.isEmpty()) {
            size = first(params, PAGE_SIZE_ALIAS);
        }
        int pageNum = toInt(num, 1);
        int pageSize = toInt(size, 10);
        page.setPageNum(pageNum < 1 ? 1 : pageNum);
        page.setPageSize(pageSize == PageParam.PAGE_SIZE_NONE || pageSize > 0
                ? pageSize : 10);
        return page;
    }

    private static int toInt(String v, int def) {
        try {
            return v == null || v.isEmpty() ? def : Integer.parseInt(v.trim());
        } catch (NumberFormatException e) {
            return def;
        }
    }

    /** 兼容 servlet 的 String[]：单值取首个。 */
    private static String first(Map<String, ?> params, String key) {
        if (params == null) {
            return null;
        }
        Object raw = params.get(key);
        if (raw == null) {
            return null;
        }
        if (raw instanceof String[]) {
            String[] arr = (String[]) raw;
            return arr.length > 0 ? arr[0] : null;
        }
        return String.valueOf(raw);
    }
}
