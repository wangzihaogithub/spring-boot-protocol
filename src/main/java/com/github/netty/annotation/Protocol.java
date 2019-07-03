package com.github.netty.annotation;

import org.springframework.core.annotation.AliasFor;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.ResponseBody;

import java.lang.annotation.*;

/**
 * Created by wangzihao on 2018/12/5/005.
 */
public class Protocol {
    /**
     * RPC parameter note :(used on the client interface, not required on the server)
     */
    @Target({ElementType.PARAMETER})
    @Retention(RetentionPolicy.RUNTIME)
    @Documented
    public @interface RpcParam{
        String value() default "";
    }

    /**
     * RPC service note :(to use RPC, the interface or class can be configured with or without annotations, the default is the class name of the interface)
     */
    @Target(ElementType.TYPE)
    @Retention(RetentionPolicy.RUNTIME)
    @Documented
    @Controller
    @ResponseBody
    public @interface RpcService{
        /**
         * Address of the interface
         * @return value
         */
        @AliasFor(annotation = Controller.class)
        String value() default "";
        /**
         * Timeout time (milliseconds)
         * @return timeout
         */
        int timeout() default DEFAULT_TIME_OUT;
        /**
         * Default timeout
         */
        int DEFAULT_TIME_OUT = 1000;
    }
}
