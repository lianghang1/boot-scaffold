package com.sfi.scaffold.query;

/**
 * 一次检索调用的元信息（拦截器上下文）。
 *
 * @author lianghang
 */
public class QueryMeta {

    /** 操作类型：page-query / list-query / count / native-query / native-count */
    private final String op;

    /** 检索类（计划类或实体 VO 类）全名 */
    private final String beanClassName;

    /** 计划名（按计划名调用时非空） */
    private final String planName;

    public QueryMeta(String op, String beanClassName, String planName) {
        this.op = op;
        this.beanClassName = beanClassName;
        this.planName = planName;
    }

    public String getOp() {
        return op;
    }

    public String getBeanClassName() {
        return beanClassName;
    }

    public String getPlanName() {
        return planName;
    }
}
