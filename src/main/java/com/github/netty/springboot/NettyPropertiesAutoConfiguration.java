package com.github.netty.springboot;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Netty's spring resource files are automatically configured
 * @author wangzihao
 */
@Configuration
public class NettyPropertiesAutoConfiguration {

    @Bean("nettyProperties")
    @ConditionalOnMissingBean(NettyProperties.class)
    public NettyProperties nettyProperties(){
        return new NettyProperties();
    }

}
