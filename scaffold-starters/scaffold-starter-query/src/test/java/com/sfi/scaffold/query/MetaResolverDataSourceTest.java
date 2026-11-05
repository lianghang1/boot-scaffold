package com.sfi.scaffold.query;

import cn.zhxu.bs.BeanMeta;
import com.sfi.scaffold.query.plan.ScaffoldMetaResolver;
import com.sfi.scaffold.query.plan.annotation.ColumnX;

import com.sfi.scaffold.query.plan.annotation.TableX;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * {@code @TableX.dataSource} 具名数据源透传验证：合成 BeanMeta 时
 * 非空数据源名写入 BeanMeta（SQL 执行器按名选库，配合宿主 NamedDataSource Bean），
 * 未声明时保持 null（走默认数据源 / ThreadLocal 路由）。
 *
 * @author lianghang
 */
class MetaResolverDataSourceTest {

    @TableX(name = "cem_grave", alias = "g", dataSource = "cemetery")
    static class DsPlan {
        @ColumnX("g.id")
        private Long id;

        public Long getId() {
            return id;
        }
    }

    @TableX(name = "t_user", alias = "u")
    static class NoDsPlan {
        @ColumnX("u.id")
        private Long id;

        public Long getId() {
            return id;
        }
    }

    private final ScaffoldMetaResolver resolver = new ScaffoldMetaResolver(null);

    @Test
    void declaredDataSourceShouldBeCarriedIntoBeanMeta() {
        BeanMeta<?> meta = resolver.resolve(DsPlan.class);
        assertEquals("cemetery", meta.getDataSource());
    }

    @Test
    void absentDataSourceShouldStayNullForRoutingFallback() {
        BeanMeta<?> meta = resolver.resolve(NoDsPlan.class);
        assertNull(meta.getDataSource());
    }
}
