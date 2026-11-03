package com.sfi.scaffold.query.filter;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * 检索过滤器，底层转成 Bean Searcher 检索参数。
 *
 * <pre>{@code
 * List<QueryFilterX> filters = new ArrayList<>();
 * filters.add(new QueryFilterX("status", OperatorX.EQ, 1));
 * filters.add(new QueryFilterX(UserVO::getName, OperatorX.CN, "张"));   // lambda 字段
 * filters.add(QueryFilterX.or(                                        // OR 分支
 *         new QueryFilterX("deptId", OperatorX.EQ, 1L),
 *         new QueryFilterX("deptId", OperatorX.EQ, 2L)));
 * }</pre>
 *
 * @author lianghang
 */
public class QueryFilterX {

    /** 字段名（属性名，非数据库列名） */
    private final String fieldName;
    /** 运算符 */
    private final OperatorX operator;
    /** 字段值 */
    private final Object value;
    /** OR 分组：非 null 时本对象代表一组 OR 分支 */
    private final List<QueryFilterX> orBranches;

    public QueryFilterX(String fieldName, OperatorX operator, Object value) {
        this.fieldName = fieldName;
        this.operator = operator;
        this.value = value;
        this.orBranches = null;
    }

    /**
     * 无值运算符专用（IS NULL / IS NOT NULL / 空串判断）。
     */
    public QueryFilterX(String fieldName, OperatorX operator) {
        if (!operator.isValueless()) {
            throw new IllegalArgumentException("运算符 " + operator + " 需要传值，请使用三参构造");
        }
        this.fieldName = fieldName;
        this.operator = operator;
        this.value = null;
        this.orBranches = null;
    }

    /**
     * lambda 字段引用构造（编译期安全）。
     */
    public <T> QueryFilterX(SFunctionX<T, ?> field, OperatorX operator, Object value) {
        this(LambdaUtilsX.propertyName(field), operator, value);
    }

    private QueryFilterX(List<QueryFilterX> orBranches) {
        this.fieldName = null;
        this.operator = null;
        this.value = null;
        this.orBranches = Collections.unmodifiableList(orBranches);
    }

    /**
     * 构造 OR 分组：各分支之间 OR，分支内为单个条件。
     */
    public static QueryFilterX or(QueryFilterX... branches) {
        if (branches == null || branches.length == 0) {
            throw new IllegalArgumentException("or(...) 至少需要一个分支");
        }
        return new QueryFilterX(Arrays.asList(branches));
    }

    public String getFieldName() {
        return fieldName;
    }

    public OperatorX getOperator() {
        return operator;
    }

    public Object getValue() {
        return value;
    }

    public boolean isOrGroup() {
        return orBranches != null;
    }

    public List<QueryFilterX> getOrBranches() {
        return orBranches;
    }

    @Override
    public String toString() {
        if (isOrGroup()) {
            return "(" + orBranches + " OR)";
        }
        return fieldName + " " + operator + " " + value;
    }
}
