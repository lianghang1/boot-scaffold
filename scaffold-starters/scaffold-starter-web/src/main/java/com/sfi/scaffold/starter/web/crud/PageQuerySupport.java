package com.sfi.scaffold.starter.web.crud;

import com.sfi.scaffold.common.request.PageParam;
import com.sfi.scaffold.common.result.PageResult;
import com.sfi.scaffold.query.QueryUtilsX;
import com.sfi.scaffold.query.filter.QueryFilterX;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import javax.servlet.http.HttpServletRequest;
import java.util.List;

/**
 * 列表查询的分页装配公共能力：把「分页参数解析 + QueryUtilsX 查询 + PageResult 包装」收口到一处，
 * 供 BaseCrudController 的 plan 分支与不继承基类的特化 Controller 共用，避免各处复制。
 *
 * <p>注意：页码统一 1 基（对齐 PageParam/PageResult），内部转 Spring Data 的 0 基 Pageable；
 * pageSize=-1 表示不分页。查询固定走 QueryUtilsX 通路，逻辑删除/租户过滤由其拦截器自动注入。</p>
 *
 * @author lianghang
 */
public final class PageQuerySupport {

    private PageQuerySupport() {
    }

    /** 从 request 解析查询条件与 1 基分页参数，查询指定实体并返回统一分页结果。 */
    public static <T> PageResult<T> query(HttpServletRequest request, Class<T> entityClass) {
        return query(QueryUtilsX.parse(request.getParameterMap()), pageParamOf(request), entityClass);
    }

    /** 用给定条件与分页参数查询指定实体，返回统一分页结果。 */
    public static <T> PageResult<T> query(List<QueryFilterX> filters, PageParam page, Class<T> entityClass) {
        Pageable pageable = page.isUnpaged()
                ? Pageable.unpaged()
                : PageRequest.of(page.getPageNum() - 1, page.getPageSize());
        Page<T> paged = QueryUtilsX.doQuery(filters, pageable, entityClass);
        long total = paged.getTotalElements();
        long size = pageable.isPaged() ? page.getPageSize() : (total == 0 ? 0 : total);
        return PageResult.of(paged.getContent(), total, page.getPageNum(), size);
    }

    /** 从 request 解析 1 基分页参数：pageNum/pageSize（兼容 page/size 别名），pageSize=-1 不分页。 */
    public static PageParam pageParamOf(HttpServletRequest request) {
        PageParam p = new PageParam();
        int pageNum = toInt(first(request, "pageNum", "page"), 1);
        int pageSize = toInt(first(request, "pageSize", "size"), 10);
        p.setPageNum(pageNum < 1 ? 1 : pageNum);
        p.setPageSize(pageSize == PageParam.PAGE_SIZE_NONE || pageSize > 0 ? pageSize : 10);
        return p;
    }

    private static String first(HttpServletRequest request, String... keys) {
        for (String key : keys) {
            String v = request.getParameter(key);
            if (v != null && !v.isEmpty()) {
                return v;
            }
        }
        return null;
    }

    private static int toInt(String v, int def) {
        try {
            return v == null || v.isEmpty() ? def : Integer.parseInt(v.trim());
        } catch (NumberFormatException e) {
            return def;
        }
    }
}
