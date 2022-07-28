package com.github.netty.annotation;

import java.lang.annotation.*;

/**
 * RPC method
 */
@Target({ElementType.METHOD, ElementType.ANNOTATION_TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface NRpcMethod {
    /**
     * method name. if empty then java method name
     *
     * @return method name.
     */
    String value() default "";

    /**
     * onTimeoutMayInterruptIfRunning
     *
     * @return true= Interrupt, false=no Interrupt
     */
    boolean timeoutInterrupt() default false;

    /**
     * timeout is -1 then never timeout
     * timeout is 0 then use client timeout
     * timeout other then use server timeout
     *
     * @return method timeout
     */
    int timeout() default -1;
}