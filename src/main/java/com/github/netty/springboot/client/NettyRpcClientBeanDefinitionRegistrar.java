package com.github.netty.springboot.client;

import com.github.netty.springboot.EnableNettyRpcClients;
import com.github.netty.springboot.NettyProperties;
import com.github.netty.springboot.NettyRpcClient;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.*;
import org.springframework.beans.factory.annotation.AnnotatedBeanDefinition;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.support.AbstractBeanDefinition;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.context.EnvironmentAware;
import org.springframework.context.ResourceLoaderAware;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.context.annotation.ImportBeanDefinitionRegistrar;
import org.springframework.context.annotation.Lazy;
import org.springframework.core.env.Environment;
import org.springframework.core.io.ResourceLoader;
import org.springframework.core.type.AnnotationMetadata;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.util.ClassUtils;
import org.springframework.util.StringUtils;

import java.beans.Introspector;
import java.lang.annotation.Annotation;
import java.lang.reflect.Proxy;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

/**
 * @author wangzihao
 */
public class NettyRpcClientBeanDefinitionRegistrar implements ImportBeanDefinitionRegistrar,
        ResourceLoaderAware, BeanClassLoaderAware, EnvironmentAware, BeanFactoryAware {
    private ResourceLoader resourceLoader;
    private ClassLoader classLoader;
    private Environment environment;
    private String enableNettyRpcClientsCanonicalName = EnableNettyRpcClients.class.getCanonicalName();
    private String nettyRpcClientCanonicalName = NettyRpcClient.class.getCanonicalName();
    private String lazyCanonicalName = Lazy.class.getCanonicalName();
	private ObjectFactory<NettyRpcLoadBalanced> nettyRpcLoadBalancedProvider;
	private ObjectFactory<NettyProperties> nettyPropertiesProvider;

    public NettyRpcClientBeanDefinitionRegistrar() {}

    @Override
    public void registerBeanDefinitions(AnnotationMetadata metadata, BeanDefinitionRegistry registry) {
        ClassPathScanningCandidateComponentProvider scanner = getScanner();
        scanner.setResourceLoader(resourceLoader);
        scanner.addIncludeFilter(new AnnotationTypeFilter(NettyRpcClient.class));
        Map<String, Object> enableNettyRpcClientsAttributes = metadata.getAnnotationAttributes(enableNettyRpcClientsCanonicalName);

        for (String basePackage : getBasePackages(metadata,enableNettyRpcClientsAttributes)) {
            for (BeanDefinition candidateComponent : scanner.findCandidateComponents(basePackage)) {
                if (!(candidateComponent instanceof AnnotatedBeanDefinition)) {
                    continue;
                }

                AnnotatedBeanDefinition beanDefinition = (AnnotatedBeanDefinition) candidateComponent;
                if(!beanDefinition.getMetadata().isInterface()) {
                    throw new IllegalArgumentException("@NettyRpcClient can only be specified on an interface");
                }
                registerNettyRpcClient(beanDefinition,registry);
            }
        }
    }

    @Override
    public void setResourceLoader(ResourceLoader resourceLoader) {
        this.resourceLoader = resourceLoader;
    }

    @Override
    public void setBeanClassLoader(ClassLoader classLoader) {
        this.classLoader = classLoader;
    }

    @Override
    public void setEnvironment(Environment environment) {
        this.environment = environment;
    }

    private void registerNettyRpcClient(AnnotatedBeanDefinition beanDefinition,BeanDefinitionRegistry registry)  {
        AnnotationMetadata metadata = beanDefinition.getMetadata();
        Map<String, Object> nettyRpcClientAttributes = metadata.getAnnotationAttributes(nettyRpcClientCanonicalName);
        Map<String, Object> lazyAttributes = metadata.getAnnotationAttributes(lazyCanonicalName);

        Class<?> beanClass;
        try {
            beanClass = ClassUtils.forName(metadata.getClassName(), classLoader);
        } catch (ClassNotFoundException e) {
            throw new BeanCreationException("NettyRpcClientsRegistrar failure! notfound class",e);
        }

        String serviceName = resolve((String) nettyRpcClientAttributes.get("serviceImplName"));
        beanDefinition.setLazyInit(lazyAttributes == null || Boolean.TRUE.equals(lazyAttributes.get("value")));
        ((AbstractBeanDefinition)beanDefinition).setAutowireMode(AbstractBeanDefinition.AUTOWIRE_BY_TYPE);
        ((AbstractBeanDefinition)beanDefinition).setInstanceSupplier(newInstanceSupplier(beanClass,serviceName,(int)nettyRpcClientAttributes.get("timeout")));

        String beanName = generateBeanName(beanDefinition.getBeanClassName());
        registry.registerBeanDefinition(beanName,beanDefinition);
    }

    public <T> Supplier<T> newInstanceSupplier(Class<T> beanClass, String serviceName,int timeout) {
        return ()->{
            NettyProperties nettyProperties = nettyPropertiesProvider.getObject();

            NettyRpcClientProxy nettyRpcClientProxy = new NettyRpcClientProxy(serviceName,null,
                    beanClass,nettyProperties,
		            nettyRpcLoadBalancedProvider::getObject);
            nettyRpcClientProxy.setTimeout(timeout);
            Object instance = Proxy.newProxyInstance(classLoader,new Class[]{beanClass},nettyRpcClientProxy);
            return (T) instance;
        };
    }

    public String generateBeanName(String beanClassName){
        return Introspector.decapitalize(ClassUtils.getShortName(beanClassName));
    }

    private String resolve(String value) {
        if (StringUtils.hasText(value)) {
            return this.environment.resolvePlaceholders(value);
        }
        return value;
    }

    protected Set<String> getBasePackages(AnnotationMetadata importingClassMetadata,Map<String, Object> enableNettyRpcClientsAttributes) {
        Set<String> basePackages = new HashSet<>();
        if(enableNettyRpcClientsAttributes != null) {
            for (String pkg : (String[]) enableNettyRpcClientsAttributes.get("value")) {
                if (StringUtils.hasText(pkg)) {
                    basePackages.add(pkg);
                }
            }
            for (String pkg : (String[]) enableNettyRpcClientsAttributes.get("basePackages")) {
                if (StringUtils.hasText(pkg)) {
                    basePackages.add(pkg);
                }
            }
        }

        if (basePackages.isEmpty()) {
            basePackages.add(
                    ClassUtils.getPackageName(importingClassMetadata.getClassName()));
        }
        return basePackages;
    }

    private ClassPathScanningCandidateComponentProvider getScanner() {
        return new ClassPathScanningCandidateComponentProvider(false, this.environment) {
            @Override
            protected boolean isCandidateComponent(
                    AnnotatedBeanDefinition beanDefinition) {
                if (beanDefinition.getMetadata().isIndependent()) {
                    // TODO until SPR-11711 will be resolved
                    if (beanDefinition.getMetadata().isInterface()
                            && beanDefinition.getMetadata()
                            .getInterfaceNames().length == 1
                            && Annotation.class.getName().equals(beanDefinition
                            .getMetadata().getInterfaceNames()[0])) {
                        try {
                            Class<?> target = ClassUtils.forName(
                                    beanDefinition.getMetadata().getClassName(),
                                    classLoader);
                            return !target.isAnnotation();
                        }
                        catch (Exception ex) {
                            this.logger.error(
                                    "Could not load target class: "
                                            + beanDefinition.getMetadata().getClassName(),
                                    ex);

                        }
                    }
                    return true;
                }
                return false;

            }
        };
    }

    @Override
    public void setBeanFactory(BeanFactory beanFactory) throws BeansException {
	    this.nettyRpcLoadBalancedProvider = beanFactory.getBeanProvider(NettyRpcLoadBalanced.class);
	    this.nettyPropertiesProvider = beanFactory.getBeanProvider(NettyProperties.class);;
    }
}
