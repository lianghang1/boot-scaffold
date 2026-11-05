package com.sfi.scaffold.query;

import com.sfi.scaffold.query.plan.ScaffoldMetaResolver;
import com.sfi.scaffold.query.plan.annotation.JoinTableX;
import com.sfi.scaffold.query.plan.annotation.TableX;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@code @TableX.useIndex}（FORCE INDEX）非 MySQL 系 fail-fast 校验。
 *
 * @author lianghang
 */
class UseIndexGuardTest {

    private final ScaffoldMetaResolver resolver = new ScaffoldMetaResolver(null);

    @AfterEach
    void cleanUp() {
        DatabaseTypesReset.reset();
    }

    @Test
    void forceIndexRejectedOnDameng() {
        MockEnvironment env = new MockEnvironment();
        env.setProperty("scaffold.database.db-type", "DAMENG");
        com.sfi.scaffold.query.config.DatabaseTypes.configure(env);
        assertThatThrownBy(() -> resolver.resolve(MainIndexPlan.class))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("FORCE INDEX")
                .hasMessageContaining("DAMENG");
    }

    @Test
    void joinForceIndexRejectedOnKingbase() {
        MockEnvironment env = new MockEnvironment();
        env.setProperty("scaffold.database.db-type", "KINGBASE");
        com.sfi.scaffold.query.config.DatabaseTypes.configure(env);
        assertThatThrownBy(() -> resolver.resolve(JoinIndexPlan.class))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("FORCE INDEX");
    }

    @Test
    void forceIndexAllowedOnMysql() {
        com.sfi.scaffold.query.config.DatabaseTypes.configure(new MockEnvironment()); // 未声明 = MySQL 系
        assertThat(resolver.resolve(MainIndexPlan.class)).isNotNull();
    }

    @Test
    void noIndexPlanFineOnDameng() {
        MockEnvironment env = new MockEnvironment();
        env.setProperty("scaffold.database.db-type", "DAMENG");
        com.sfi.scaffold.query.config.DatabaseTypes.configure(env);
        assertThat(resolver.resolve(NoIndexPlan.class)).isNotNull();
    }

    // ==================== fixtures ====================

    @TableX(name = "t_user", alias = "u", useIndex = "idx_user_status")
    static class MainIndexPlan {
        private Long id;

        public Long getId() {
            return id;
        }

        public void setId(Long id) {
            this.id = id;
        }
    }

    @TableX(name = "t_user", alias = "u")
    @JoinTableX(name = "t_dept", alias = "d", useIndex = "idx_dept_pk",
            columns = @com.sfi.scaffold.query.plan.annotation.JoinColumnX(name = "id", referencedColumnName = "dept_id"))
    static class JoinIndexPlan {
        private Long id;

        public Long getId() {
            return id;
        }

        public void setId(Long id) {
            this.id = id;
        }
    }

    @TableX(name = "t_user", alias = "u")
    static class NoIndexPlan {
        private Long id;

        public Long getId() {
            return id;
        }

        public void setId(Long id) {
            this.id = id;
        }
    }

    /** 测试类加载隔离用（DatabaseTypes.reset 同包不可见时经此类清理） */
    static final class DatabaseTypesReset {
        static void reset() {
            com.sfi.scaffold.query.config.DatabaseTypes.reset();
        }
    }
}
