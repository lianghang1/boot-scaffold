package com.sfi.scaffold.starter.data.jpa.logicdelete;

import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 逻辑删除 JPA 通路集成测试应用：H2 + data-jpa + query 全装配；
 * DeletedDemoService 经 @Service 组件扫描注册，@IncludeDeleted 切面由 query 自动装配。
 *
 * @author lianghang
 */
@SpringBootApplication
public class TestApp {
}
