package com.sfi.scaffold.starter.data.jpa.logicdelete;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * @author lianghang
 */
public interface DeletedRecordRepository extends JpaRepository<DeletedRecord, Long> {
}
