package com.sfi.scaffold.starter.tenant.jpa;

import com.sfi.scaffold.starter.data.jpa.entity.BaseEntity;
import com.sfi.scaffold.starter.tenant.annotation.TenantIdField;
import com.sfi.scaffold.starter.tenant.aspect.JpaTenantFilterAspect;
import org.hibernate.annotations.Filter;

import javax.persistence.Entity;
import javax.persistence.Table;

/**
 * 自定租户列/字段名形态：@Filter 直写条件（org_tenant 列）+ @TenantIdField 标注字段。
 *
 * @author lianghang
 */
@Entity
@Table(name = "TBL_TENANT_CUSTOM")
@Filter(name = JpaTenantFilterAspect.FILTER_NAME, condition = "org_tenant = :tenantId")
public class CustomFieldRecord extends BaseEntity {

    @TenantIdField
    private String orgTenant;

    private String name;

    protected CustomFieldRecord() {
    }

    public CustomFieldRecord(String orgTenant, String name) {
        this.orgTenant = orgTenant;
        this.name = name;
    }

    public String getOrgTenant() {
        return orgTenant;
    }
}
