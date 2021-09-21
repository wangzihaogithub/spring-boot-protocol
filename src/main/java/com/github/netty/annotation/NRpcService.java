package com.github.netty.annotation;

import org.springframework.core.annotation.AliasFor;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.ResponseBody;

import java.lang.annotation.*;

/**
 * RPC service note :(to use RPC, the interface or class can be configured with or without annotations, the default is the class name of the interface)
 */
@Target({ElementType.TYPE, ElementType.ANNOTATION_TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Controller
@ResponseBody
public @interface NRpcService {
    /**
     * Default timeout
     */
    int DEFAULT_TIME_OUT = 1000;

    /**
     * Address of the interface
     *
     * @return value
     */
    @AliasFor(annotation = Controller.class)
    String value() default "";

    /**
     * service version
     *
     * @return any str
     */
    String version() default "";

    /**
     * Timeout time (milliseconds)
     *
     * @return timeout
     */
    int timeout() default DEFAULT_TIME_OUT;
}
