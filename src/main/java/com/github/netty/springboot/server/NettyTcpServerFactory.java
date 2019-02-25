package com.github.netty.springboot.server;

import com.github.netty.core.ProtocolsRegister;
import com.github.netty.protocol.HttpServletProtocolsRegister;
import com.github.netty.protocol.servlet.ServletContext;
import com.github.netty.protocol.servlet.ServletDefaultHttpServlet;
import com.github.netty.protocol.servlet.ServletRegistration;
import com.github.netty.springboot.NettyProperties;
import org.springframework.boot.web.reactive.server.ConfigurableReactiveWebServerFactory;
import org.springframework.boot.web.server.WebServer;
import org.springframework.boot.web.servlet.ServletContextInitializer;
import org.springframework.boot.web.servlet.server.AbstractServletWebServerFactory;
import org.springframework.boot.web.servlet.server.ConfigurableServletWebServerFactory;
import org.springframework.boot.web.servlet.server.Jsp;
import org.springframework.http.server.reactive.HttpHandler;
import org.springframework.http.server.reactive.ServletHttpHandlerAdapter;

import java.io.File;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.Collection;
import java.util.Collections;
import java.util.Objects;

/**
 * netty容器工厂 tcp层面的服务器工厂
 *
 * EmbeddedWebApplicationContext - createEmbeddedServletContainer
 * ImportAwareBeanPostProcessor
 *
 * @author wangzihao
 *  2018/7/14/014
 */
public class NettyTcpServerFactory
        extends AbstractServletWebServerFactory
        implements ConfigurableReactiveWebServerFactory,ConfigurableServletWebServerFactory {
    protected NettyProperties properties;
    private Collection<ProtocolsRegister> protocolsRegisters;

    public NettyTcpServerFactory() {
        this(new NettyProperties(),Collections.emptyList());
    }

    public NettyTcpServerFactory(NettyProperties properties,Collection<ProtocolsRegister> protocolsRegisters) {
        this.properties = properties;
        this.protocolsRegisters = Objects.requireNonNull(protocolsRegisters);
    }

    /**
     * 获取 Reactive 容器. 暂时用servlet代替
     * @param httpHandler
     * @return
     */
    @Override
    public WebServer getWebServer(HttpHandler httpHandler) {
        try {
            ServletContext servletContext = getServletContext();
            if(servletContext != null) {
                ServletRegistration.Dynamic servletRegistration = servletContext.addServlet("default", new ServletHttpHandlerAdapter(httpHandler));
                servletRegistration.setAsyncSupported(true);
                servletRegistration.addMapping("/");
            }

            //服务器端口
            InetSocketAddress serverAddress = getServerSocketAddress();
            return new NettyTcpServer(serverAddress, properties,protocolsRegisters);
        }catch (Exception e){
            throw new IllegalStateException(e.getMessage(),e);
        }
    }

    /**
     * 获取servlet容器
     * @param initializers 初始化
     * @return
     */
    @Override
    public WebServer getWebServer(ServletContextInitializer... initializers) {
        ServletContext servletContext = Objects.requireNonNull(getServletContext());
        try {
            //默认 servlet
            if (super.isRegisterDefaultServlet()) {
                servletContext.addServlet("default",new ServletDefaultHttpServlet())
                        .addMapping("/");
            }

            //jsp 不支持
            if(super.shouldRegisterJspServlet()){
                Jsp jsp = getJsp();
            }

            //初始化
            for (ServletContextInitializer initializer : super.mergeInitializers(initializers)) {
                initializer.onStartup(servletContext);
            }

            //服务器端口
            InetSocketAddress serverAddress = getServerSocketAddress();
            return new NettyTcpServer(serverAddress, properties,protocolsRegisters);
        }catch (Exception e){
            throw new IllegalStateException(e.getMessage(),e);
        }
    }

    @Override
    public File getDocumentRoot() {
        File dir = properties.getBasedir();
        if(dir == null){
            dir = super.getDocumentRoot();
        }
        if(dir == null){
            //临时目录
            dir = super.createTempDir("nettyx-docbase");
        }
        return dir;
    }

    public ServletContext getServletContext(){
        for(ProtocolsRegister protocolsRegister : protocolsRegisters){
            if(protocolsRegister instanceof HttpServletProtocolsRegister){
                return ((HttpServletProtocolsRegister) protocolsRegister).getServletContext();
            }
        }
        return null;
    }

    public Collection<ProtocolsRegister> getProtocolsRegisters() {
        return protocolsRegisters;
    }

    public InetSocketAddress getServerSocketAddress() {
        InetAddress address = getAddress();
        return new InetSocketAddress(address == null? InetAddress.getLoopbackAddress():address,getPort());
    }
}
