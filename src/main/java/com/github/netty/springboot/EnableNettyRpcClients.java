package com.github.netty.springboot;

import com.github.netty.springboot.client.NettyRpcClientsRegistrar;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Import;

import java.lang.annotation.*;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@Documented
@EnableConfigurationProperties(NettyProperties.class)
@Import({NettyRpcClientsRegistrar.class})
public @interface EnableNettyRpcClients {
	String[] value() default {};
	String[] basePackages() default {};
}
