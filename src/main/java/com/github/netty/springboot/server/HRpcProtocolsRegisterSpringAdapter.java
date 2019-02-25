package com.github.netty.springboot.server;

import com.github.netty.annotation.Protocol;
import com.github.netty.core.util.*;
import com.github.netty.protocol.NRpcProtocolsRegister;
import org.springframework.web.bind.annotation.*;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.function.Function;

/**
 * Internal RPC protocol registry (spring adapter)
 * @author wangzihao
 */
public class HRpcProtocolsRegisterSpringAdapter extends NRpcProtocolsRegister {
    public HRpcProtocolsRegisterSpringAdapter(int messageMaxLength,ApplicationX application) {
        super(messageMaxLength,application);
    }

    @Override
    public void onServerStart() throws Exception {
        Collection list = super.getApplication().getBeanForAnnotation(Protocol.RpcService.class);
        for(Object serviceImpl : list){
            if(super.existInstance(serviceImpl)){
                continue;
            }
            boolean isAdd = addInstanceForRequestMapping(serviceImpl);
            if(isAdd){
                continue;
            }
            super.addInstance(serviceImpl);
        }
        super.onServerStart();
    }

    private boolean addInstanceForRequestMapping(Object serviceImpl){
        Class annotationOnClass = ReflectUtil.findClassByAnnotation(serviceImpl.getClass(), RequestMapping.class);
        if(annotationOnClass == null){
            return false;
        }

        //In the case of server-side annotations, the information is taken from the annotations
        RequestMapping requestMapping = (RequestMapping) annotationOnClass.getDeclaredAnnotation(RequestMapping.class);
        if(requestMapping == null) {
            return false;
        }

        //Get the service name
        String serviceName = requestMapping.name();
        String[] values = requestMapping.value();
        String[] paths = requestMapping.path();
        if(StringUtil.isEmpty(serviceName) && values.length > 0){
            serviceName = values[0];
        }
        if(StringUtil.isEmpty(serviceName) && paths.length > 0){
            serviceName = paths[0];
        }
        if(StringUtil.isEmpty(serviceName)) {
            return false;
        }

        List<Class<?extends Annotation>> parameterAnnotationClasses = Arrays.asList(
                Protocol.RpcParam.class,RequestParam.class,RequestBody.class, RequestHeader.class,
                PathVariable.class,CookieValue.class, RequestPart.class);
        Function<Method,String[]> methodToParameterNamesFunction;
        boolean hasParameterAnnotation = ReflectUtil.hasParameterAnnotation(serviceImpl.getClass(),parameterAnnotationClasses);
        if(hasParameterAnnotation){
            methodToParameterNamesFunction = new AnnotationMethodToParameterNamesFunction(parameterAnnotationClasses);
        }else {
            methodToParameterNamesFunction = new AsmMethodToParameterNamesFunction();
        }
        super.addInstance(serviceImpl, serviceName,methodToParameterNamesFunction);
        return true;
    }

}
