package com.github.netty.springboot;

import java.lang.annotation.*;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@Documented
public @interface NettyRpcClient {

	/**
	 * The serviceId is the same as serviceId
	 * @return value
	 */
	String value() default "";
    /**
     * The service ID is the same as value
     * @return serviceId
     */
    String serviceId() default "";

//    Class<?> fallback() default void.class;
    boolean primary() default true;
    String qualifier() default "";

    /**
     * Timeout time (milliseconds)
     * @return timeout
     */
//    int timeout() default RpcService.DEFAULT_TIME_OUT;

}
