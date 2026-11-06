package com.sfi.scaffold.starter.tenant.jpa;

import com.sfi.scaffold.starter.data.jpa.entity.BaseEntity;

import javax.persistence.Entity;
import javax.persistence.Table;

/**
 * 非租户实体：未标注 @Filter，验证完全不受租户过滤器影响。
 *
 * @author lianghang
 */
@Entity
@Table(name = "TBL_TENANT_PLAIN")
public class PlainRecord extends BaseEntity {

    private String name;

    protected PlainRecord() {
    }

    public PlainRecord(String name) {
        this.name = name;
    }
}
