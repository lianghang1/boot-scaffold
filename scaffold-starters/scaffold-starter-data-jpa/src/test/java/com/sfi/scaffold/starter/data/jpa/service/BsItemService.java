package com.sfi.scaffold.starter.data.jpa.service;

import org.springframework.stereotype.Service;

/**
 * BaseServiceQueryTest 用的测试 Service——继承 BaseService 声明实体泛型。
 *
 * @author lianghang
 */
@Service
public class BsItemService extends BaseService<BsItem> {

    public BsItemService(BsItemRepository repo) {
        super(repo);
    }
}
