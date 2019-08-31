package com.github.netty.springboot;

import com.github.netty.springboot.client.NettyRpcClientBeanDefinitionRegistrar;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Import;

import java.lang.annotation.*;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@Documented
@EnableConfigurationProperties(NettyProperties.class)
@Import({NettyRpcClientBeanDefinitionRegistrar.class})
public @interface EnableNettyRpcClients {
	String[] value() default {};
	String[] basePackages() default {};
}
