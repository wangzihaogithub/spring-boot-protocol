package com.github.netty.springboot.server;

import com.github.netty.core.ProtocolHandler;
import com.github.netty.core.ServerListener;
import com.github.netty.protocol.HttpServletProtocol;
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
import java.net.UnknownHostException;
import java.util.Collection;
import java.util.Collections;
import java.util.Objects;

/**
 * Netty container factory TCP layer server factory
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
    private Collection<ProtocolHandler> protocolHandlers;
    private Collection<ServerListener> serverListeners;

    public NettyTcpServerFactory() {
        this(new NettyProperties(),Collections.emptyList(),Collections.emptyList());
    }

    public NettyTcpServerFactory(NettyProperties properties,
                                 Collection<ProtocolHandler> protocolHandlers,
                                 Collection<ServerListener> serverListeners) {
        this.properties = properties;
        this.protocolHandlers = Objects.requireNonNull(protocolHandlers);
        this.serverListeners = Objects.requireNonNull(serverListeners);
    }

    /**
     * Reactive container (temporarily replaced by servlets)
     * @param httpHandler httpHandler
     * @return NettyTcpServer
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

            //Server port
            InetSocketAddress serverAddress = getServerSocketAddress(getAddress(),getPort());
            return new NettyTcpServer(serverAddress, properties, protocolHandlers,serverListeners);
        }catch (Exception e){
            throw new IllegalStateException(e.getMessage(),e);
        }
    }

    /**
     * Get servlet container
     * @param initializers Initialize the
     * @return NettyTcpServer
     */
    @Override
    public WebServer getWebServer(ServletContextInitializer... initializers) {
        ServletContext servletContext = Objects.requireNonNull(getServletContext());
        try {
            //The default servlet
            if (super.isRegisterDefaultServlet()) {
                servletContext.addServlet("default",new ServletDefaultHttpServlet())
                        .addMapping("/");
            }

            //JSP is not supported
            if(super.shouldRegisterJspServlet()){
                Jsp jsp = getJsp();
            }

            //Initialize the
            for (ServletContextInitializer initializer : super.mergeInitializers(initializers)) {
                initializer.onStartup(servletContext);
            }

            //Server port
            InetSocketAddress serverAddress = getServerSocketAddress(getAddress(),getPort());
            return new NettyTcpServer(serverAddress, properties, protocolHandlers,serverListeners);
        }catch (Exception e){
            throw new IllegalStateException(e.getMessage(),e);
        }
    }

    @Override
    public File getDocumentRoot() {
        File dir = properties.getHttpServlet().getBasedir();
        if(dir == null){
            dir = super.getDocumentRoot();
        }
        if(dir == null){
            //The temporary directory
            dir = super.createTempDir("netty-docbase");
        }
        return dir;
    }

    public ServletContext getServletContext(){
        for(ProtocolHandler protocolHandler : protocolHandlers){
            if(protocolHandler instanceof HttpServletProtocol){
                return ((HttpServletProtocol) protocolHandler).getServletContext();
            }
        }
        return null;
    }

    public Collection<ProtocolHandler> getProtocolHandlers() {
        return protocolHandlers;
    }

    public Collection<ServerListener> getServerListeners() {
        return serverListeners;
    }

    public static InetSocketAddress getServerSocketAddress(InetAddress address,int port) {
        if(address == null) {
            try {
                address = InetAddress.getByAddress(new byte[]{0,0,0,0});
                if(!address.isAnyLocalAddress()){
                    address = InetAddress.getByName("::1");
                }
                if(!address.isAnyLocalAddress()){
                    address = new InetSocketAddress(port).getAddress();
                }
            } catch (UnknownHostException e) {
                address = new InetSocketAddress(port).getAddress();
            }
        }
        return new InetSocketAddress(address,port);
    }
}
