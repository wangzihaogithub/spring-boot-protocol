package com.github.netty.core.util;

/**
 *
 * @author acer01
 * 2018/8/25/025
 */
public class LoggerFactoryX {

    public static LoggerX getLogger(Class clazz){
        return new LoggerX(clazz);
    }

}
