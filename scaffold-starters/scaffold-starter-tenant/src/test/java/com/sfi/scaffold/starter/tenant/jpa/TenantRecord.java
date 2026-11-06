package com.sfi.scaffold.starter.tenant.jpa;

import com.sfi.scaffold.starter.data.jpa.entity.BaseEntity;
import com.sfi.scaffold.starter.tenant.aspect.JpaTenantFilterAspect;
import org.hibernate.annotations.Filter;

import javax.persistence.Entity;
import javax.persistence.Table;

/**
 * 租户实体（默认形态）：Hibernate @Filter 直写条件（列名默认 tenant_id）。
 *
 * @author lianghang
 */
@Entity
@Table(name = "TBL_TENANT_RECORD")
@Filter(name = JpaTenantFilterAspect.FILTER_NAME, condition = "tenant_id = :tenantId")
public class TenantRecord extends BaseEntity {

    private String tenantId;

    private String name;

    protected TenantRecord() {
    }

    public TenantRecord(String name) {
        this.name = name;
    }

    public String getTenantId() {
        return tenantId;
    }

    public void setTenantId(String tenantId) {
        this.tenantId = tenantId;
    }
}
