package com.github.netty.springboot;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * netty的spring资源文件自动配置
 * @author 84215
 */
@Configuration
public class NettyPropertiesAutoConfiguration {

    @Bean("nettyProperties")
    @ConditionalOnMissingBean(NettyProperties.class)
    public NettyProperties nettyProperties(){
        return new NettyProperties();
    }

}
