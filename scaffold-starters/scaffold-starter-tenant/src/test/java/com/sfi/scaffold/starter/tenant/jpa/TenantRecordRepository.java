package com.sfi.scaffold.starter.tenant.jpa;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * @author lianghang
 */
public interface TenantRecordRepository extends JpaRepository<TenantRecord, Long> {
}
