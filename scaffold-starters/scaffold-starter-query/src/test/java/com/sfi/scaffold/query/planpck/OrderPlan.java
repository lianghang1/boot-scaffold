package com.sfi.scaffold.query.planpck;

import com.sfi.scaffold.query.filter.OperatorX;
import com.sfi.scaffold.query.plan.annotation.ColumnX;
import com.sfi.scaffold.query.plan.annotation.JoinColumnX;
import com.sfi.scaffold.query.plan.annotation.JoinTableX;
import com.sfi.scaffold.query.plan.annotation.QueryPlanX;
import com.sfi.scaffold.query.plan.annotation.FixedFilter;
import com.sfi.scaffold.query.plan.annotation.TableX;

/**
 * 多表联查计划：订单 → 用户（inner join）→ 部门（自定义 ON 条件），
 * 验证 @JoinTableX 可重复标注 + 固定条件 + inner join。
 */
@QueryPlanX(name = "orderPlan", returnType = OrderView.class)
@TableX(name = "t_order", alias = "o",
        filters = @FixedFilter(name = "deleted", op = OperatorX.EQ, value = "0", type = Integer.class))
@JoinTableX(name = "t_user", alias = "u", joinType = "inner join",
        columns = @JoinColumnX(name = "id", referencedColumnName = "user_id"))
@JoinTableX(name = "t_dept", alias = "d",
        columns = @JoinColumnX(sql = "d.id = u.dept_id"))
public class OrderPlan {

    @ColumnX("o.order_no")
    private String orderNo;

    @ColumnX("o.amount")
    private Integer amount;

    @ColumnX("o.deleted")
    private Integer deleted;

    @ColumnX("u.name")
    private String userName;

    @ColumnX("d.dept_name")
    private String deptName;

    public String getOrderNo() {
        return orderNo;
    }

    public void setOrderNo(String orderNo) {
        this.orderNo = orderNo;
    }

    public Integer getAmount() {
        return amount;
    }

    public Integer getDeleted() {
        return deleted;
    }

    public void setDeleted(Integer deleted) {
        this.deleted = deleted;
    }

    public void setAmount(Integer amount) {
        this.amount = amount;
    }

    public String getUserName() {
        return userName;
    }

    public void setUserName(String userName) {
        this.userName = userName;
    }

    public String getDeptName() {
        return deptName;
    }

    public void setDeptName(String deptName) {
        this.deptName = deptName;
    }
}
