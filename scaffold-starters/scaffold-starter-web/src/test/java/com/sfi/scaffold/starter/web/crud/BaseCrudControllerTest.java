package com.sfi.scaffold.starter.web.crud;

import com.sfi.scaffold.common.result.PageResult;
import com.sfi.scaffold.query.filter.QueryFilterX;
import com.sfi.scaffold.starter.data.jpa.entity.BaseEntity;
import com.sfi.scaffold.starter.data.jpa.service.BaseService;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.RestController;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Table;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * {@link BaseCrudController} 端点与三处钩子：get/save/delete 委托 Service；
 * filtersOf 改写条件、beforeDelete 在删除前触发、toEntity 装配字段。
 *
 * @author lianghang
 */
class BaseCrudControllerTest {

    @Entity
    @Table(name = "t_item")
    public static class Item extends BaseEntity {
        @Column(name = "item_name")
        private String itemName;
        public String getItemName() { return itemName; }
        public void setItemName(String itemName) { this.itemName = itemName; }
    }

    public static class ItemDto {
        private String itemName;
        public String getItemName() { return itemName; }
        public void setItemName(String itemName) { this.itemName = itemName; }
    }

    @SuppressWarnings("unchecked")
    static class ItemService extends BaseService<Item> {
        ItemService() { super(mock(org.springframework.data.jpa.repository.JpaRepository.class)); }
    }

    @RestController
    static class ItemController extends BaseCrudController<Item, ItemDto, ItemService> {
        boolean beforeDeleteCalled;
        List<QueryFilterX> capturedFilters;

        ItemController(ItemService service) { super(service, Item.class); }

        @Override
        protected void beforeDelete(Long id) { beforeDeleteCalled = true; }

        @Override
        protected List<QueryFilterX> filtersOf(javax.servlet.http.HttpServletRequest request) {
            capturedFilters = new ArrayList<>(super.filtersOf(request));
            return capturedFilters;
        }
    }

    @Test
    void getDelegatesToService() throws Exception {
        ItemService service = spy(new ItemService());
        Item item = new Item();
        item.setId(7L);
        doReturn(item).when(service).get(7L);
        MockMvc mvc = MockMvcBuilders.standaloneSetup(new ItemController(service)).build();

        mvc.perform(get("/get").param("id", "7"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.id").value(7));
        verify(service).get(7L);
    }

    @Test
    void saveConvertsDtoAndDelegates() throws Exception {
        ItemService service = spy(new ItemService());
        doAnswer(inv -> inv.getArgument(0)).when(service).save(any(Item.class));
        ItemController controller = new ItemController(service);
        MockMvc mvc = MockMvcBuilders.standaloneSetup(controller).build();

        mvc.perform(post("/save").contentType("application/json")
                        .content("{\"itemName\":\"测试\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.itemName").value("测试"));
        verify(service).save(any(Item.class));
    }

    @Test
    void deleteTriggersBeforeDeleteHook() throws Exception {
        ItemService service = spy(new ItemService());
        doNothing().when(service).delete(3L);
        ItemController controller = new ItemController(service);
        MockMvc mvc = MockMvcBuilders.standaloneSetup(controller).build();

        mvc.perform(post("/delete").contentType("application/json").content("{\"id\":3}"))
                .andExpect(status().isOk());
        assertThat(controller.beforeDeleteCalled).isTrue();
        verify(service).delete(3L);
    }

    @Test
    void entityClassIsConstructorProvided() {
        ItemController controller = new ItemController(new ItemService());
        assertThat(controller.entityClass).isEqualTo(Item.class);
    }

    @Test
    void filtersHookIsInvoked() throws Exception {
        ItemService service = spy(new ItemService());
        PageResult<Item> page = PageResult.empty(1, 10);
        doReturn(page).when(service).page(anyList(), any());
        ItemController controller = new ItemController(service);
        MockMvc mvc = MockMvcBuilders.standaloneSetup(controller).build();

        mvc.perform(get("/list").param("itemName", "abc"))
                .andExpect(status().isOk());
        assertThat(controller.capturedFilters).isNotNull();
        verify(service).page(anyList(), any());
    }
}
