package com.sfi.scaffold.starter.data.jpa.logicdelete;

import com.sfi.scaffold.starter.data.jpa.entity.BaseEntity;

import javax.persistence.Entity;
import javax.persistence.Table;

/**
 * 无 deleted 字段、未标注 @Filter 的对照实体：应完全不受逻辑删除过滤影响。
 *
 * @author lianghang
 */
@Entity
@Table(name = "TBL_PLAIN_RECORD")
public class PlainRecord extends BaseEntity {

    private String name;

    protected PlainRecord() {
    }

    public PlainRecord(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }
}
