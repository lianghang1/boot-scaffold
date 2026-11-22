package com.sfi.scaffold.starter.data.jpa.service;

import com.sfi.scaffold.common.exception.BizException;
import com.sfi.scaffold.common.exception.GlobalErrorCode;
import com.sfi.scaffold.starter.data.jpa.entity.BaseEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * {@link BaseService} 骨架行为：get 查不到返回 null、逻辑删除过滤、
 * delete 走逻辑删除且不存在抛 NOT_FOUND、require 供写操作兜底。
 *
 * @author lianghang
 */
class BaseServiceTest {

    static class Item extends BaseEntity {
        private String name;
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
    }

    @SuppressWarnings("unchecked")
    private final JpaRepository<Item, Long> repo = mock(JpaRepository.class);
    private ItemService service;

    static class ItemService extends BaseService<Item> {
        ItemService(JpaRepository<Item, Long> repo) { super(repo); }
        Item requirePublic(Long id) { return require(id); }
    }

    @BeforeEach
    void setUp() {
        service = new ItemService(repo);
    }

    private Item item(Long id, boolean deleted) {
        Item i = new Item();
        i.setId(id);
        i.setDeleted(deleted);
        return i;
    }

    @Test
    void getReturnsAliveEntity() {
        when(repo.findById(1L)).thenReturn(Optional.of(item(1L, false)));
        assertEquals(1L, service.get(1L).getId());
    }

    @Test
    void getFiltersLogicalDeletedReturningNull() {
        when(repo.findById(1L)).thenReturn(Optional.of(item(1L, true)));
        assertNull(service.get(1L), "逻辑删除记录 get 应返回 null，不抛异常");
        assertFalse(service.exists(1L));
    }

    @Test
    void getReturnsNullWhenMissing() {
        when(repo.findById(9L)).thenReturn(Optional.empty());
        assertNull(service.get(9L));
        assertFalse(service.exists(9L));
    }

    @Test
    void getReturnsNullForNullId() {
        assertNull(service.get(null));
    }

    @Test
    void requireThrowsNotFoundForWritePaths() {
        when(repo.findById(9L)).thenReturn(Optional.empty());
        BizException ex = assertThrows(BizException.class, () -> service.requirePublic(9L));
        assertEquals(GlobalErrorCode.NOT_FOUND.getCode(), ex.getErrorCode().getCode());
    }

    @Test
    void requireReturnsAlive() {
        when(repo.findById(1L)).thenReturn(Optional.of(item(1L, false)));
        assertEquals(1L, service.requirePublic(1L).getId());
    }

    @Test
    void deleteMarksDeletedAndSavesNeverPhysicallyDeletes() {
        Item alive = item(1L, false);
        when(repo.findById(1L)).thenReturn(Optional.of(alive));
        service.delete(1L);
        assertTrue(Boolean.TRUE.equals(alive.getDeleted()));
        verify(repo).save(alive);
        verify(repo, never()).delete(any(Item.class));
        verify(repo, never()).deleteById(anyLong());
    }

    @Test
    void deleteMissingThrowsNotFound() {
        when(repo.findById(9L)).thenReturn(Optional.empty());
        assertThrows(BizException.class, () -> service.delete(9L));
        verify(repo, never()).save(any(Item.class));
    }
}
