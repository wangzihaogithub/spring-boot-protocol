package com.github.netty.springboot;

import com.github.netty.springboot.client.NettyRpcClientBeanDefinitionRegistrar;
import com.github.netty.springboot.client.NettyRpcLoadBalanced;
import com.github.netty.springboot.client.NettyRpcRequest;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Import;

import java.lang.annotation.*;


/**
 * Enable embedded Rpc client protocol
 * It will enable.
 *      1. rpc client protocol. {@link NettyRpcClientBeanDefinitionRegistrar}
 *
 * You must implement the interface. Returns ip address of the server. {@link NettyRpcLoadBalanced#chooseAddress(NettyRpcRequest)}
 *
 * @see NettyProperties
 * @see com.github.netty.springboot.client.NettyRpcLoadBalanced#chooseAddress(NettyRpcRequest)
 * @see com.github.netty.springboot.client.NettyRpcClientBeanDefinitionRegistrar
 * @author wangzihao 2019-11-2 00:58:38
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@Documented
@EnableConfigurationProperties(NettyProperties.class)
@Import({NettyRpcClientBeanDefinitionRegistrar.class})
public @interface EnableNettyRpcClients {
	String[] value() default {};
	String[] basePackages() default {};
}
