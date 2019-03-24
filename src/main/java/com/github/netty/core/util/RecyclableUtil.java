package com.github.netty.core.util;

import io.netty.buffer.ByteBuf;
import io.netty.util.internal.InternalThreadLocalMap;
import io.netty.util.internal.RecyclableArrayList;

import java.util.List;

/**
 * RecyclableUtil
 * @author wangzihao
 */
public class RecyclableUtil {

    public static <T>List<T> newRecyclableList(int minCapacity){
        RecyclableArrayList finishListeners = RecyclableArrayList.newInstance(minCapacity);
        return (List<T>) finishListeners;
    }

    public static StringBuilder newStringBuilder() {
        return InternalThreadLocalMap.get().stringBuilder();
    }

    public static ByteBuf newReadOnlyBuffer(byte[] bytes) {
        return ReadOnlyPooledHeapByteBuf.newInstance(bytes);
    }
}
