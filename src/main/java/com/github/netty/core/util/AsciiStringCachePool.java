package com.github.netty.core.util;

import io.netty.util.AsciiString;

import java.util.Arrays;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @author wangzihao
 */
public class AsciiStringCachePool {
    private static final Map<Integer,AsciiString> CACHE_POOL_MAP;

    public static AsciiString newInstance(byte[] bytes){
        int hash = Arrays.hashCode(bytes);
        AsciiString instance = CACHE_POOL_MAP.get(hash);
        if(instance == null){
            instance = new AsciiString(bytes);
            CACHE_POOL_MAP.put(hash,instance);
        }
        return instance;
    }

    static {
        Map<Integer,AsciiString> map;
        try {
            map = (Map<Integer, AsciiString>) Class.forName("io.netty.util.collection.IntObjectHashMap").getConstructor(int.class).newInstance(32);
        } catch (Exception e) {
            map = new ConcurrentHashMap<>(32);
        }
        CACHE_POOL_MAP = map;
    }
}
