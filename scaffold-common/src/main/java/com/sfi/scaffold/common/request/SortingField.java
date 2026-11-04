package com.sfi.scaffold.common.request;

import javax.validation.constraints.NotBlank;
import java.io.Serializable;
import java.util.StringJoiner;

/**
 * 排序字段：字段名 + 方向。字段白名单校验由查询层负责（见 scaffold-starter-query
 * 的排序严格校验），本模型只做协议承载。
 *
 * @author lianghang
 */
public class SortingField implements Serializable {

    private static final long serialVersionUID = 1L;

    @NotBlank(message = "排序字段不能为空")
    private String fieldName;

    private Order order = Order.ASCENDING;

    public enum Order {
        ASCENDING, DESCENDING
    }

    public SortingField() {
    }

    public SortingField(String fieldName, Order order) {
        this.fieldName = fieldName;
        this.order = order;
    }

    public String getFieldName() {
        return fieldName;
    }

    public void setFieldName(String fieldName) {
        this.fieldName = fieldName;
    }

    public Order getOrder() {
        return order;
    }

    public void setOrder(Order order) {
        this.order = order;
    }

    @Override
    public String toString() {
        return new StringJoiner(", ", SortingField.class.getSimpleName() + "[", "]")
                .add(fieldName).add(String.valueOf(order)).toString();
    }
}
