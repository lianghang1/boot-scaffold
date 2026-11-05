package com.sfi.scaffold.starter.protection.support;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.core.ParameterNameDiscoverer;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;

/**
 * 注解 key 的 SpEL 解析：锁与幂等共用一处。
 *
 * <p>支持具名参数与 #p0 序号别名（与 Spring 缓存注解习惯一致）；表达式不含 # 时原样
 * 视为静态串（返回空表示无动态部分）；求值失败回落空串——key 解析失败不应放大成业务故障，
 * 锁/幂等侧按各自默认键兜底。</p>
 *
 * @author lianghang
 */
public final class SpelKeyResolver {

    private static final ExpressionParser PARSER = new SpelExpressionParser();
    private static final ParameterNameDiscoverer NAME_DISCOVERER = new DefaultParameterNameDiscoverer();

    private SpelKeyResolver() {
    }

    /** 求值 SpEL key；无动态部分或求值失败返回 ""（调用方回落默认键）。 */
    public static String resolve(ProceedingJoinPoint pjp, String expression) {
        if (expression == null || expression.isEmpty() || !expression.contains("#")) {
            return "";
        }
        try {
            MethodSignature signature = (MethodSignature) pjp.getSignature();
            StandardEvaluationContext context = new StandardEvaluationContext();
            String[] names = NAME_DISCOVERER.getParameterNames(signature.getMethod());
            Object[] args = pjp.getArgs();
            if (names != null) {
                for (int i = 0; i < names.length; i++) {
                    context.setVariable(names[i], args[i]);
                    context.setVariable("p" + i, args[i]);
                }
            } else {
                for (int i = 0; i < args.length; i++) {
                    context.setVariable("p" + i, args[i]);
                }
            }
            Object value = PARSER.parseExpression(expression).getValue(context);
            return value == null ? "" : String.valueOf(value);
        } catch (Exception e) {
            return "";
        }
    }
}
