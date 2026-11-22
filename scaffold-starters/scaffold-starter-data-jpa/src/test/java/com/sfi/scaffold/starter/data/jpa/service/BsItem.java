package com.sfi.scaffold.starter.data.jpa.service;

import com.sfi.scaffold.starter.data.jpa.entity.BaseEntity;

import javax.persistence.Entity;
import javax.persistence.Table;

/**
 * BaseServiceQueryTest 用的测试实体。
 *
 * @author lianghang
 */
@Entity
@Table(name = "bs_item")
public class BsItem extends BaseEntity {

    private String name;
    private Integer score;

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public Integer getScore() { return score; }
    public void setScore(Integer score) { this.score = score; }
}
