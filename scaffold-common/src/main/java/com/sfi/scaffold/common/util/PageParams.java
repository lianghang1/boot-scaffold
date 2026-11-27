package com.sfi.scaffold.common.util;

import com.sfi.scaffold.common.request.PageParam;

import javax.servlet.http.HttpServletRequest;
import java.util.Map;

/**
 * 分页参数解析统一出口：HTTP 请求参数 → 1 基 {@link PageParam}。
 *
 * <p>全平台分页参数解析只此一份，{@code BaseCrudController}、特化查询 Controller
 * 统一调用本工具，避免各处复制出行为漂移的解析逻辑。规则：</p>
 * <ul>
 *   <li>页码取 {@code pageNum}（兼容别名 {@code page}），默认 1，小于 1 归 1；</li>
 *   <li>页长取 {@code pageSize}（兼容别名 {@code size}），默认 10；</li>
 *   <li>{@code pageSize=-1}（{@link PageParam#PAGE_SIZE_NONE}）表示不分页；</li>
 *   <li>非法数字回落默认值。</li>
 * </ul>
 *
 * @author lianghang
 */
public final class PageParams {

    private PageParams() {
    }

    /** 默认页长 10、不截断上限。 */
    private static final int DEFAULT_PAGE_SIZE = 10;

    /** 从 Servlet 请求解析分页参数。 */
    public static PageParam of(HttpServletRequest request) {
        return of(request.getParameterMap());
    }

    /** 从参数 map 解析分页参数（供非 Servlet 场景复用），不截断上限。 */
    public static PageParam of(Map<String, ?> params) {
        return of(params, DEFAULT_PAGE_SIZE, Integer.MAX_VALUE);
    }

    /**
     * 从 Servlet 请求解析并截断页长上限。
     *
     * @param defaultPageSize 缺省/非法时的回落页长
     * @param maxPageSize     页长上限（正数才生效）
     */
    public static PageParam of(HttpServletRequest request, int defaultPageSize, int maxPageSize) {
        return of(request.getParameterMap(), defaultPageSize, maxPageSize);
    }

    /** 从参数 map 解析，可指定默认页长与上限。 */
    public static PageParam of(Map<String, ?> params, int defaultPageSize, int maxPageSize) {
        int pageNum = toInt(first(params, "pageNum", "page"), 1);
        int pageSize = toInt(first(params, "pageSize", "size"), defaultPageSize);
        if (pageSize != PageParam.PAGE_SIZE_NONE && pageSize > maxPageSize) {
            pageSize = maxPageSize;
        }
        return of(pageNum, pageSize);
    }

    /** 按显式页码/页长装配；defaultPageSize 为非法值与缺省时的回落值。 */
    public static PageParam of(int pageNum, int defaultPageSize) {
        PageParam p = new PageParam();
        p.setPageNum(Math.max(pageNum, 1));
        p.setPageSize(defaultPageSize == PageParam.PAGE_SIZE_NONE || defaultPageSize > 0
                ? defaultPageSize : DEFAULT_PAGE_SIZE);
        return p;
    }

    private static String first(Map<String, ?> params, String... keys) {
        for (String key : keys) {
            Object value = params.get(key);
            if (value instanceof String[]) {
                String[] arr = (String[]) value;
                if (arr.length > 0 && arr[0] != null && !arr[0].isEmpty()) {
                    return arr[0];
                }
            } else if (value instanceof String && !((String) value).isEmpty()) {
                return (String) value;
            }
        }
        return null;
    }

    private static int toInt(String value, int defaultValue) {
        try {
            return value == null || value.isEmpty() ? defaultValue : Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }
}
