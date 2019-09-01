package com.github.netty.springboot;

import org.springframework.stereotype.Component;

import java.lang.annotation.*;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@Documented
@Component
public @interface NettyRpcClient {
    /**
     * The serviceName is the same as serviceName
     * example value "service-provider"
     * @return serviceName
     */
    String serviceImplName();

//    Class<?> fallback() default void.class;

    /**
     * Timeout time (milliseconds)
     * @return timeout
     */
    int timeout() default 2000;

}
