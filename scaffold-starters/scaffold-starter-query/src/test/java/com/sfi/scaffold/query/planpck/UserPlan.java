package com.sfi.scaffold.query.planpck;

import com.sfi.scaffold.query.plan.annotation.ColumnX;
import com.sfi.scaffold.query.plan.annotation.JoinColumnX;
import com.sfi.scaffold.query.plan.annotation.JoinTableX;

import com.sfi.scaffold.query.plan.annotation.FixedFilter;
import com.sfi.scaffold.query.plan.annotation.TableX;

/**
 * 计划一：用户联表计划（直接返回计划类实例），带固定条件 status=1。
 */
@TableX(name = "t_user", alias = "u",
        filters = @FixedFilter(name = "status", op = com.sfi.scaffold.query.filter.OperatorX.EQ,
                value = "1", type = Integer.class))
@JoinTableX(name = "t_dept", alias = "d",
        columns = @JoinColumnX(name = "id", referencedColumnName = "dept_id"))
public class UserPlan {

    @ColumnX("u.id")
    private Long id;

    @ColumnX("u.name")
    private String name;

    @ColumnX("u.age")
    private Integer age;

    @ColumnX("u.status")
    private Integer status;

    @ColumnX("d.dept_name")
    private String deptName;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Integer getAge() {
        return age;
    }

    public void setAge(Integer age) {
        this.age = age;
    }

    public String getDeptName() {
        return deptName;
    }

    public void setDeptName(String deptName) {
        this.deptName = deptName;
    }
}
