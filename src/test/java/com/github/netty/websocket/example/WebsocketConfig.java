package com.github.netty.websocket.example;

import com.github.netty.springboot.server.NettyRequestUpgradeStrategy;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.messaging.simp.user.SimpUserRegistry;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.config.annotation.*;
import org.springframework.web.socket.messaging.DefaultSimpUserRegistry;
import org.springframework.web.socket.server.RequestUpgradeStrategy;
import org.springframework.web.socket.server.support.DefaultHandshakeHandler;

import java.security.Principal;
import java.util.Map;

/**
 * Websocket配置
 * @author wangzihao
 */
@EnableWebSocketMessageBroker
@EnableWebSocket
@Configuration
public class WebsocketConfig implements WebSocketMessageBrokerConfigurer {

    /**
     * 容器策略, 这里选用netty
     * @return 容器策略
     */
    public RequestUpgradeStrategy requestUpgradeStrategy() {
//        return new JettyRequestUpgradeStrategy();
//        return new TomcatRequestUpgradeStrategy();
        return new NettyRequestUpgradeStrategy();
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        StompWebSocketEndpointRegistration endpoint = registry.addEndpoint("/my-websocket");//添加一个/my-websocket端点，客户端就可以通过这个端点来进行连接；

        endpoint.setHandshakeHandler(new DefaultHandshakeHandler(requestUpgradeStrategy()) {
            //这里获取首次握手的身份
            @Override
            protected Principal determineUser(ServerHttpRequest request, WebSocketHandler wsHandler, Map<String, Object> attributes) {
                String token = request.getHeaders().getFirst("access_token");
                return () -> "账号-" + token;
            }
        });//这里放入一个握手的处理器,可以处理自定义握手期间的事情,重写父类方法即可 选用netty的协议升级策略

        endpoint.setAllowedOrigins("*").withSockJS();//setAllowedOrigins(*)设置跨域.  withSockJS(*)添加SockJS支持
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.enableSimpleBroker("/topic/");//定义了一个客户端订阅地址的前缀信息，也就是客户端接收服务端发送消息的前缀信息
        registry.setApplicationDestinationPrefixes("/app");//api全局的前缀名
        registry.setUserDestinationPrefix("/user/");// 点对点使用的订阅前缀（客户端订阅路径上会体现出来），不设置的话，默认也是/user/ ， 如果设置了全局前缀效果为 /app/user/xxx
    }

    /**
     * 负责管理用户信息
     * @return
     */
    @Bean
    @ConditionalOnMissingBean(SimpUserRegistry.class)
    public SimpUserRegistry userRegistry() {
        return new DefaultSimpUserRegistry();
    }

}
