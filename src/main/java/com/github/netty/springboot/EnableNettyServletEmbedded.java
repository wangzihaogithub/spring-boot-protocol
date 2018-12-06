package com.github.netty.springboot;

import com.github.netty.springboot.server.NettyEmbeddedAutoConfiguration;
import org.springframework.context.annotation.Import;

import java.lang.annotation.*;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@Documented
@Inherited
@Import({NettyEmbeddedAutoConfiguration.class,NettyPropertiesAutoConfiguration.class})
public @interface EnableNettyServletEmbedded {

}
