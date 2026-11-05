package com.sfi.scaffold.query.planpck;

import com.sfi.scaffold.query.plan.annotation.ColumnX;
import com.sfi.scaffold.query.plan.annotation.FieldTypeX;
import com.sfi.scaffold.query.plan.annotation.IgnorePropertyX;
import com.sfi.scaffold.query.plan.annotation.IgnorePropertiesX;
import com.sfi.scaffold.query.plan.annotation.JoinColumnX;
import com.sfi.scaffold.query.plan.annotation.JoinTableX;

import com.sfi.scaffold.query.plan.annotation.TableX;

/**
 * 全功能计划：自动映射（无 @ColumnX 字段驼峰转下划线）+ @IgnorePropertyX +
 * @IgnorePropertiesX + @FieldTypeX + useIndex(FORCE INDEX)。
 */
@TableX(name = "t_user", alias = "u", useIndex = "idx_user_status")
@JoinTableX(name = "t_dept", alias = "d", useIndex = "idx_dept_pk",
        columns = @JoinColumnX(name = "id", referencedColumnName = "dept_id"))
@IgnorePropertiesX({
        @IgnorePropertyX(name = "internalFlag")
})
@FieldTypeX(name = "memo", type = String.class)
public class FullFeaturePlan {

    // @ColumnX 指定列名
    @ColumnX("u.name")
    private String name;

    // 无注解：自动映射 u.age
    private Integer age;

    // 无注解：自动映射 u.status
    private Integer status;

    // @IgnorePropertiesX 类级忽略：不参与 SQL
    private Integer internalFlag;

    // @IgnorePropertyX 字段级忽略：不参与 SQL
    @IgnorePropertyX
    private String secretNote;

    // 类型被类级 @FieldTypeX(name="memo") 覆盖为 String
    private Long memo;

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

    public Integer getStatus() {
        return status;
    }

    public void setStatus(Integer status) {
        this.status = status;
    }

    public Integer getInternalFlag() {
        return internalFlag;
    }

    public void setInternalFlag(Integer internalFlag) {
        this.internalFlag = internalFlag;
    }

    public String getSecretNote() {
        return secretNote;
    }

    public void setSecretNote(String secretNote) {
        this.secretNote = secretNote;
    }

    public Long getMemo() {
        return memo;
    }

    public void setMemo(Long memo) {
        this.memo = memo;
    }
}
