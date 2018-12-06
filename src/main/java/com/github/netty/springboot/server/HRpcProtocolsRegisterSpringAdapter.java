package com.github.netty.springboot.server;

import com.github.netty.annotation.RegisterFor;
import com.github.netty.core.util.*;
import com.github.netty.register.NRpcProtocolsRegister;
import org.springframework.web.bind.annotation.*;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.function.Function;

/**
 * 内部RPC协议注册器 （适配spring）
 * @author 84215
 */
public class HRpcProtocolsRegisterSpringAdapter extends NRpcProtocolsRegister {
    public HRpcProtocolsRegisterSpringAdapter(int messageMaxLength,ApplicationX application) {
        super(messageMaxLength,application);
    }

    @Override
    public void onServerStart() throws Exception {
        ApplicationX application = getApplication();
        Collection list = application.getBeanForAnnotation(RegisterFor.RpcService.class);
        for(Object serviceImpl : list){
            if(existInstance(serviceImpl)){
                continue;
            }
            boolean isAdd = addInstanceForRequestMapping(serviceImpl);
            if(isAdd){
                continue;
            }
            addInstance(serviceImpl);
        }

        super.onServerStart();
    }

    private boolean addInstanceForRequestMapping(Object serviceImpl){
        List<Class<?extends Annotation>> parameterAnnotationClasses = Arrays.asList(
                RegisterFor.RpcParam.class,RequestParam.class,RequestBody.class, RequestHeader.class,
                PathVariable.class,CookieValue.class, RequestPart.class);
        Class annotationOnClass = ReflectUtil.findClassByAnnotation(serviceImpl.getClass(), RequestMapping.class);
        if(annotationOnClass == null){
            return false;
        }

        //如果是服务端注解的方式，信息就从注解上取
        RequestMapping requestMapping = (RequestMapping) annotationOnClass.getDeclaredAnnotation(RequestMapping.class);
        if(requestMapping == null) {
            return false;
        }

        //获取服务名
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

        Function<Method,String[]> methodToParameterNamesFunction;
        boolean hasParameterAnnotation = ReflectUtil.hasParameterAnnotation(serviceImpl.getClass(),parameterAnnotationClasses);
        if(hasParameterAnnotation){
            methodToParameterNamesFunction = new AnnotationMethodToParameterNamesFunction(parameterAnnotationClasses);
        }else {
            methodToParameterNamesFunction = new AsmMethodToParameterNamesFunction();
        }
        addInstance(serviceImpl, serviceName,methodToParameterNamesFunction);
        return true;
    }

}
