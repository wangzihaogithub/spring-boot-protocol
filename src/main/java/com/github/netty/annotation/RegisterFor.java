package com.github.netty.annotation;

import java.lang.annotation.*;

/**
 * Created by acer01 on 2018/12/5/005.
 */
public class RegisterFor {
    /**
     * rpc参数 注:(客户端的接口上用， 服务端不需要用)
     */
    @Target({ElementType.PARAMETER})
    @Retention(RetentionPolicy.RUNTIME)
    @Documented
    public @interface RpcParam{
        String value() default "";
    }

    /**
     * rpc服务 注:(要使用rpc, 接口或类上可以用这个注解配置, 也可以不打注解， 默认是接口的类名)
     */
    @Target(ElementType.TYPE)
    @Retention(RetentionPolicy.RUNTIME)
    @Documented
    public @interface RpcService{
        /**
         * 接口地址
         * @return
         */
        String value() default "";
        /**
         * 超时时间 (毫秒)
         * @return
         */
        int timeout() default DEFAULT_TIME_OUT;
        /**
         * 默认超时时间
         */
        int DEFAULT_TIME_OUT = 1000;
    }
}
