package com.sfi.scaffold.query.config;

import cn.zhxu.bs.BeanSearcher;
import cn.zhxu.bs.MapSearcher;
import cn.zhxu.bs.boot.BeanSearcherAutoConfiguration;
import cn.zhxu.bs.implement.BaseSearcher;
import com.sfi.scaffold.query.QueryUtilsX;
import com.sfi.scaffold.query.QueryInterceptor;
import com.sfi.scaffold.query.plan.ScaffoldMetaResolver;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * 动态检索自动配置：
 * <ol>
 *   <li>把 {@link ScaffoldMetaResolver} 挂到 BeanSearcher / MapSearcher（支持 QueryPlanX 注解合成）</li>
 *   <li>初始化 {@link QueryUtilsX} 静态门面</li>
 * </ol>
 *
 * <p>Bean Searcher starter 默认使用 SpringSqlExecutor，检索自动参与 Spring 事务。
 *
 * @author lianghang
 */
@AutoConfiguration(after = BeanSearcherAutoConfiguration.class)
@ConditionalOnProperty(name = "scaffold.query.enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(QueryProperties.class)
public class QueryAutoConfiguration {

    /**
     * 包装检索器的元数据解析器，使 @QueryPlanX 注解类可被检索。
     */
    @Bean
    public static SearcherPostProcessor searcherPostProcessor() {
        return new SearcherPostProcessor();
    }

    @Bean
    public QueryUtilsInitializer queryUtilsInitializer(BeanSearcher beanSearcher, MapSearcher mapSearcher,
                                                       QueryProperties properties,
                                                       org.springframework.core.env.Environment environment,
                                                       org.springframework.beans.factory.ObjectProvider<QueryInterceptor> interceptors,
                                                       org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate namedJdbc) {
        return new QueryUtilsInitializer(beanSearcher, mapSearcher, properties, environment, interceptors, namedJdbc);
    }

    /**
     * 固定条件参数过滤器：@TableX.filters 总是生效，同字段动态参数被丢弃。
     * bean-searcher-boot-starter 自动收集容器内 ParamFilter Bean。
     */
    @Bean
    @org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
    public FixedFilterParamFilter fixedFilterParamFilter() {
        return new FixedFilterParamFilter();
    }

    /**
     * 参数风格默认值：字段与运算符分隔符默认为 {@code _}（{@code age_gt=25} 风格），
     * Bean Searcher 原默认为 {@code -}（{@code age-gt}）。最低优先级，用户可覆盖。
     * {@link FixedFilterParamFilter} 注入的 {@code _op} / {@code _ic} key 依赖此风格。
     */
    @Bean
    public static org.springframework.beans.factory.config.BeanFactoryPostProcessor paramStyleDefaults() {
        return beanFactory -> {
            if (beanFactory instanceof org.springframework.beans.factory.config.ConfigurableListableBeanFactory) {
                org.springframework.core.env.ConfigurableEnvironment env =
                        beanFactory.getBean(org.springframework.core.env.ConfigurableEnvironment.class);
                java.util.Map<String, Object> defaults = new java.util.HashMap<>();
                defaults.put("bean-searcher.params.separator", "_");
                env.getPropertySources().addLast(
                        new org.springframework.core.env.MapPropertySource("scaffoldQueryParamDefaults", defaults));
            }
        };
    }

    /**
     * 检索器后置处理器：替换 metaResolver 为合成版（保留原实现为 fallback）。
     */
    static class SearcherPostProcessor implements BeanPostProcessor {

        @Override
        public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
            if (bean instanceof BaseSearcher) {
                BaseSearcher searcher = (BaseSearcher) bean;
                searcher.setMetaResolver(new ScaffoldMetaResolver(searcher.getMetaResolver()));
            }
            return bean;
        }
    }

    /**
     * 初始化 QueryUtilsX 静态门面（含收口配置与拦截器）。
     */
    static class QueryUtilsInitializer {

        QueryUtilsInitializer(BeanSearcher beanSearcher, MapSearcher mapSearcher,
                              QueryProperties properties,
                              org.springframework.core.env.Environment environment,
                              org.springframework.beans.factory.ObjectProvider<QueryInterceptor> interceptors,
                              org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate namedJdbc) {
            // 收集 db-type 声明：doNativeQuery 分页与 @TableX.useIndex 校验按数据源解析
            DatabaseTypes.configure(environment);
            QueryUtilsX.configure(beanSearcher, mapSearcher);
            QueryUtilsX.configure(properties);
            QueryUtilsX.configureInterceptors(interceptors.orderedStream()
                    .collect(java.util.stream.Collectors.toList()));
            if (namedJdbc != null) {
                QueryUtilsX.configureNative(namedJdbc);
            }
        }
    }
}
