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
 * {@link BaseService} 骨架行为：逻辑删除过滤、逻辑删除而非物理删除、NOT_FOUND 语义。
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
    void getFiltersLogicalDeleted() {
        when(repo.findById(1L)).thenReturn(Optional.of(item(1L, true)));
        BizException ex = assertThrows(BizException.class, () -> service.get(1L));
        assertEquals(GlobalErrorCode.NOT_FOUND.getCode(), ex.getErrorCode().getCode());
    }

    @Test
    void getThrowsNotFoundWhenMissing() {
        when(repo.findById(9L)).thenReturn(Optional.empty());
        assertThrows(BizException.class, () -> service.get(9L));
    }

    @Test
    void getRejectsNullId() {
        assertThrows(BizException.class, () -> service.get(null));
    }

    @Test
    void findReturnsNullInsteadOfThrowing() {
        when(repo.findById(9L)).thenReturn(Optional.empty());
        assertNull(service.find(9L));
        assertFalse(service.exists(9L));
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
}
