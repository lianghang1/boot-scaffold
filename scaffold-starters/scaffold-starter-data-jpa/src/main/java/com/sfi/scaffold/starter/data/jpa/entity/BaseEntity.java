package com.sfi.scaffold.starter.data.jpa.entity;

import com.sfi.scaffold.starter.data.jpa.id.SnowflakeIdentifierGenerator;
import org.hibernate.annotations.FilterDef;
import org.hibernate.annotations.FilterDefs;
import org.hibernate.annotations.GenericGenerator;
import org.hibernate.annotations.ParamDef;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import javax.persistence.Column;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;
import javax.persistence.MappedSuperclass;
import javax.persistence.EntityListeners;
import java.time.LocalDateTime;

/**
 * 实体基类，统一提供雪花 Long 主键、审计字段与逻辑删除标记。
 *
 * <p>主键由 {@code SnowflakeIdentifierGenerator} 在应用侧生成，避免数据库自增
 * 带来的分库迁移问题；{@code createBy/updateBy} 由 AuditorAware 自动填充，
 * 来源为 {@code LoginContext} 当前账户，{@code createTime/updateTime} 由
 * Auditing 自动写入，业务代码无需手工赋值。</p>
 *
 * <p>本类不含租户字段与逻辑删除字段——这两者都不是每张表都有，统一由需要的
 * 实体自行声明（按固定字段名 {@code tenantId} / {@code deleted} 探测），框架
 * 跨 jpa/query 两条通路自动拼接过滤条件。需要乐观锁的实体请继承 {@link VersionedEntity}。</p>
 *
 * <p>本类同时注册数据权限等 Hibernate FilterDef（PU 全局生效，声明处仅此一处）：
 * 实体类标注 {@code @Filter(name = DataScopeAspect.FILTER_SELF)} 即加入「仅本人」
 * 过滤白名单，配合服务方法的 {@code @DataScope(SELF)} 注解按 create_by 过滤。
 * 逻辑删除同理：需要逻辑删除的实体声明 {@code deleted} 字段并标注
 * {@code @Filter(name = JpaDeletedFilterAspect.FILTER_NAME, condition = "deleted = false")}。</p>
 *
 * @author lianghang
 */
@MappedSuperclass
@EntityListeners(AuditingEntityListener.class)
@FilterDefs({
        @FilterDef(name = "scaffold_data_scope_self",
                parameters = @ParamDef(name = "owner", type = "string"),
                defaultCondition = "create_by = :owner"),
        // 部门范围 FilterDef（数据权限动态决策）：不带 defaultCondition——
        // 列名由各实体 @Filter condition 声明（默认 dept_id），自定义列的实体可覆盖
        @FilterDef(name = "scaffold_data_scope_dept",
                parameters = @ParamDef(name = "deptIds", type = "java.lang.Long")),
        // 多租户 FilterDef（scaffold-starter-tenant 在场时由切面启用）：
        // 不带 defaultCondition——列名由各实体 @Filter condition 声明（默认列 tenant_id），
        // 避免在此写死列名导致自定义列的实体无法覆盖
        @FilterDef(name = "scaffold_tenant",
                parameters = @ParamDef(name = "tenantId", type = "string")),
        // 逻辑删除 FilterDef（data-jpa 在场时由 JpaDeletedFilterAspect 启用）：
        // 条件固定，由各实体 @Filter condition 声明（默认 deleted = false），
        // 无参数——只有含 deleted 字段且标注 @Filter 的实体才加入过滤白名单
        @FilterDef(name = "scaffold_deleted")
})
public abstract class BaseEntity {

    @Id
    @GeneratedValue(generator = "snowflake")
    @GenericGenerator(name = "snowflake", strategy = "com.sfi.scaffold.starter.data.jpa.id.SnowflakeIdentifierGenerator")
    private Long id;

    @CreatedDate
    @Column(name = "create_time", nullable = false, updatable = false)
    private LocalDateTime createTime;

    @LastModifiedDate
    @Column(name = "update_time")
    private LocalDateTime updateTime;

    @CreatedBy
    @Column(name = "create_by", updatable = false, length = 64)
    private String createBy;

    @LastModifiedBy
    @Column(name = "update_by", length = 64)
    private String updateBy;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public LocalDateTime getCreateTime() {
        return createTime;
    }

    public void setCreateTime(LocalDateTime createTime) {
        this.createTime = createTime;
    }

    public LocalDateTime getUpdateTime() {
        return updateTime;
    }

    public void setUpdateTime(LocalDateTime updateTime) {
        this.updateTime = updateTime;
    }

    public String getCreateBy() {
        return createBy;
    }

    public void setCreateBy(String createBy) {
        this.createBy = createBy;
    }

    public String getUpdateBy() {
        return updateBy;
    }

    public void setUpdateBy(String updateBy) {
        this.updateBy = updateBy;
    }
}
