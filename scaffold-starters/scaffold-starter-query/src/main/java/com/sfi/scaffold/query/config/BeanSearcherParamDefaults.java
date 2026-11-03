package com.sfi.scaffold.query.config;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

import java.util.HashMap;
import java.util.Map;

public class BeanSearcherParamDefaults implements EnvironmentPostProcessor {

    static final String PROPERTY_SOURCE_NAME = "scaffoldQueryBeanSearcherDefaults";

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        Map<String, Object> defaults = new HashMap<>();
        defaults.put("bean-searcher.params.pagination.max-allowed-size", Integer.MAX_VALUE);
        defaults.put("bean-searcher.params.pagination.max-allowed-offset", Long.MAX_VALUE);
        environment.getPropertySources().addLast(new MapPropertySource(PROPERTY_SOURCE_NAME, defaults));
    }
}
