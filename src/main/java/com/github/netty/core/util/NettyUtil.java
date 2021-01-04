package com.github.netty.core.util;

import io.netty.util.internal.PlatformDependent;

import java.lang.reflect.Field;
import java.util.concurrent.atomic.AtomicLong;

public class NettyUtil {
    private static final float THRESHOLD = SystemPropertyUtil.getFloat("netty-core.directBufferThreshold",0.7F);
    private static final long MAX_DIRECT_MEMORY;
    private static final AtomicLong DIRECT_MEMORY_COUNTER;

    public static long freeDirectMemory(){
        if(DIRECT_MEMORY_COUNTER == null){
            return -1;
        }else {
            return MAX_DIRECT_MEMORY - DIRECT_MEMORY_COUNTER.get();
        }
    }

    static {
        AtomicLong directMemoryCounter;
        try {
            Field field = PlatformDependent.class.getDeclaredField("DIRECT_MEMORY_COUNTER");
            field.setAccessible(true);
            directMemoryCounter = (AtomicLong) field.get(null);
        } catch (Throwable e) {
            directMemoryCounter = null;
        }
        DIRECT_MEMORY_COUNTER = directMemoryCounter;

        MAX_DIRECT_MEMORY = (long) (PlatformDependent.maxDirectMemory() * THRESHOLD);
    }

}
