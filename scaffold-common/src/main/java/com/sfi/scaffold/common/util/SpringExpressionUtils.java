package com.sfi.scaffold.common.util;

import org.springframework.context.expression.MapAccessor;
import org.springframework.expression.Expression;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.common.TemplateParserContext;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * SpEL 求值入口：限流/幂等/缓存等注解的 key 表达式统一在此解析。
 *
 * <p>解析结果按表达式文本缓存（Expression 线程安全可复用）；
 * 根对象取 variables 的 {@code #root}，命名变量按 {@code #name} 引用；
 * Map 变量支持属性点访问（MapAccessor）。</p>
 *
 * @author lianghang
 */
public final class SpringExpressionUtils {

    private static final ExpressionParser PARSER = new SpelExpressionParser();

    private static final Map<String, Expression> CACHE = new ConcurrentHashMap<>();

    private static final String ROOT_VARIABLE = "root";

    private SpringExpressionUtils() {
    }

    /** 求值 SpEL 表达式，返回结果（类型不匹配时由调用方转换）。 */
    public static Object evaluate(String expression, Map<String, Object> variables) {
        return compile(expression).getValue(evaluationContext(variables));
    }

    /** 求值并转换为目标类型。 */
    public static <T> T evaluate(String expression, Map<String, Object> variables, Class<T> returnType) {
        return compile(expression).getValue(evaluationContext(variables), returnType);
    }

    /** 模板表达式求值：{@code "订单{@link #orderId} 已发货"} 形态，返回拼装后的文本。 */
    public static String evaluateTemplate(String template, Map<String, Object> variables) {
        Expression expression = CACHE.computeIfAbsent("T:" + template,
                k -> PARSER.parseExpression(template, new TemplateParserContext()));
        return expression.getValue(evaluationContext(variables), String.class);
    }

    private static Expression compile(String expression) {
        return CACHE.computeIfAbsent(expression, PARSER::parseExpression);
    }

    private static StandardEvaluationContext evaluationContext(Map<String, Object> variables) {
        StandardEvaluationContext context = new StandardEvaluationContext();
        context.addPropertyAccessor(new MapAccessor());
        if (variables != null) {
            for (Map.Entry<String, Object> entry : variables.entrySet()) {
                context.setVariable(entry.getKey(), entry.getValue());
            }
            context.setRootObject(variables.get(ROOT_VARIABLE));
        }
        return context;
    }
}
