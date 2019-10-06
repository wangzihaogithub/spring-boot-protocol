//package com.github.netty.core.util;
//
//import java.lang.reflect.Method;
//import java.util.function.Function;
//
///**
// * ASM - based method variable parameter name function
// * @author wangzihao
// */
//public class AsmMethodToParameterNamesFunction implements Function<Method,String[]> {
//    private ParameterNameDiscoverer parameterNameDiscoverer = new ParameterNameDiscoverer();
//    @Override
//    public String[] apply(Method method) {
//        return parameterNameDiscoverer.getParameterNames(method);
//    }
//}
