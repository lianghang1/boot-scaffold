package com.sfi.scaffold.common.util;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sfi.scaffold.common.result.R;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Servlet 层统一出口：JSON 响应写出、请求头/AJAX 判定。
 *
 * <p>典型场景是 Filter 层的异常与 401/403 出口——Filter 阶段
 * {@code @RestControllerAdvice} 不生效，需要直接向响应写 JSON；
 * 统一走 {@link R} 结构，保证与 Controller 层响应格式一致。</p>
 *
 * @author lianghang
 */
public final class ServletUtils {

    private static final Logger log = LoggerFactory.getLogger(ServletUtils.class);

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private static final String AJAX_HEADER = "X-Requested-With";
    private static final String AJAX_HEADER_VALUE = "XMLHttpRequest";

    private ServletUtils() {
    }

    /**
     * 向响应写出 JSON（{@code application/json;charset=UTF-8}）。
     * 响应已提交时只记 debug 日志不再写出。
     */
    public static void writeJSON(HttpServletResponse response, Object object) {
        if (response.isCommitted()) {
            log.debug("[servlet] 响应已提交，跳过 JSON 写出");
            return;
        }
        try {
            response.setContentType("application/json;charset=UTF-8");
            response.setCharacterEncoding(StandardCharsets.UTF_8.name());
            OBJECT_MAPPER.writeValue(response.getOutputStream(), object);
        } catch (IOException e) {
            log.warn("[servlet] JSON 写出失败", e);
        }
    }

    /**
     * 向响应写出纯文本（UTF-8）。
     */
    public static void writeString(HttpServletResponse response, String text) {
        if (response.isCommitted()) {
            log.debug("[servlet] 响应已提交，跳过文本写出");
            return;
        }
        try {
            response.setContentType("text/plain;charset=UTF-8");
            response.setCharacterEncoding(StandardCharsets.UTF_8.name());
            response.getWriter().write(text);
        } catch (IOException e) {
            log.warn("[servlet] 文本写出失败", e);
        }
    }

    /** 读取请求头，空白时返回默认值。 */
    public static String getHeader(HttpServletRequest request, String name, String defaultValue) {
        String value = request.getHeader(name);
        return StringUtils.isBlank(value) ? defaultValue : value;
    }

    /** 是否 AJAX 请求（X-Requested-With=XMLHttpRequest）。 */
    public static boolean isAjaxRequest(HttpServletRequest request) {
        return AJAX_HEADER_VALUE.equalsIgnoreCase(request.getHeader(AJAX_HEADER));
    }

    /** 客户端 IP，取值规则见 {@link IpUtils}。 */
    public static String getClientIP(HttpServletRequest request) {
        return IpUtils.clientIp(request);
    }

    /** Filter 层统一异常出口：错误日志 + SYSTEM_ERROR JSON。 */
    public static void writeError(HttpServletResponse response, Exception e) {
        log.error("[filter] 过滤器链异常", e);
        writeJSON(response, R.fail(com.sfi.scaffold.common.exception.GlobalErrorCode.SYSTEM_ERROR));
    }
}
