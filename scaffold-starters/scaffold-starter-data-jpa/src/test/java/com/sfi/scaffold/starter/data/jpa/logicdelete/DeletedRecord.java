package com.sfi.scaffold.starter.data.jpa.logicdelete;

import com.sfi.scaffold.starter.data.jpa.entity.BaseEntity;
import org.hibernate.annotations.Filter;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Table;

/**
 * 逻辑删除测试实体：自行声明 deleted 字段并标注 @Filter（白名单式加入过滤）。
 *
 * @author lianghang
 */
@Entity
@Table(name = "TBL_DELETED_RECORD")
@Filter(name = JpaDeletedFilterAspect.FILTER_NAME, condition = "deleted = false")
public class DeletedRecord extends BaseEntity {

    @Column(name = "deleted")
    private Boolean deleted = Boolean.FALSE;

    private String name;

    protected DeletedRecord() {
    }

    public DeletedRecord(String name) {
        this.name = name;
    }

    public Boolean getDeleted() {
        return deleted;
    }

    public void setDeleted(Boolean deleted) {
        this.deleted = deleted;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }
}
