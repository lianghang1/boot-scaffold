package com.sfi.scaffold.common.constant;

import org.springframework.core.Ordered;

/**
 * 框架内置 Filter 顺序表：各 starter 注册 Filter 时统一引用，
 * 避免散落魔法数导致跨 starter 顺序漂移。业务 Filter 按需插在间隙。
 *
 * @author lianghang
 */
public final class FilterOrder {

    public static final int TRACE_ID = Ordered.HIGHEST_PRECEDENCE;
    public static final int XSS = Ordered.HIGHEST_PRECEDENCE + 10;
    public static final int CORS = Ordered.HIGHEST_PRECEDENCE + 100;
    public static final int AUTHENTICATION = Ordered.HIGHEST_PRECEDENCE + 200;
    public static final int AUTHORIZATION = Ordered.HIGHEST_PRECEDENCE + 300;
    public static final int REPEAT_SUBMIT = Ordered.HIGHEST_PRECEDENCE + 400;

    private FilterOrder() {
    }
}
