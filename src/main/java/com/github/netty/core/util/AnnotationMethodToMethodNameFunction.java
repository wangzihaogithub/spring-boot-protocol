package com.github.netty.core.util;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.*;
import java.util.function.Function;

/**
 * Annotation-based method name function
 *
 * @author wangzihao
 */
public class AnnotationMethodToMethodNameFunction implements Function<Method, String> {
    private final Collection<Class<? extends Annotation>> methodNameAnnotationClasses;
    private final Collection<String> fieldNameList = new LinkedHashSet<>(Arrays.asList("value", "name"));
    private final Map<Integer, Boolean> existAnnotationMap = new WeakHashMap<>(128);

    public AnnotationMethodToMethodNameFunction(Collection<Class<? extends Annotation>> methodNameAnnotationClasses) {
        this.methodNameAnnotationClasses = Objects.requireNonNull(methodNameAnnotationClasses);
    }

    @SafeVarargs
    public AnnotationMethodToMethodNameFunction(Class<? extends Annotation>... methodNameAnnotationClasses) {
        this.methodNameAnnotationClasses = new LinkedHashSet<>(Arrays.asList(methodNameAnnotationClasses));
    }

    public Collection<String> getFieldNameList() {
        return fieldNameList;
    }

    public Collection<Class<? extends Annotation>> getMethodNameAnnotationClasses() {
        return methodNameAnnotationClasses;
    }

    @Override
    public String apply(Method method) {
        for (Annotation annotation : method.getDeclaredAnnotations()) {
            String methodName = getName(annotation);
            if (methodName != null && !methodName.isEmpty()) {
                return methodName;
            }
        }
        return method.getName();
    }

    private String getName(Annotation annotation) {
        Class<? extends Annotation> annotationType = annotation.annotationType();
        for (Class<? extends Annotation> methodNameAnnotationClass : methodNameAnnotationClasses) {
            int hashCode = Objects.hash(annotationType, methodNameAnnotationClass);
            Boolean exist = existAnnotationMap.get(hashCode);
            if (exist == null) {
                exist = Objects.equals(annotationType, methodNameAnnotationClass) || ReflectUtil.findAnnotation(annotationType, methodNameAnnotationClass) != null;
                existAnnotationMap.put(hashCode, exist ? Boolean.TRUE : Boolean.FALSE);
            }
            if (exist) {
                String methodName = getDirectName(annotation);
                if (methodName != null && !methodName.isEmpty()) {
                    return methodName;
                }
            }
        }
        return null;
    }

    private String getDirectName(Annotation annotation) {
        Map memberValuesMap = ReflectUtil.getAnnotationValueMap(annotation);
        for (String fieldName : fieldNameList) {
            Object value = memberValuesMap.get(fieldName);
            if (value instanceof String[]) {
                for (String s : ((String[]) value)) {
                    if (s != null && !"".equals(s)) {
                        return s;
                    }
                }
            } else if (value != null && !"".equals(value)) {
                return value.toString();
            }
        }
        return null;
    }

}
