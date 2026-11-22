package com.sfi.scaffold.starter.data.jpa.service;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * BaseServiceQueryTest 用的测试 Repository。
 *
 * @author lianghang
 */
public interface BsItemRepository extends JpaRepository<BsItem, Long> {
}
