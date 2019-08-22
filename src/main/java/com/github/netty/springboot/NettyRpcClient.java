package com.github.netty.springboot;

import org.springframework.core.annotation.AliasFor;
import org.springframework.stereotype.Component;

import java.lang.annotation.*;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@Documented
@Component
public @interface NettyRpcClient {

	/**
	 * The serviceId is the same as serviceId
	 * @return value
	 */
    @AliasFor("serviceId")
	String value() default "";
    /**
     * The service ID is the same as value
     * @return serviceId
     */
    @AliasFor("value")
    String serviceId() default "";

//    Class<?> fallback() default void.class;
    boolean primary() default true;
//    String qualifier() default "";

    /**
     * Timeout time (milliseconds)
     * @return timeout
     */
    int timeout() default 1000;

}
