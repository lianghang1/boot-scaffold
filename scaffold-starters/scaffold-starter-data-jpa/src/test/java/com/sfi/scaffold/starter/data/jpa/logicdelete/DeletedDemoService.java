package com.sfi.scaffold.starter.data.jpa.logicdelete;

import com.sfi.scaffold.query.annotation.IncludeDeleted;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

/**
 * @IncludeDeleted 方法级豁免验证用 Service——验证 jpa 通路同样读豁免标记。
 *
 * @author lianghang
 */
@Service
public class DeletedDemoService {

    @Autowired
    private DeletedRecordRepository repository;

    /** 常规查询：应过滤已删除。 */
    public List<DeletedRecord> normalFindAll() {
        return repository.findAll();
    }

    /** @IncludeDeleted 豁免：findAll 应含已删除。 */
    @IncludeDeleted
    public List<DeletedRecord> findAllIncludingDeleted() {
        return repository.findAll();
    }

    /** 常规 findById：已删除应返回空。 */
    public Optional<DeletedRecord> normalFindById(Long id) {
        return repository.findById(id);
    }

    /** @IncludeDeleted 豁免：findById 应能取到已删除。 */
    @IncludeDeleted
    public Optional<DeletedRecord> findByIdIncludingDeleted(Long id) {
        return repository.findById(id);
    }
}
