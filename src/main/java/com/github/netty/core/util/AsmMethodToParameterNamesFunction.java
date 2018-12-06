package com.github.netty.core.util;

import java.lang.reflect.Method;
import java.util.function.Function;

/**
 * 基于ASM的方法变参数名函数 （注：抽象方法不能用ASM 获取，只有具体方法可以）
 * @author 84215
 */
public class AsmMethodToParameterNamesFunction implements Function<Method,String[]> {
    private ParameterNameDiscoverer parameterNameDiscoverer = new ParameterNameDiscoverer();
    @Override
    public String[] apply(Method method) {
        return parameterNameDiscoverer.getParameterNames(method);
    }
}
