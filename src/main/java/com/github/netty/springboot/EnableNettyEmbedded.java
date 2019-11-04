package com.github.netty.springboot;

import com.github.netty.core.AbstractProtocol;
import com.github.netty.springboot.server.*;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Import;

import java.lang.annotation.*;

/**
 * Enable embedded TCP container.
 * It will enable.
 *      1. http server protocol,
 *          Servlet Web or Reactive Web. {@link NettyTcpServerFactory} {@link HttpServletProtocolSpringAdapter}
 *          Websocket. {@link NettyRequestUpgradeStrategy}
 *      2. rpc server protocol. {@link HRpcProtocolSpringAdapter}
 *      3. and user-defined protocols..
 *
 * If you want to add your own protocol,  you need implement {@link AbstractProtocol}
 * <pre> {@code
 *     \@Component
 *     public class MyProtocolsRegister extends AbstractProtocolsRegister{
 *          public static final byte[] PROTOCOL_HEADER = {
 *                  'M', 'Y',
 *                  'H', 'E', 'A', 'D', 'E', 'R'
 *          };
 *
 *          public String getProtocolName() {
 *              return "my-protocol";
 *          }
 *
 *          public boolean canSupport(ByteBuf msg) {
 *              if (msg.readableBytes() < PROTOCOL_HEADER.length) {
 *                  return false;
 *              }
 *              for (int i = 0; i < PROTOCOL_HEADER.length; i++) {
 *                  if (msg.getByte(i) != PROTOCOL_HEADER[i]) {
 *                      return false;
 *                  }
 *              }
 *              return true;
 *          }
 *
 *          public void addPipeline(Channel channel) throws Exception {
 *              channel.pipeline().addLast(new StringDecoder());
 *              channel.pipeline().addLast(new StringEncoder());
 *              channel.pipeline().addLast(new MyChannelHandler());
 *          }
 *     }
 *
 * }</pre>
 *
 *-----------------------------------------------------------
 * If you want to enable websocket protocol,  you need use NettyRequestUpgradeStrategy.class.
 *  example..
  * <pre> {@code
  * public class WebsocketConfig extends AbstractWebSocketMessageBrokerConfigurer {
  *     public RequestUpgradeStrategy requestUpgradeStrategy() {
  *         // return new JettyRequestUpgradeStrategy();
  *         // return new TomcatRequestUpgradeStrategy();
  *         return new NettyRequestUpgradeStrategy();
  *     }
  *
  *     public void registerStompEndpoints(StompEndpointRegistry registry) {
  *         StompWebSocketEndpointRegistration endpoint = registry.addEndpoint("/my-websocket");
  *         endpoint.setHandshakeHandler(new DefaultHandshakeHandler(requestUpgradeStrategy()) {
  *             protected Principal determineUser(ServerHttpRequest request, WebSocketHandler wsHandler, Map<String, Object> attributes) {
  *                 String token = request.getHeaders().getFirst("access_token");
  *                 return () -> token;
  *             }
  *         });
  *         endpoint.setAllowedOrigins("*").withSockJS();
  *     }
  *
  *     public void configureMessageBroker(MessageBrokerRegistry registry) {
  *         registry.enableSimpleBroker("/topic/");
  *         registry.setApplicationDestinationPrefixes("/app");
  *         registry.setUserDestinationPrefix("/user/");
  *     }
  *  }
  * }</pre>

 * @see com.github.netty.springboot.NettyProperties
 * @see com.github.netty.springboot.server.NettyEmbeddedAutoConfiguration
 * @see com.github.netty.springboot.server.NettyTcpServerFactory
 * @see com.github.netty.springboot.server.HttpServletProtocolSpringAdapter
 * @see com.github.netty.springboot.server.HRpcProtocolSpringAdapter
 * @see com.github.netty.springboot.server.NettyRequestUpgradeStrategy
 * @see com.github.netty.core.AbstractProtocol
 * @author wangzihao 2019-11-2 00:58:11
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@Documented
@Inherited
@EnableConfigurationProperties(NettyProperties.class)
@Import({NettyEmbeddedAutoConfiguration.class})
public @interface EnableNettyEmbedded {

}
