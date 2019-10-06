package com.github.netty.core.util;

import java.util.function.Consumer;

/**
 * recycled
 * @author wangzihao
 */
public interface Recyclable {

    /**
     * recycle
     */
    default void recycle(){
        recycle(null);
    }

    /**
     * async recycle
     * @param consumer callback
     * @param <T> last recycle object
     */
    default <T> void recycle(Consumer<T> consumer){
        consumer.accept(null);
    }
}
