package com.github.netty.core.util;

import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.*;

/**
 * ReflectUtil
 * Provide utility functions for calling getter/setter methods, accessing private variables, calling private methods, getting generic type classes, real classes that have been aoped, and so on.
 *
 * @author wangzihao
 */
public class ReflectUtil {

    public static Class[] getInterfaces(Class sourceClass) {
        Set<Class> interfaceList = new HashSet<>();
        for (Class currClass = sourceClass; currClass != null && currClass != Object.class; currClass = currClass.getSuperclass()) {
            Collections.addAll(interfaceList, currClass.getInterfaces());
        }
        if (sourceClass.isInterface()) {
            interfaceList.add(sourceClass);
        }
        return interfaceList.toArray(new Class[interfaceList.size()]);
    }

    public static boolean hasParameterAnnotation(Class sourceClass, Collection<Class<? extends Annotation>> parameterAnnotations) {
        if (parameterAnnotations == null || parameterAnnotations.isEmpty()) {
            return false;
        }
        Class[] interfaces = ReflectUtil.getInterfaces(sourceClass);
        for (Class clazz : interfaces) {
            for (Method method : clazz.getMethods()) {
                for (Parameter parameter : method.getParameters()) {
                    for (Class<? extends Annotation> annotationClass : parameterAnnotations) {
                        Annotation annotation = parameter.getAnnotation(annotationClass);
                        if (annotation != null) {
                            return true;
                        }
                    }
                }
            }
        }
        return false;
    }

    public static Class findClassByAnnotation(Class claz, Class<? extends Annotation> ann) {
        Annotation a;
        //类上找
        for (Class clazz = claz; clazz != null; clazz = clazz.getSuperclass()) {
            if (null != (a = clazz.getAnnotation(ann))) {
                return clazz;
            }
        }

        //接口上找
        Class[] interfaces = getInterfaces(claz);
        for (Class i : interfaces) {
            for (Class clazz = i; clazz != null; clazz = clazz.getSuperclass()) {
                if (null != (a = clazz.getAnnotation(ann))) {
                    return clazz;
                }
            }
        }
        return null;
    }

    public static <A extends Annotation> A findAnnotation(Class claz, Class<A> ann) {
        Annotation a;
        //类上找
        for (Class clazz = claz; clazz != null; clazz = clazz.getSuperclass()) {
            if (null != (a = clazz.getAnnotation(ann))) {
                return (A) a;
            }
        }

        //接口上找
        Class[] interfaces = getInterfaces(claz);
        for (Class i : interfaces) {
            for (Class clazz = i; clazz != null; clazz = clazz.getSuperclass()) {
                if (null != (a = clazz.getAnnotation(ann))) {
                    return (A) a;
                }
            }
        }
        return null;
    }

    public static Map<String, Object> getAnnotationValueMap(Annotation annotation) {
        if (annotation == null) {
            return Collections.emptyMap();
        }
        Method[] declaredMethods = annotation.annotationType().getDeclaredMethods();
        Map<String, Object> map = new HashMap<>(declaredMethods.length);
        for (Method method : declaredMethods) {
            if (method.getParameterCount() != 0 || method.getReturnType() == void.class) {
                continue;
            }
            boolean isAccessible = method.isAccessible();
            try {
                method.setAccessible(true);
                Object value = method.invoke(annotation);
                map.put(method.getName(), value);
            } catch (IllegalAccessException | InvocationTargetException e) {
                //skip
            } finally {
                method.setAccessible(isAccessible);
            }
        }
        return map;
    }

    /**
     * Read the object property values directly, ignoring the private/protected modifier, without going through the getter function.
     *
     * @param obj       obj
     * @param fieldName fieldName
     * @return ObjectValue
     */
    public static Object getFieldValue(final Object obj, final String fieldName) {
        Field field = getAccessibleField(obj, fieldName);
        if (field == null) {
            throw new IllegalArgumentException("in [" + obj.getClass() + "] ，not found [" + fieldName + "]  ");
        }
        Object result = null;
        try {
            result = field.get(obj);
        } catch (IllegalAccessException e) {
            throw new IllegalArgumentException("getFieldValue error:" + e, e);
        }
        return result;
    }

    /**
     * Loop up to get the DeclaredField of the object and force it to be accessible.
     * if the Object cannot be found even if the Object is transformed upward, null will be returned.
     *
     * @param obj       obj
     * @param fieldName fieldName
     * @return Field
     */
    public static Field getAccessibleField(final Object obj, final String fieldName) {
        Objects.requireNonNull(obj, "object can't be null");
        Objects.requireNonNull(fieldName, "fieldName can't be blank");
        for (Class<?> superClass = obj.getClass(); superClass != Object.class; superClass = superClass.getSuperclass()) {
            try {
                Field field = superClass.getDeclaredField(fieldName);
                field.setAccessible(true);
                return field;
            } catch (NoSuchFieldException ignored) {

            }
        }
        return null;
    }

}
