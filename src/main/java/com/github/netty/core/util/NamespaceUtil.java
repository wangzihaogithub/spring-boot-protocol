package com.github.netty.core.util;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * @author 84215
 */
public class NamespaceUtil {

    private static Namespace defaultNamespace;

    private NamespaceUtil(){}

    public static String newIdName(Object obj){
        String startName = obj instanceof Class?((Class) obj).getSimpleName():obj.toString();
        return getDefaultNamespace().newIdName(obj,startName);
    }

    public static String newIdName(Object obj,String name){
        return getDefaultNamespace().newIdName(obj,name);
    }

    public static String getIdName(Object obj,String name){
        return getDefaultNamespace().getIdName(obj,name);
    }

    public static String newIdName(Class obj){
        String name = StringUtil.firstUpperCase(obj.getSimpleName());
        return getDefaultNamespace().newIdName(obj,name);
    }

    public static String newIdName(String preName,Class obj){
        return preName + newIdName(obj);
    }

    public static String getIdNameClass(Object obj,String name){
        return getDefaultNamespace().getIdNameClass(obj,name);
    }

    public static int getId(Object obj){
        return getDefaultNamespace().getId(obj);
    }

    public static int getIdClass(Object obj){
        return getDefaultNamespace().getIdClass(obj);
    }

    private static Namespace getDefaultNamespace() {
        if(defaultNamespace == null){
            synchronized (NamespaceUtil.class) {
                if(defaultNamespace == null) {
                    defaultNamespace = new Namespace();
                }
            }
        }
        return defaultNamespace;
    }

    public static Namespace newNamespace() {
        return new Namespace();
    }

    static class Namespace {
        private final Map<Object,AtomicInteger> idIncrMap;
        private final Map<Object,Integer> idMap;

        Namespace(){
            idIncrMap = new ConcurrentHashMap<>(16);
            idMap = new ConcurrentHashMap<>(16);
        }

        public String newIdName(Object obj,String name){
            return name+"@"+ newId(obj);
        }

        public String getIdName(Object obj,String name){
            return name+"@"+ getId(obj);
        }

        public String getIdNameClass(Object obj,String name){
            return name+"@"+ getIdClass(obj);
        }

        public int getId(Object obj){
            Integer id = idMap.get(obj);
            if (id != null) {
                return id;
            }
            id = newId(obj);
            idMap.put(obj, id);
            return id;
        }

        public int getIdClass(Object obj){
            Integer id = idMap.get(obj);
            if (id != null) {
                return id;
            }
            id = newId(obj.getClass());
            idMap.put(obj, id);
            return id;
        }

        private int newId(Object obj){
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
