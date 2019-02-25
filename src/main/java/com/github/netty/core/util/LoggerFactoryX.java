package com.github.netty.core.util;

/**
 *
 * @author wangzihao
 * 2018/8/25/025
 */
public class LoggerFactoryX {

    public static LoggerX getLogger(Class clazz){
        return new LoggerX(clazz);
    }

    public static LoggerX getLogger(String clazz){
        return new LoggerX(clazz);
    }
}
