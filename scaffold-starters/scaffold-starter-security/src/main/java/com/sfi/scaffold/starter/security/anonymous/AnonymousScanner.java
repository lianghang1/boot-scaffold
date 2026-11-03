package com.sfi.scaffold.starter.security.anonymous;

import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.web.bind.annotation.RequestMethod;

/**
 * {@link Anonymous} 注解扫描器：启动时从 RequestMappingHandlerMapping
 * 收集匿名放行的「HttpMethod → 路径模式」清单，供安全链注册 permitAll。
 *
 * <p>类级注解对该类全部接口生效，方法级覆盖类级；方法上的
 * {@code @RequestMapping} 未声明 method 时按全部常用方法注册
 * （GET/POST/PUT/DELETE/PATCH），避免漏配导致匿名接口被 401。</p>
 *
 * @author lianghang
 */
public class AnonymousScanner {

    private static final HttpMethod[] ALL_METHODS = {
            HttpMethod.GET, HttpMethod.POST, HttpMethod.PUT, HttpMethod.DELETE, HttpMethod.PATCH};

    private final RequestMappingHandlerMapping handlerMapping;

    private final Map<HttpMethod, List<String>> patterns = new LinkedHashMap<>();

    public AnonymousScanner(RequestMappingHandlerMapping handlerMapping) {
        this.handlerMapping = handlerMapping;
        scan();
    }

    /** 把匿名清单注册为 permitAll（安全链构建时调用）。 */
    public void registerPermitAll(HttpSecurity http) throws Exception {
        for (Map.Entry<HttpMethod, List<String>> entry : patterns.entrySet()) {
            if (!entry.getValue().isEmpty()) {
                http.authorizeRequests().antMatchers(entry.getKey(),
                        entry.getValue().toArray(new String[0])).permitAll();
            }
        }
    }

    private void scan() {
        Map<RequestMappingInfo, HandlerMethod> methods = handlerMapping.getHandlerMethods();
        for (Map.Entry<RequestMappingInfo, HandlerMethod> entry : methods.entrySet()) {
            HandlerMethod handlerMethod = entry.getValue();
            boolean anonymous = handlerMethod.hasMethodAnnotation(Anonymous.class)
                    || handlerMethod.getBeanType().isAnnotationPresent(Anonymous.class);
            if (!anonymous) {
                continue;
            }
            RequestMappingInfo info = entry.getKey();
            Set<RequestMethod> declared = info.getMethodsCondition().getMethods();
            if (declared.isEmpty()) {
                for (String pattern : patternsOf(info)) {
                    for (HttpMethod m : ALL_METHODS) {
                        add(m, pattern);
                    }
                }
            } else {
                for (RequestMethod rm : declared) {
                    HttpMethod m = HttpMethod.valueOf(rm.name());
                    for (String pattern : patternsOf(info)) {
                        add(m, pattern);
                    }
                }
            }
        }
    }

    private Iterable<String> patternsOf(RequestMappingInfo info) {
        if (info.getPathPatternsCondition() != null
                && !info.getPathPatternsCondition().getPatternValues().isEmpty()) {
            return info.getPathPatternsCondition().getPatternValues();
        }
        return info.getPatternsCondition() == null
                ? java.util.Collections.<String>emptyList()
                : info.getPatternsCondition().getPatterns();
    }

    private void add(HttpMethod method, String pattern) {
        patterns.computeIfAbsent(method, k -> new ArrayList<>()).add(pattern);
    }
}
