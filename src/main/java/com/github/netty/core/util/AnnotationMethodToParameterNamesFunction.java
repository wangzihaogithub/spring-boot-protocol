package com.github.netty.core.util;

import com.github.netty.core.util.ReflectUtil;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.lang.reflect.Proxy;
import java.util.*;
import java.util.function.Function;

/**
 * 基于注解的方法变参数名函数 （因为抽象方法不能用ASM 获取， 只有具体方法可以）
 * @author 84215
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
                if(!Proxy.isProxyClass(annotation.getClass())) {
                    continue;
                }
                Object memberValues = ReflectUtil.getFieldValue(Proxy.getInvocationHandler(annotation),"memberValues");
                if(memberValues == null || !(memberValues instanceof Map)) {
                    continue;
                }
                Map memberValuesMap = (Map) memberValues;
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
        return parameterNames.toArray(new String[parameterNames.size()]);
    }
}
