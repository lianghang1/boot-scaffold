package com.sfi.scaffold.starter.tenant.jpa;

import com.sfi.scaffold.starter.tenant.annotation.TenantIgnore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * @TenantIgnore 方法级豁免验证用 Service。
 *
 * @author lianghang
 */
@Service
public class TenantDemoService {

    @Autowired
    private TenantRecordRepository tenantRecordRepository;

    @TenantIgnore
    public List<TenantRecord> listAllIgnoringTenant() {
        return tenantRecordRepository.findAll();
    }
}
