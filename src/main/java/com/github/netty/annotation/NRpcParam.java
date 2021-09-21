package com.github.netty.annotation;

import java.lang.annotation.*;

/**
 * RPC parameter note :(used on the client interface, not required on the server)
 */
@Target({ElementType.PARAMETER, ElementType.ANNOTATION_TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface NRpcParam {
    String value() default "";
}