package com.github.netty.core.util;

/**
 * Wrapper
 *
 * @author wangzihao
 * 2018/7/31/031
 */
public interface Wrapper<T> {

    static <T> T unwrap(T source) {
        if (source instanceof Wrapper) {
            return (T) ((Wrapper) source).unwrap();
        }
        return source;
    }

    /**
     * wrap
     *
     * @param source source object
     */
    void wrap(T source);

    /**
     * get source object
     *
     * @return source object
     */
    T unwrap();
}
