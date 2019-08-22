package com.github.netty.springboot.client;

import com.github.netty.springboot.EnableNettyRpcClients;
import com.github.netty.springboot.NettyProperties;
import com.github.netty.springboot.NettyRpcClient;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.BeanClassLoaderAware;
import org.springframework.beans.factory.BeanCreationException;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.BeanFactoryAware;
import org.springframework.beans.factory.annotation.AnnotatedBeanDefinition;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.support.*;
import org.springframework.context.EnvironmentAware;
import org.springframework.context.ResourceLoaderAware;
import org.springframework.context.annotation.AnnotationBeanNameGenerator;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.context.annotation.ImportBeanDefinitionRegistrar;
import org.springframework.context.annotation.Lazy;
import org.springframework.core.env.Environment;
import org.springframework.core.io.ResourceLoader;
import org.springframework.core.type.AnnotationMetadata;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.util.Assert;
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
public class NettyRpcClientsRegistrar implements ImportBeanDefinitionRegistrar,
        ResourceLoaderAware, BeanClassLoaderAware, EnvironmentAware, BeanFactoryAware {
    private ResourceLoader resourceLoader;
    private ClassLoader classLoader;
    private Environment environment;
    private BeanFactory beanFactory;
    private BeanNameGenerator beanNameGenerator = new AnnotationBeanNameGenerator();
    private String nettyRpcClientCanonicalName = NettyRpcClient.class.getCanonicalName();
    private String lazyCanonicalName =  Lazy.class.getCanonicalName();

    public NettyRpcClientsRegistrar() {}

    @Override
    public void registerBeanDefinitions(AnnotationMetadata metadata, BeanDefinitionRegistry registry) {
        ClassPathScanningCandidateComponentProvider scanner = getScanner();
        scanner.setResourceLoader(resourceLoader);
        scanner.addIncludeFilter(new AnnotationTypeFilter(NettyRpcClient.class));
        Map<String, Object> enableNettyRpcClientsAttributes = metadata.getAnnotationAttributes(EnableNettyRpcClients.class.getCanonicalName());

        Set<String> basePackages = getBasePackages(metadata,enableNettyRpcClientsAttributes);
        for (String basePackage : basePackages) {
            Set<BeanDefinition> candidateComponents = scanner.findCandidateComponents(basePackage);
            for (BeanDefinition candidateComponent : candidateComponents) {
                if (candidateComponent instanceof AnnotatedBeanDefinition) {
                    AnnotatedBeanDefinition beanDefinition = (AnnotatedBeanDefinition) candidateComponent;
                    AnnotationMetadata annotationMetadata = beanDefinition.getMetadata();
                    Assert.isTrue(annotationMetadata.isInterface(),"@NettyRpcClient can only be specified on an interface");

                    registerNettyRpcClient(beanDefinition,registry, annotationMetadata);
                }
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

    private void registerNettyRpcClient(AnnotatedBeanDefinition beanDefinition,BeanDefinitionRegistry registry, AnnotationMetadata annotationMetadata)  {
        Map<String, Object> nettyRpcClientAttributes = annotationMetadata.getAnnotationAttributes(nettyRpcClientCanonicalName);
        Map<String, Object> lazyAttributes = annotationMetadata.getAnnotationAttributes(lazyCanonicalName);

        Class<?> beanClass;
        try {
            beanClass = ClassUtils.forName(annotationMetadata.getClassName(), classLoader);
        } catch (ClassNotFoundException e) {
            throw new BeanCreationException("NettyRpcClientsRegistrar failure!  notfound class",e);
        }

        if(lazyAttributes != null && Boolean.FALSE.equals(lazyAttributes.get("value"))){
            beanDefinition.setLazyInit(false);
        }else {
            beanDefinition.setLazyInit(true);
        }
        String serviceId = getServiceId(nettyRpcClientAttributes);
        int timeout = (int)nettyRpcClientAttributes.get("timeout");
        beanDefinition.setPrimary((Boolean)nettyRpcClientAttributes.get("primary"));
        ((AbstractBeanDefinition)beanDefinition).setAutowireMode(AbstractBeanDefinition.AUTOWIRE_BY_TYPE);
        ((AbstractBeanDefinition)beanDefinition).setInstanceSupplier(newInstanceSupplier(beanClass,serviceId,timeout));

        String beanName = generateBeanName(beanDefinition.getBeanClassName());
        registry.registerBeanDefinition(beanName,beanDefinition);
    }

    public <T> Supplier<T> newInstanceSupplier(Class<T> beanClass, String serviceId,int timeout) {
        return ()->{
            NettyProperties nettyConfig = beanFactory.getBean(NettyProperties.class);
            NettyRpcLoadBalanced loadBalanced = beanFactory.getBean(NettyRpcLoadBalanced.class);

            NettyRpcClientProxy nettyRpcClientProxy = new NettyRpcClientProxy(serviceId,null,beanClass,nettyConfig,loadBalanced);
            nettyRpcClientProxy.setTimeout(timeout);
            Object instance = Proxy.newProxyInstance(classLoader,new Class[]{beanClass},nettyRpcClientProxy);
            return (T) instance;
        };
    }

    public String generateBeanName(String beanClassName){
        return Introspector.decapitalize(ClassUtils.getShortName(beanClassName));
    }

    private String getServiceId(Map<String, Object> attributes) {
        String name = (String) attributes.get("serviceId");
        if (!StringUtils.hasText(name)) {
            name = (String) attributes.get("value");
        }
        if (!StringUtils.hasText(name)) {
            name = (String) attributes.get("name");
        }
        name = resolve(name);
        if (!StringUtils.hasText(name)) {
            return "";
        }
        return name;
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

    private String getQualifier(Map<String, Object> client) {
        if (client == null) {
            return null;
        }
        String qualifier = (String) client.get("qualifier");
        if (StringUtils.hasText(qualifier)) {
            return qualifier;
        }
        return null;
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
        this.beanFactory = beanFactory;
    }
}
