package com.github.netty.springboot.server;

import com.github.netty.annotation.Protocol;
import com.github.netty.core.AbstractNettyServer;
import com.github.netty.core.util.*;
import com.github.netty.protocol.NRpcProtocol;
import org.springframework.web.bind.annotation.*;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.function.Function;

/**
 * Internal RPC protocol registry (spring adapter)
 * @author wangzihao
 */
public class HRpcProtocolSpringAdapter extends NRpcProtocol {
    private final ClassFileMethodToParameterNamesFunction classFileMethodToParameterNamesFunction = new ClassFileMethodToParameterNamesFunction();
    private final AnnotationMethodToParameterNamesFunction annotationMethodToParameterNamesFunction = new AnnotationMethodToParameterNamesFunction(
            Protocol.RpcParam.class,RequestParam.class,RequestBody.class, RequestHeader.class,
            PathVariable.class,CookieValue.class, RequestPart.class);

    public HRpcProtocolSpringAdapter(ApplicationX application) {
        super(application);
    }

    @Override
    public <T extends AbstractNettyServer> void onServerStart(T server) throws Exception {
        Collection list = super.getApplication().getBeanForAnnotation(Protocol.RpcService.class);
        getAnnotationMethodToMethodNameFunction().getMethodNameAnnotationClasses().add(RequestMapping.class);

        for(Object serviceImpl : list){
            if(super.existInstance(serviceImpl)){
                continue;
            }

            RequestMapping requestMapping = getRequestMapping(serviceImpl);
            if(requestMapping != null){
                String requestMappingName = getRequestMappingName(requestMapping);
                Function<Method,String[]> methodToParameterNamesFunction = getMethodToParameterNamesFunction(serviceImpl);
                super.addInstance(serviceImpl, requestMappingName, methodToParameterNamesFunction);
            }else {
                super.addInstance(serviceImpl);
            }
        }
        super.onServerStart(server);
    }

    protected Function<Method,String[]> getMethodToParameterNamesFunction(Object serviceImpl){
        if(ReflectUtil.hasParameterAnnotation(serviceImpl.getClass(),annotationMethodToParameterNamesFunction.getParameterAnnotationClasses())){
            return annotationMethodToParameterNamesFunction;
        }else {
            return classFileMethodToParameterNamesFunction;
        }
    }

    protected String getRequestMappingName(RequestMapping requestMapping){
        //Get the service name
        String requestMappingName = requestMapping.name();
        String[] values = requestMapping.value();
        String[] paths = requestMapping.path();
        if(StringUtil.isEmpty(requestMappingName) && values.length > 0){
            requestMappingName = values[0];
        }
        if(StringUtil.isEmpty(requestMappingName) && paths.length > 0){
            requestMappingName = paths[0];
        }
        if(StringUtil.isEmpty(requestMappingName)) {
            throw new IllegalArgumentException("RequestMapping isEmpty!");
        }
        return requestMappingName;
    }

    public AnnotationMethodToParameterNamesFunction getAnnotationMethodToParameterNamesFunction() {
        return annotationMethodToParameterNamesFunction;
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
