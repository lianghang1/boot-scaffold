package com.sfi.scaffold.starter.ws;

import com.sfi.scaffold.starter.push.PushService;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.util.List;

/**
 * WebSocket 自动装配入口（{@code scaffold.ws.enabled=true} 时生效）。
 *
 * <p>装配内容：会话注册表 + 主处理器 + 握手拦截器 + WsPushChannel 挂入
 * 推送体系；上下行协议与 SSE 信封一致（PushMessage JSON 文本）。
 * 握手鉴权是可选增强：token 认证组件在场时复用 token 会话校验，
 * 缺席时握手不设防（传输层安全由边界链与网关负责）。</p>
 *
 * @author lianghang
 */
@AutoConfiguration
@ConditionalOnProperty(prefix = "scaffold.ws", name = "enabled", havingValue = "true")
@EnableConfigurationProperties(WsProperties.class)
public class WsAutoConfiguration {

    @Bean
    public WsSessionRegistry wsSessionRegistry() {
        return new WsSessionRegistry();
    }

    @Bean
    public ScaffoldWebSocketHandler scaffoldWebSocketHandler(WsSessionRegistry registry,
                                                             ObjectProvider<WsMessageListener> listenerProvider) {
        return new ScaffoldWebSocketHandler(registry, listenerProvider.orderedStream()
                .collect(java.util.stream.Collectors.toList()));
    }

    /** WebSocket 通道挂入推送体系（与 SSE 通道并列，业务一次推送两通道可达）。 */
    @Bean
    public PushChannelRegistration pushChannelRegistration(WsSessionRegistry registry,
                                                           PushService pushService) {
        pushService.registerChannel(new WsPushChannel(registry));
        return new PushChannelRegistration();
    }

    /** 仅作通道注册的标记 Bean（显式表达依赖顺序）。 */
    public static final class PushChannelRegistration {
    }

    /** token 认证组件在场：握手复用 token 会话鉴权。 */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(name = "com.sfi.scaffold.starter.security.token.TokenService")
    static class WsTokenHandshakeConfiguration {

        @Bean
        public WsEndpointConfig wsEndpointConfig(WsProperties properties, WsSessionRegistry registry,
                                                 ScaffoldWebSocketHandler handler,
                                                 com.sfi.scaffold.starter.security.token.TokenService tokenService) {
            return new WsEndpointConfig(properties, registry, handler,
                    new WsHandshakeInterceptor(tokenService));
        }
    }

    /** token 认证组件缺席：握手不设防，其余装配不变。 */
    @Configuration(proxyBeanMethods = false)
    static class WsPlainHandshakeConfiguration {

        @Bean
        @ConditionalOnMissingBean(WsEndpointConfig.class)
        public WsEndpointConfig wsEndpointConfig(WsProperties properties, WsSessionRegistry registry,
                                                 ScaffoldWebSocketHandler handler) {
            return new WsEndpointConfig(properties, registry, handler, null);
        }
    }

    @Configuration
    @EnableWebSocket
    static class WsEndpointConfig implements WebSocketConfigurer {

        private final WsProperties properties;
        private final WsSessionRegistry registry;
        private final ScaffoldWebSocketHandler handler;
        private final HandshakeInterceptor handshakeInterceptor;

        WsEndpointConfig(WsProperties properties, WsSessionRegistry registry,
                         ScaffoldWebSocketHandler handler, HandshakeInterceptor handshakeInterceptor) {
            this.properties = properties;
            this.registry = registry;
            this.handler = handler;
            this.handshakeInterceptor = handshakeInterceptor;
        }

        @Override
        public void registerWebSocketHandlers(WebSocketHandlerRegistry webSocketHandlerRegistry) {
            org.springframework.web.socket.config.annotation.WebSocketHandlerRegistration registration =
                    webSocketHandlerRegistry.addHandler(handler, properties.getPath());
            if (handshakeInterceptor != null) {
                registration.addInterceptors(handshakeInterceptor);
            }
            registration.setAllowedOrigins(properties.getAllowedOrigins().split(","));
        }
    }
}
