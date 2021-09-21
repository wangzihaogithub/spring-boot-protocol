package com.github.netty.core.util;


import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.*;
import java.util.function.Function;

/**
 * Annotation-based method variable parameter name function (because abstract methods cannot be obtained with ASM, only concrete methods can)
 *
 * @author wangzihao
 */
public class AnnotationMethodToParameterNamesFunction implements Function<Method, String[]> {
    private final Collection<Class<? extends Annotation>> parameterAnnotationClasses;
    private final Collection<String> fieldNameList = new LinkedHashSet<>(Arrays.asList("value", "name"));
    private final Map<Integer, Boolean> existAnnotationMap = new WeakHashMap<>(128);

    public AnnotationMethodToParameterNamesFunction(Collection<Class<? extends Annotation>> parameterAnnotationClasses) {
        this.parameterAnnotationClasses = Objects.requireNonNull(parameterAnnotationClasses);
    }

    @SafeVarargs
    public AnnotationMethodToParameterNamesFunction(Class<? extends Annotation>... parameterAnnotationClasses) {
        this.parameterAnnotationClasses = new LinkedHashSet<>(Arrays.asList(parameterAnnotationClasses));
    }

    public Collection<String> getFieldNameList() {
        return fieldNameList;
    }

    public Collection<Class<? extends Annotation>> getParameterAnnotationClasses() {
        return parameterAnnotationClasses;
    }

    @Override
    public String[] apply(Method method) {
        List<String> parameterNames = new ArrayList<>();
        for (Parameter parameter : method.getParameters()) {
            String parameterName = null;
            for (Annotation annotation : parameter.getAnnotations()) {
                parameterName = getName(annotation);
                if (parameterName != null && !parameterName.isEmpty()) {
                    break;
                }
            }
            if (parameterName == null) {
                parameterName = parameter.getName();
            }
            parameterNames.add(parameterName);
        }
        return parameterNames.toArray(new String[0]);
    }

    private String getName(Annotation annotation) {
        Class<? extends Annotation> annotationType = annotation.annotationType();
        for (Class<? extends Annotation> parameterAnnotationClass : parameterAnnotationClasses) {
            int hashCode = Objects.hash(annotationType, parameterAnnotationClass);
            Boolean exist = existAnnotationMap.get(hashCode);
            if (exist == null) {
                exist = Objects.equals(annotationType, parameterAnnotationClass) || ReflectUtil.findAnnotation(annotationType, parameterAnnotationClass) != null;
                existAnnotationMap.put(hashCode, exist ? Boolean.TRUE : Boolean.FALSE);
            }
            if (exist) {
                String methodName = getDirectName(annotation);
                if (methodName != null && !methodName.isEmpty()) {
                    return methodName;
                } else {
                    return annotation.annotationType().getSimpleName();
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
