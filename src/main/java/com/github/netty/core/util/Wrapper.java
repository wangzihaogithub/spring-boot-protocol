package com.github.netty.core.util;

/**
 * 包装者
 *
 * @author acer01
 * 2018/7/31/031
 */
public interface Wrapper<T> {

    /**
     * 包装
     * @param source 源对象
     */
    void wrap(T source);

    /**
     * 获取源对象
     * @return 源对象
     */
    T unwrap();

    static <T>T unwrap(T source){
        if(source instanceof Wrapper){
            return (T) ((Wrapper) source).unwrap();
        }
        return source;
    }
}
