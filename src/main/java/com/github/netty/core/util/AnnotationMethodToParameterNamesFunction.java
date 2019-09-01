package com.github.netty.core.util;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.*;
import java.util.function.Function;

/**
 * Annotation-based method variable parameter name function (because abstract methods cannot be obtained with ASM, only concrete methods can)
 * @author wangzihao
 */
public class AnnotationMethodToParameterNamesFunction implements Function<Method,String[]> {
    private Collection<Class<?extends Annotation>> parameterAnnotationClasses;
    public AnnotationMethodToParameterNamesFunction(Collection<Class<? extends Annotation>> parameterAnnotationClasses) {
        this.parameterAnnotationClasses = Objects.requireNonNull(parameterAnnotationClasses);
    }

    @Override
    public String[] apply(Method method) {
        List<String> parameterNames = new ArrayList<>();
        for(Parameter parameter : method.getParameters()){
            boolean notFound = true;
            for(Class<?extends Annotation> annClass : parameterAnnotationClasses) {
                Annotation annotation = parameter.getAnnotation(annClass);
                if(annotation == null){
                    continue;
                }
                Map memberValuesMap = ReflectUtil.getAnnotationValueMap(annotation);
                Object value = memberValuesMap.get("value");
                if(value == null) {
                    value = memberValuesMap.get("name");
                }
                if(value == null){
                    value = annotation.annotationType().getSimpleName();
                }
                parameterNames.add(value.toString());
                notFound = false;
                break;
            }
            if(notFound){
                parameterNames.add(null);
            }
        }
        return parameterNames.toArray(new String[0]);
    }
}
