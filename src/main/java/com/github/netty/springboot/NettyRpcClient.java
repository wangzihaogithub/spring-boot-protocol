package com.github.netty.springboot;

import java.lang.annotation.*;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@Documented
public @interface NettyRpcClient {

	/**
	 * 服务ID 同 serviceId
	 * @return
	 */
	String value() default "";
    /**
     * 服务ID 同 value
     * @return
     */
    String serviceId() default "";

//    Class<?> fallback() default void.class;
    boolean primary() default true;
    String qualifier() default "";

    /**
     * 超时时间 (毫秒)
     * @return
     */
//    int timeout() default RpcService.DEFAULT_TIME_OUT;

}
