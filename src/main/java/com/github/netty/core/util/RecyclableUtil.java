package com.github.netty.core.util;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.EmptyByteBuf;
import io.netty.util.ReferenceCounted;
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


    public static boolean release(Object obj) {
        if(obj == null){
            return false;
        }
        if(obj instanceof EmptyByteBuf){
            return true;
        }

        if(obj instanceof ReferenceCounted) {
            ReferenceCounted counted = (ReferenceCounted)obj;
            try {
                int refCnt = counted.refCnt();
                if (refCnt > 0) {
                    counted.release();
                    return true;
                }else {
                    return false;
                }
            }catch (IllegalStateException e){
                throw e;
            }
        }
        if(obj instanceof Recyclable){
            ((Recyclable) obj).recycle();
            return true;
        }
        return false;
    }
}
