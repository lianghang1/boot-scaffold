package com.sfi.scaffold.starter.liquibase.database;

import com.sfi.scaffold.common.database.DatabaseType;
import liquibase.database.Database;
import liquibase.database.DatabaseFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import java.util.List;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/** 按声明注册：声明才注册、未声明不注册、达梦扩展缺失只 WARN 不阻塞。 */
class ScaffoldDatabaseRegistrarTest {

    @AfterEach
    void resetFactory() {
        DatabaseFactory.reset();
    }

    private List<String> registeredClasses() {
        return DatabaseFactory.getInstance().getImplementedDatabases().stream()
                .map(db -> db.getClass().getName())
                .collect(Collectors.toList());
    }

    @Test
    void registersKingbaseOnlyWhenDeclared() {
        assertThat(registeredClasses()).doesNotContain(KingbaseDatabase.class.getName());

        MockEnvironment env = new MockEnvironment();
        env.setProperty("scaffold.database.db-type", "KINGBASE");
        ScaffoldDatabaseRegistrar.registerForDeclarations(env);

        assertThat(registeredClasses()).contains(KingbaseDatabase.class.getName());
        assertThat(registeredClasses()).doesNotContain(OpenGaussDatabase.class.getName());
    }

    @Test
    void registersGaussAndOceanBaseFromDataSourceBlocks() {
        MockEnvironment env = new MockEnvironment();
        env.setProperty("scaffold.datasource.dynamic.datasources.og.db-type", "GAUSS");
        env.setProperty("scaffold.datasource.dynamic.datasources.ob.db-type", "OCEANBASE");
        ScaffoldDatabaseRegistrar.registerForDeclarations(env);

        assertThat(registeredClasses())
                .contains(OpenGaussDatabase.class.getName(), OceanBaseDatabase.class.getName());
    }

    @Test
    void registerOnceIsIdempotent() {
        MockEnvironment env = new MockEnvironment();
        env.setProperty("scaffold.database.db-type", "KINGBASE");
        ScaffoldDatabaseRegistrar.registerForDeclarations(env);
        ScaffoldDatabaseRegistrar.registerForDeclarations(env);

        long count = registeredClasses().stream()
                .filter(KingbaseDatabase.class.getName()::equals)
                .count();
        assertThat(count).isEqualTo(1);
    }

    @Test
    void doesNothingWithoutDeclarations() {
        ScaffoldDatabaseRegistrar.registerForDeclarations(new MockEnvironment());
        assertThat(registeredClasses())
                .doesNotContain(KingbaseDatabase.class.getName(),
                        OpenGaussDatabase.class.getName(),
                        OceanBaseDatabase.class.getName());
    }

    @Test
    void damengWithoutExtensionOnlyWarns() {
        MockEnvironment env = new MockEnvironment();
        env.setProperty("scaffold.database.db-type", DatabaseType.DAMENG.name());
        assertThatCode(() -> ScaffoldDatabaseRegistrar.registerForDeclarations(env)).doesNotThrowAnyException();
        assertThat(registeredClasses()).doesNotContain(KingbaseDatabase.class.getName());
    }
}
