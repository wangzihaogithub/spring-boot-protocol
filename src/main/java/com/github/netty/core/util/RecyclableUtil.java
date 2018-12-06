package com.github.netty.core.util;

import io.netty.util.internal.InternalThreadLocalMap;
import io.netty.util.internal.RecyclableArrayList;

import java.util.List;

/**
 * 回收工具
 * @author 84215
 */
public class RecyclableUtil {

    public static <T>List<T> newRecyclableList(int minCapacity){
        RecyclableArrayList finishListeners = RecyclableArrayList.newInstance(minCapacity);
        return (List<T>) finishListeners;
    }


    public static StringBuilder newStringBuilder() {
        return InternalThreadLocalMap.get().stringBuilder();
    }

}
