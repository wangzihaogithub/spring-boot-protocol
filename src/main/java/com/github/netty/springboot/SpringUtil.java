package com.github.netty.springboot;

import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.beans.factory.support.DefaultSingletonBeanRegistry;

public class SpringUtil {

    public static boolean isSingletonBean(BeanFactory beanFactory, String beanName) {
        if (beanFactory instanceof DefaultSingletonBeanRegistry
                && ((DefaultSingletonBeanRegistry) beanFactory).isSingletonCurrentlyInCreation(beanName)) {
            return true;
        }
        if (beanFactory instanceof ConfigurableBeanFactory &&
                ((ConfigurableBeanFactory) beanFactory).isCurrentlyInCreation(beanName)) {
            return false;
        }
        return beanFactory.containsBean(beanName) && beanFactory.isSingleton(beanName);
    }
}
