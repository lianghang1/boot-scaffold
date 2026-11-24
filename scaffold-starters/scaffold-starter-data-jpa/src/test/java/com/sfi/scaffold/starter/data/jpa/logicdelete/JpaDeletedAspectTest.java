package com.sfi.scaffold.starter.data.jpa.logicdelete;

import com.sfi.scaffold.query.core.IncludeDeletedHolder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * JPA 通路逻辑删除集成测试（H2）：标注 @Filter 的实体 findAll 自动过滤、
 * findById 兜底过滤、@IncludeDeleted 在 jpa 通路同样豁免、退出恢复、
 * 无 deleted 字段的实体放行、DeletedFieldAccessor 写读闭环。
 *
 * <p>与 {@code JpaTenantAspectTest}（租户通路）对等——两者共同保证 jpa 侧
 * tenantId 与 deleted 两条过滤线口径一致、互不干扰。</p>
 *
 * @author lianghang
 */
@SpringBootTest(classes = TestApp.class,
        properties = "spring.jpa.hibernate.ddl-auto=create-drop")
class JpaDeletedAspectTest {

    @Autowired
    DeletedRecordRepository repository;

    @Autowired
    PlainRecordRepository plainRepository;

    @Autowired
    DeletedDemoService demoService;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @BeforeEach
    void cleanUp() {
        // deleteAll() 自身会被过滤切面拦截（只删未删除的），用原生 SQL 清表保证隔离
        jdbcTemplate.execute("DELETE FROM TBL_DELETED_RECORD");
        jdbcTemplate.execute("DELETE FROM TBL_PLAIN_RECORD");
        IncludeDeletedHolder.clear();
    }

    @AfterEach
    void tearDown() {
        IncludeDeletedHolder.clear();
    }

    /** 常规 findAll：已删除记录被 Hibernate Filter 排除。 */
    @Test
    void findAllShouldFilterLogicalDeleted() {
        seed("存活A", false);
        seed("存活B", false);
        seed("已删C", true);

        assertThat(demoService.normalFindAll())
                .hasSize(2)
                .allSatisfy(r -> assertThat(r.getDeleted()).isFalse());
    }

    /** 常规 findById：Hibernate Filter 对主键直达无效，由兜底切面返回空。 */
    @Test
    void findByIdShouldReturnEmptyOnDeleted() {
        DeletedRecord alive = seed("存活", false);
        DeletedRecord deleted = seed("已删", true);

        assertThat(demoService.normalFindById(alive.getId())).isPresent();
        assertThat(demoService.normalFindById(deleted.getId())).isEmpty();
    }

    /** @IncludeDeleted 豁免 findAll：jpa 切面读到标记，放行已删除。 */
    @Test
    void includeDeletedShouldBypassFindAllFilter() {
        seed("存活", false);
        seed("已删", true);

        assertThat(demoService.findAllIncludingDeleted()).hasSize(2);
        // 切面退出后标记清零，恢复过滤
        assertThat(IncludeDeletedHolder.isMarked()).isFalse();
        assertThat(demoService.normalFindAll()).hasSize(1);
    }

    /** @IncludeDeleted 豁免 findById：兜底切面读到标记，返回已删除实体。 */
    @Test
    void includeDeletedShouldBypassFindByIdFilter() {
        DeletedRecord deleted = seed("已删", true);

        assertThat(demoService.normalFindById(deleted.getId())).isEmpty();
        assertThat(demoService.findByIdIncludingDeleted(deleted.getId()))
                .isPresent()
                .get()
                .extracting(DeletedRecord::getName).isEqualTo("已删");
    }

    /** 豁免方法正常退出与异常退出都要清理标记，避免污染后续查询。 */
    @Test
    void includeDeletedMarkerShouldBeClearedAfterCall() {
        seed("已删", true);
        demoService.findAllIncludingDeleted();
        assertThat(IncludeDeletedHolder.isMarked()).isFalse();
        // 后续普通调用重新过滤
        assertThat(demoService.normalFindAll()).isEmpty();
    }

    /** 未声明 deleted 字段、未标注 @Filter 的实体完全不受过滤影响。 */
    @Test
    void entityWithoutDeletedFieldShouldStayVisible() {
        seed("存活", false);
        seed("已删", true);
        plainRepository.save(new PlainRecord("公开记录"));
        plainRepository.flush();

        assertThat(plainRepository.findAll()).hasSize(1);
    }

    /** 写侧标记（DeletedFieldAccessor）与读侧过滤闭环：标记并保存后查询即排除。 */
    @Test
    void markDeletedThenQueryShouldFilter() {
        DeletedRecord target = seed("待删", false);
        assertThat(demoService.normalFindAll()).hasSize(1);

        DeletedFieldAccessor.markDeleted(target);
        repository.save(target);
        repository.flush();

        assertThat(DeletedFieldAccessor.isDeleted(target)).isTrue();
        assertThat(demoService.normalFindAll()).isEmpty();
    }

    /** DeletedFieldAccessor 字段探测：有字段读写、无字段空操作。 */
    @Test
    void deletedFieldAccessorShouldProbeByFieldName() {
        assertThat(DeletedFieldAccessor.hasField(DeletedRecord.class)).isTrue();
        assertThat(DeletedFieldAccessor.hasField(PlainRecord.class)).isFalse();

        DeletedRecord record = new DeletedRecord("x");
        assertThat(DeletedFieldAccessor.isDeleted(record)).isFalse();
        DeletedFieldAccessor.markDeleted(record);
        assertThat(DeletedFieldAccessor.isDeleted(record)).isTrue();

        // 无 deleted 字段实体：读恒 false、写空操作不抛异常
        PlainRecord plain = new PlainRecord("y");
        DeletedFieldAccessor.markDeleted(plain);
        assertThat(DeletedFieldAccessor.isDeleted(plain)).isFalse();
    }

    private DeletedRecord seed(String name, boolean deleted) {
        DeletedRecord record = new DeletedRecord(name);
        record.setDeleted(deleted);
        DeletedRecord saved = repository.save(record);
        repository.flush();
        return saved;
    }
}
