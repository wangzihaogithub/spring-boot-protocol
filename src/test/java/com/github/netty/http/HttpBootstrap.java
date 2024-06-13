package com.github.netty.http;

import com.github.netty.springboot.EnableNettyEmbedded;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.web.context.ServletConfigAware;
import org.springframework.web.context.ServletContextAware;

import javax.servlet.ServletConfig;
import javax.servlet.ServletContext;
import java.net.URL;

@EnableNettyEmbedded
@SpringBootApplication
public class HttpBootstrap {
    private static final URL CONFIG_URL = HttpBootstrap.class.getResource(
            "/http/application.yaml");

    public static void main(String[] args) {
        System.getProperties().put("spring.config.location", CONFIG_URL.toString());
        SpringApplication.run(HttpBootstrap.class, args);
    }

    @Bean
    public ServletContextAware servletContextAware(){
        return new ServletContextAware() {
            @Override
            public void setServletContext(ServletContext servletContext) {
                String string = servletContext.getRealPath("/");
                System.out.println("string = " + string);
            }
        };
    }

    @Bean
    public ServletConfigAware servletConfigAware(){
        return new ServletConfigAware() {
            @Override
            public void setServletConfig(ServletConfig servletConfig) {
                String servletName = servletConfig.getServletName();
                System.out.println("servletName = " + servletName);
            }
        };
    }

}
