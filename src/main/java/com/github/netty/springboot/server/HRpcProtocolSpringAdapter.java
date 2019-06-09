package com.github.netty.springboot.server;

import com.github.netty.annotation.Protocol;
import com.github.netty.core.util.*;
import com.github.netty.protocol.NRpcProtocol;
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
public class HRpcProtocolSpringAdapter extends NRpcProtocol {
    public HRpcProtocolSpringAdapter(ApplicationX application) {
        super(application);
    }

    @Override
    public void onServerStart() throws Exception {
        Collection list = super.getApplication().getBeanForAnnotation(Protocol.RpcService.class);
        for(Object serviceImpl : list){
            if(super.existInstance(serviceImpl)){
                continue;
            }

            RequestMapping requestMapping = getRequestMapping(serviceImpl);
            if(requestMapping != null){
                String serviceName = getServiceName(requestMapping);
                Function<Method,String[]> methodToParameterNamesFunction = getMethodToParameterNamesFunction(serviceImpl);
                super.addInstance(serviceImpl, serviceName, methodToParameterNamesFunction);
            }else {
                super.addInstance(serviceImpl);
            }
        }
        super.onServerStart();
    }

    protected Function<Method,String[]> getMethodToParameterNamesFunction(Object serviceImpl){
        List<Class<?extends Annotation>> parameterAnnotationClasses = getParameterAnnotationClasses();
        if(ReflectUtil.hasParameterAnnotation(serviceImpl.getClass(),parameterAnnotationClasses)){
            return new AnnotationMethodToParameterNamesFunction(parameterAnnotationClasses);
        }else {
            return new AsmMethodToParameterNamesFunction();
        }
    }

    protected String getServiceName(RequestMapping requestMapping){
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
            throw new IllegalArgumentException("RequestMapping serviceName isEmpty!");
        }
        return serviceName;
    }

    protected List<Class<?extends Annotation>> getParameterAnnotationClasses(){
        return Arrays.asList(
                Protocol.RpcParam.class,RequestParam.class,RequestBody.class, RequestHeader.class,
                PathVariable.class,CookieValue.class, RequestPart.class);
    }

    protected RequestMapping getRequestMapping(Object serviceImpl){
        Class annotationOnClass = ReflectUtil.findClassByAnnotation(serviceImpl.getClass(), RequestMapping.class);
        if(annotationOnClass == null){
            return null;
        }
        //In the case of server-side annotations, the information is taken from the annotations
        return (RequestMapping) annotationOnClass.getDeclaredAnnotation(RequestMapping.class);
    }

}
