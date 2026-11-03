package com.sfi.scaffold.query.planpck;

import com.sfi.scaffold.query.plan.annotation.ColumnX;
import com.sfi.scaffold.query.plan.annotation.JoinColumnX;
import com.sfi.scaffold.query.plan.annotation.JoinTableX;
import com.sfi.scaffold.query.plan.annotation.QueryPlanX;
import com.sfi.scaffold.query.plan.annotation.TableX;

/**
 * 计划二：returnType 与计划类不同，验证 Map → POJO 自动封装。
 */
@QueryPlanX(name = "deptUserSummary", returnType = DeptUserSummary.class)
@TableX(name = "t_user", alias = "u")
@JoinTableX(name = "t_dept", alias = "d",
        columns = @JoinColumnX(name = "id", referencedColumnName = "dept_id"))
public class DeptUserSummaryPlan {

    @ColumnX("u.name")
    private String userName;

    @ColumnX("d.dept_name")
    private String deptName;

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
