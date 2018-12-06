package com.github.netty.register.servlet.util;

import com.github.netty.core.util.NamespaceUtil;
import com.github.netty.core.util.ReflectUtil;
import org.springframework.cglib.core.DebuggingClassWriter;
import org.springframework.cglib.proxy.Enhancer;
import org.springframework.cglib.proxy.MethodInterceptor;
import org.springframework.cglib.proxy.MethodProxy;

import java.lang.reflect.*;
import java.util.Arrays;

/**
 *
 * @author acer01
 *  2018/7/2/002
 */
@SuppressWarnings("unchecked")
public class ProxyUtil {

    private static boolean enableProxy = false;
    public static boolean isEnableProxy() {
        return enableProxy;
    }

    public static void setEnableProxy(boolean enableProxy) {
        ProxyUtil.enableProxy = enableProxy;
    }

    public static boolean canProxyByCglib(Class clazz) {
        int mod = clazz.getModifiers();
        return !Modifier.isFinal(mod) && !Modifier.isAbstract(mod) && !Modifier.isInterface(mod) && !Modifier.isPrivate(mod);
    }

    //============================newInstance=================================

    public static <T>T newInstance(Class<T> sourceClass){
        try {
            return newInstance(sourceClass,new Class[]{},new Object[]{});
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    private static <T>T newInstance(Class<T> sourceClass, Class[]argTypes, Object[] args){
        try {
            Constructor<T> constructor = sourceClass.getDeclaredConstructor(argTypes);
            T source = constructor.newInstance(args);
            return source;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    //============================CglibProxy=================================

    public static <T>T newProxyByCglib(Class<T> sourceClass, String logName, boolean isEnableLog){
        return newProxyByCglib(sourceClass,logName, isEnableLog,new Class[]{},new Object[]{});
    }

    public static <T>T newProxyByCglib(Class<T> sourceClass){
        String logName = NamespaceUtil.newIdName(sourceClass);
        return newProxyByCglib(sourceClass,logName, true,new Class[]{},new Object[]{});
    }

    public static <T>T newProxyByCglib(Class<T> sourceClass, String logName, boolean isEnableLog, Class[]argTypes, Object[] args){
        if(!isEnableProxy()){
            return newInstance(sourceClass,argTypes,args);
        }

        Enhancer enhancer = new Enhancer();
        enhancer.setSuperclass(sourceClass);
        enhancer.setCallback(new CglibProxy(logName,isEnableLog));

        T proxy = (T) enhancer.create(argTypes,args);
        return proxy;
    }

    public static <T>T newProxyByCglib(Class<T> sourceClass, Class[]argTypes, Object[] args){
        return newProxyByCglib(sourceClass,NamespaceUtil.newIdName(sourceClass),true,argTypes,args);
    }

    public static void setCglibDebugClassWriterPath(String path){
//        "D:\\cglib";
        System.setProperty(DebuggingClassWriter.DEBUG_LOCATION_PROPERTY,path);
    }

    //============================JdkProxy=================================

    public static <T>T newProxyByJdk(Class<T> sourceClass, String logName,boolean isEnableLog){
        try {
            T source = newInstance(sourceClass);
            return newProxyByJdk(source,logName,isEnableLog);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }
    public static <T>T newProxyByJdk(T source){
        return newProxyByJdk(source,source.toString(),true,ReflectUtil.getInterfaces(source.getClass()));
    }

    public static <T>T newProxyByJdk(T source, String logName,boolean isEnableLog,Class[] interfaces){
        if(!isEnableProxy()){
            return source;
        }
        return (T) Proxy.newProxyInstance(
                source.getClass().getClassLoader(),
                interfaces,
                new JdkProxy(source,logName,isEnableLog));
    }

    public static <T>T newProxyByJdk(T source, String logName,boolean isEnableLog){
        return newProxyByJdk(source,logName,isEnableLog, ReflectUtil.getInterfaces(source.getClass()));
    }

    //============================JdkProxy=================================

    public static Object unWrapper(Object object){
        if(ProxyUtil.isProxyByJdk(object)){
            Proxy loopProxy = (Proxy) object;
            InvocationHandler invocationHandler = Proxy.getInvocationHandler(loopProxy);
            if(invocationHandler instanceof JdkProxy){
                JdkProxy jdkProxy = (JdkProxy) invocationHandler;
                return jdkProxy.getSource();
            }
        }

        if(ProxyUtil.isProxyByCglib(object)){

        }
        return object;
    }


    //============================private=================================

    private static boolean isProxyByCglib(Object object){
        String className = object.getClass().getName();
        return (className != null && className.contains("$$"));
    }

    private static boolean isProxyByJdk(Object object){
        return Proxy.isProxyClass(object.getClass());
    }

    static void log(String proxyName, Method method,Object[] args,Object result,long beginTime){
//        if(!method.getName().contains("Heade")){
//            return;
//        }
        if(Arrays.asList("toString","hashCode","equals").contains(method.getName())){
            return;
        }

        long time = System.currentTimeMillis() - beginTime;
        if( time > 10
//                && "getRequestedSessionId".equals(method.getName())
                ) {
            System.out.println("-" + (time) + "---" + Thread.currentThread() + "----" + proxyName + " 方法:" + method.getName() +
                    (args == null || args.length == 0 ? "" : " 参数:"
                           + Arrays.toString(args)
                    ) +
                    " 结果:" + result);
        }
    }

    public static class CglibProxy implements MethodInterceptor {

        private boolean isEnableLog;
        private String name;

        public CglibProxy(String name, boolean isEnableLog) {
            this.isEnableLog = isEnableLog;
            this.name = name;
        }

        @Override
        public Object intercept(Object o, Method method, Object[] args, MethodProxy methodProxy) throws Throwable {
            try {
                if("toString".equals(method.getName())){
                    return name;
                }

                long beginTime = System.currentTimeMillis();
                Object result = methodProxy.invokeSuper(o,args);
                if(isEnableLog){
                    ProxyUtil.log(name,method,args,result,beginTime);
                }
                return result;
            }catch (Throwable t){
                throw t;
            }
        }

        @Override
        public String toString() {
            return "CglibProxy{" +
                    "name='" + name + '\'' +
                    '}';
        }
    }


    public static class JdkProxy implements InvocationHandler {

        private Object source;
        private boolean isEnableLog;
        private String name;

        public JdkProxy(Object source, String name, boolean isEnableLog) {
            this.source = source;
            this.isEnableLog = isEnableLog;
            this.name = name;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            try {
                if("toString".equals(method.getName())){
                    return name;
                }
                long beginTime = System.currentTimeMillis();

                Object result = method.invoke(source,args);
                if(isEnableLog){
                    ProxyUtil.log(name,method,args,result,beginTime);
                }
                return result;
            }catch (Throwable t){
                throw t;
            }
        }

        public Object getSource() {
            return source;
        }

        @Override
        public String toString() {
            return "JdkProxy{" +
                    "name='" + name + '\'' +
                    '}';
        }
    }
}
