package com.sfi.scaffold.starter.data.jpa.service;

import com.sfi.scaffold.common.request.PageParam;
import com.sfi.scaffold.common.result.PageResult;
import com.sfi.scaffold.query.filter.OperatorX;
import com.sfi.scaffold.query.filter.QueryFilterX;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * BaseService 列表/分页/计数集成测试：验证实体泛型经 ResolvableType 解析后，
 * QueryFilterX 条件能正确落到自身实体，1 基分页与不分页语义正确。
 *
 * @author lianghang
 */
@SpringBootTest(classes = BaseServiceQueryTest.TestApp.class,
        properties = {
                "spring.jpa.hibernate.ddl-auto=create-drop",
                "spring.datasource.url=jdbc:h2:mem:bsquerydb;DB_CLOSE_DELAY=-1",
                "spring.datasource.driver-class-name=org.h2.Driver"
        })
class BaseServiceQueryTest {

    @SpringBootApplication
    static class TestApp {
    }

    @Autowired
    BsItemRepository repository;

    @Autowired
    BsItemService service;

    @BeforeEach
    void cleanAndSeed() {
        repository.deleteAll();
        for (int i = 1; i <= 5; i++) {
            BsItem item = new BsItem();
            item.setName("item-" + i);
            item.setScore(i * 10);
            repository.save(item);
        }
        repository.flush();
    }

    @Test
    void entityClassResolvedFromGeneric() {
        assertThat(service.entityClass()).isEqualTo(BsItem.class);
    }

    @Test
    void listWithFilter() {
        List<QueryFilterX> filters = new ArrayList<>();
        filters.add(new QueryFilterX("score", OperatorX.GE, 30));
        List<BsItem> list = service.list(filters);
        assertThat(list).hasSize(3);
        assertThat(list).allSatisfy(i -> assertThat(i.getScore()).isGreaterThanOrEqualTo(30));
    }

    @Test
    void pageIsOneBased() {
        PageParam param = new PageParam();
        param.setPageNum(2);
        param.setPageSize(2);
        PageResult<BsItem> page = service.page(new ArrayList<>(), param);
        assertThat(page.getTotal()).isEqualTo(5);
        assertThat(page.getPageNum()).isEqualTo(2);
        assertThat(page.getPageSize()).isEqualTo(2);
        assertThat(page.getList()).hasSize(2);
    }

    @Test
    void unpagedPageReturnsAll() {
        PageParam param = new PageParam();
        param.setPageSize(PageParam.PAGE_SIZE_NONE);
        PageResult<BsItem> page = service.page(new ArrayList<>(), param);
        assertThat(page.getTotal()).isEqualTo(5);
        assertThat(page.getList()).hasSize(5);
    }

    @Test
    void countWithFilter() {
        List<QueryFilterX> filters = new ArrayList<>();
        filters.add(new QueryFilterX("name", OperatorX.EQ, "item-1"));
        assertThat(service.count(filters)).isEqualTo(1);
    }
}
