package com.sfi.scaffold.starter.web.crud;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sfi.scaffold.common.annotation.CrudResource;
import com.sfi.scaffold.query.QuerySupport;
import com.sfi.scaffold.starter.data.jpa.entity.BaseEntity;
import com.sfi.scaffold.starter.data.jpa.service.BaseService;
import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Table;
import java.util.HashMap;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * {@link AbstractCrudController} 端到端：子类零方法、泛型 DTO 解析、save→BaseService、
 * delete→逻辑删除、list/get 端点自动生成。用 MockMvc standalone + mock Repository 驱动。
 *
 * @author lianghang
 */
class AbstractCrudControllerTest {

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

    static class ItemService extends BaseService<Item> {
        @SuppressWarnings("unchecked")
        ItemService(JpaRepository<Item, Long> repo) { super(repo); }
    }

    @org.springframework.web.bind.annotation.RestController
    @CrudResource(entity = Item.class, dto = ItemDto.class, module = "条目管理")
    static class ItemController extends AbstractCrudController<Item, ItemDto, ItemService> {
        ItemController(ItemService service) {
            super(service, new QuerySupport(), new ObjectMapper());
        }
    }

    @SuppressWarnings("unchecked")
    private final JpaRepository<Item, Long> repo = mock(JpaRepository.class);
    private final ItemService service = new ItemService(repo);
    private final MockMvc mvc = MockMvcBuilders.standaloneSetup(new ItemController(service)).build();

    @Test
    void getDelegatesToBaseService() throws Exception {
        Item item = new Item();
        item.setId(7L);
        when(repo.findById(7L)).thenReturn(java.util.Optional.of(item));
        mvc.perform(get("/get").param("id", "7"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.id").value(7));
    }

    @Test
    void saveResolvesGenericDtoAndCallsService() throws Exception {
        when(repo.save(any(Item.class))).thenAnswer(inv -> inv.getArgument(0));
        mvc.perform(post("/")
                        .contentType("application/json")
                        .content("{\"itemName\":\"测试条目\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.itemName").value("测试条目"));
        verify(repo).save(any(Item.class));
    }

    @Test
    void deleteDelegatesToLogicalDelete() throws Exception {
        Item item = new Item();
        item.setId(3L);
        when(repo.findById(3L)).thenReturn(java.util.Optional.of(item));
        mvc.perform(post("/delete")
                        .contentType("application/json")
                        .content("{\"id\":3}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));
        verify(repo).save(item);
        org.junit.jupiter.api.Assertions.assertTrue(Boolean.TRUE.equals(item.getDeleted()));
    }

    @Test
    void crudResourceEntityClassIsRead() {
        ItemController controller = new ItemController(service);
        org.junit.jupiter.api.Assertions.assertEquals(Item.class, controller.entityClass());
    }
}
