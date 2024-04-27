package com.github.netty.core.util;

import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * @author wangzihao
 */
public class NamespaceUtil {

    private static Namespace defaultNamespace;

    private NamespaceUtil() {
    }

    public static String newIdName(Class obj) {
        String name = StringUtil.firstUpperCase(obj.getSimpleName());
        return getDefaultNamespace().newIdName(obj, name);
    }

    public static String newIdName(String preName, Class obj) {
        return preName + newIdName(obj);
    }

    private static Namespace getDefaultNamespace() {
        if (defaultNamespace == null) {
            synchronized (NamespaceUtil.class) {
                if (defaultNamespace == null) {
                    defaultNamespace = new Namespace();
                }
            }
        }
        return defaultNamespace;
    }

    static class Namespace {
        private final Map<Object, AtomicInteger> idIncrMap;
        private final Map<Object, Integer> idMap;

        Namespace() {
            idIncrMap = new WeakHashMap<>(16);
            idMap = new WeakHashMap<>(16);
        }

        public String newIdName(Object obj, String name) {
            return name + "_" + newId(obj);
        }

        public int getId(Object obj) {
            Integer id = idMap.get(obj);
            if (id != null) {
                return id;
            }
            id = newId(obj);
            idMap.put(obj, id);
            return id;
        }

        private int newId(Object obj) {
            AtomicInteger atomicInteger = idIncrMap.get(obj);
            if (atomicInteger == null) {
                synchronized (idIncrMap) {
                    atomicInteger = idIncrMap.get(obj);
                    if (atomicInteger == null) {
                        atomicInteger = new AtomicInteger(0);
                        idIncrMap.put(obj, atomicInteger);
                    }
                }
            }
            return atomicInteger.incrementAndGet();
        }

    }

}
