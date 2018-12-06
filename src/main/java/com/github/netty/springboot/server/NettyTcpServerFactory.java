package com.github.netty.springboot.server;

import com.github.netty.register.servlet.ServletContext;
import com.github.netty.register.servlet.ServletDefaultHttpServlet;
import com.github.netty.springboot.NettyProperties;
import org.springframework.boot.web.reactive.server.ConfigurableReactiveWebServerFactory;
import org.springframework.boot.web.server.WebServer;
import org.springframework.boot.web.servlet.ServletContextInitializer;
import org.springframework.boot.web.servlet.server.AbstractServletWebServerFactory;
import org.springframework.boot.web.servlet.server.ConfigurableServletWebServerFactory;
import org.springframework.boot.web.servlet.server.Jsp;
import org.springframework.context.ResourceLoaderAware;
import org.springframework.core.io.ResourceLoader;
import org.springframework.http.server.reactive.HttpHandler;
import org.springframework.util.ClassUtils;

import java.io.File;
import java.net.InetAddress;
import java.net.InetSocketAddress;

/**
 * netty容器工厂 tcp层面的服务器工厂
 *
 * EmbeddedWebApplicationContext - createEmbeddedServletContainer
 * ImportAwareBeanPostProcessor
 *
 * @author acer01
 *  2018/7/14/014
 */
public class NettyTcpServerFactory
        extends AbstractServletWebServerFactory
        implements ConfigurableReactiveWebServerFactory,ConfigurableServletWebServerFactory,ResourceLoaderAware {
    protected ResourceLoader resourceLoader;
    protected NettyProperties properties;

    public NettyTcpServerFactory() {
        this(new NettyProperties());
    }

    public NettyTcpServerFactory(NettyProperties properties) {
        this.properties = properties;
    }

    // TODO: 2018/11/12/012  getWebServer(HttpHandler httpHandler)
    @Override
    public WebServer getWebServer(HttpHandler httpHandler) {
        return null;
    }

    /**
     * 获取servlet容器
     * @param initializers 初始化
     * @return
     */
    @Override
    public WebServer getWebServer(ServletContextInitializer... initializers) {
        try {
            //临时目录
            File documentRoot = getValidDocumentRoot();
            File docBase = documentRoot != null? documentRoot : createTempDir("nettyx-docbase");

            //服务器端口
            InetSocketAddress serverAddress = new InetSocketAddress(getAddress() == null? InetAddress.getLoopbackAddress():getAddress(),getPort());
            ClassLoader classLoader = resourceLoader != null ? resourceLoader.getClassLoader() : ClassUtils.getDefaultClassLoader();
            NettyTcpServer server = new NettyTcpServer(serverAddress, properties);

            ServletContext servletContext = new ServletContext(serverAddress,classLoader,docBase.getAbsolutePath());

            //配置tcp服务器
            configurableTcpServer(server,servletContext);

            //默认 servlet
            if (isRegisterDefaultServlet()) {
                ServletDefaultHttpServlet defaultServlet = new ServletDefaultHttpServlet();
                servletContext.addServlet("default",defaultServlet).addMapping("/");
            }

            //jsp
            Jsp jsp = getJsp();
            if(shouldRegisterJspServlet()){
                //
            }

            //初始化
            ServletContextInitializer[] servletContextInitializers = mergeInitializers(initializers);
            for (ServletContextInitializer initializer : servletContextInitializers) {
                initializer.onStartup(servletContext);
            }

            return server;
        }catch (Exception e){
            throw new IllegalStateException(e.getMessage(),e);
        }
    }

    /**
     * 配置tpc服务器
     * @param tcpServer
     * @param servletContext
     * @throws Exception
     */
    protected void configurableTcpServer(NettyTcpServer tcpServer,ServletContext servletContext) throws Exception {
        //添加httpServlet协议注册器
        tcpServer.addProtocolsRegister(new HttpServletProtocolsRegisterSpringAdapter(properties,servletContext,this));
        //添加内部rpc协议注册器
        tcpServer.addProtocolsRegister(new HRpcProtocolsRegisterSpringAdapter(properties.getRpcServerMessageMaxLength(),properties.getApplication()));
    }

    @Override
    public void setResourceLoader(ResourceLoader resourceLoader) {
        this.resourceLoader = resourceLoader;
    }
}
