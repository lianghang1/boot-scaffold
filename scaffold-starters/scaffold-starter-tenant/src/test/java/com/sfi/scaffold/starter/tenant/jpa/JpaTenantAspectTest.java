package com.sfi.scaffold.starter.tenant.jpa;

import com.sfi.scaffold.starter.security.identity.LoginContext;
import com.sfi.scaffold.starter.security.identity.LoginUser;
import com.sfi.scaffold.starter.tenant.core.TenantScope;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * JPA 通路集成测试（H2）：@TenantFilter 组合注解（Hibernate meta-annotation）生效、
 * findAll 按租户过滤、findById 不一致返回空、@TenantIgnore 豁免、平台视角放行、
 * TenantScope 模拟租户、自定列/字段形态。
 *
 * @author lianghang
 */
@SpringBootTest(classes = TestApp.class,
        properties = {"spring.jpa.hibernate.ddl-auto=create-drop", "scaffold.tenant.enabled=true"})
class JpaTenantAspectTest {

    @Autowired
    TenantRecordRepository tenantRecordRepository;

    @Autowired
    PlainRecordRepository plainRecordRepository;

    @Autowired
    CustomFieldRecordRepository customFieldRepository;

    @Autowired
    TenantDemoService demoService;

    @BeforeEach
    void cleanUp() {
        // Spring 上下文跨测试类缓存，先清库保证各用例计数从零开始
        tenantRecordRepository.deleteAll();
        plainRecordRepository.deleteAll();
        customFieldRepository.deleteAll();
    }

    @AfterEach
    void tearDown() {
        LoginContext.clear();
    }

    @Test
    void findAllShouldFilterByCurrentTenant() {
        seed("t1", "t1", "t2");

        login("t1");
        assertThat(tenantRecordRepository.findAll())
                .hasSize(2)
                .allSatisfy(r -> assertThat(r.getTenantId()).isEqualTo("t1"));

        login("t2");
        assertThat(tenantRecordRepository.findAll())
                .hasSize(1)
                .allSatisfy(r -> assertThat(r.getTenantId()).isEqualTo("t2"));
    }

    @Test
    void platformViewShouldSeeEverything() {
        seed("t1", "t1", "t2");
        // 无登录态 = 平台视角：不启用过滤器
        assertThat(tenantRecordRepository.findAll()).hasSize(3);
    }

    @Test
    void findByIdShouldReturnEmptyOnTenantMismatch() {
        List<TenantRecord> seeded = seed("t1", "t2");
        Long t2RecordId = seeded.get(1).getId();

        login("t1");
        // 过滤式：不一致返回空而非 403，防主键存在性探测
        assertThat(tenantRecordRepository.findById(t2RecordId)).isEmpty();
        Long t1RecordId = seeded.get(0).getId();
        assertThat(tenantRecordRepository.findById(t1RecordId)).isPresent();
    }

    @Test
    void tenantIgnoreMethodShouldBypassFiltering() {
        seed("t1", "t1", "t2");
        login("t1");
        assertThat(demoService.listAllIgnoringTenant()).hasSize(3);
    }

    @Test
    void entityWithoutTenantFilterShouldStayVisible() {
        seed("t1", "t1", "t2");
        login("t1");
        plainRecordRepository.save(new PlainRecord("公开配置"));
        plainRecordRepository.flush();
        // 未标注 @Filter 的实体完全不受租户过滤器影响
        assertThat(plainRecordRepository.findAll()).hasSize(1);
    }

    @Test
    void tenantScopeShouldSimulateTenantForBackgroundJobs() {
        seed("t1", "t1", "t2");

        // 无登录态的后台线程：块内为租户视角，块外恢复平台视角
        List<TenantRecord> inScope = TenantScope.execute("t1", () -> tenantRecordRepository.findAll());
        assertThat(inScope).hasSize(2);
        assertThat(LoginContext.currentOrNull()).isNull();
        assertThat(tenantRecordRepository.findAll()).hasSize(3);

        // 已有登录身份：块内换租户，块外恢复原身份原租户
        login("t1");
        List<TenantRecord> switched = TenantScope.execute("t2", () -> tenantRecordRepository.findAll());
        assertThat(switched).hasSize(1);
        assertThat(LoginContext.current().getTenantId()).isEqualTo("t1");
    }

    @Test
    void customTenantFieldShouldBeResolvedViaTenantIdFieldAnnotation() {
        List<CustomFieldRecord> seeded = customFieldRepository.saveAll(Arrays.asList(
                new CustomFieldRecord("t1", "a"), new CustomFieldRecord("t2", "b")));
        customFieldRepository.flush();
        login("t1");
        // @TenantIdField 标注的字段参与 findById 比对
        assertThat(customFieldRepository.findById(seeded.get(0).getId())).isPresent();
        assertThat(customFieldRepository.findById(seeded.get(1).getId())).isEmpty();
        // @Filter condition 引用自定列（org_tenant），findAll 同样过滤
        assertThat(customFieldRepository.findAll()).hasSize(1);
    }

    private List<TenantRecord> seed(String... tenants) {
        List<TenantRecord> records = new java.util.ArrayList<>();
        int i = 0;
        for (String tenant : tenants) {
            TenantRecord record = new TenantRecord("数据-" + (i++));
            record.setTenantId(tenant);
            records.add(tenantRecordRepository.save(record));
        }
        tenantRecordRepository.flush();
        return records;
    }

    private void login(String tenantId) {
        LoginUser user = new LoginUser();
        user.setAccountId(1L);
        user.setAccountNo("admin");
        user.setDisplayName("管理员");
        user.setTenantId(tenantId);
        LoginContext.set(user);
    }
}
