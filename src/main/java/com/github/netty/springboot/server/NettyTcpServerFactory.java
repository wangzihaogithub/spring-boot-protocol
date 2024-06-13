package com.github.netty.springboot.server;

import com.github.netty.core.Ordered;
import com.github.netty.core.ProtocolHandler;
import com.github.netty.core.ServerListener;
import com.github.netty.core.util.IOUtil;
import com.github.netty.protocol.DynamicProtocolChannelHandler;
import com.github.netty.protocol.HttpServletProtocol;
import com.github.netty.protocol.servlet.ServletContext;
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
import java.util.Objects;
import java.util.TreeSet;
import java.util.function.Supplier;

/**
 * Netty container factory TCP layer server factory
 * <p>
 * EmbeddedWebApplicationContext - createEmbeddedServletContainer
 * ImportAwareBeanPostProcessor
 *
 * @author wangzihao
 * 2018/7/14/014
 */
public class NettyTcpServerFactory
        extends AbstractServletWebServerFactory
        implements ConfigurableReactiveWebServerFactory, ConfigurableServletWebServerFactory {
    private final Collection<ProtocolHandler> protocolHandlers = new TreeSet<>(Ordered.COMPARATOR);
    private final Collection<ServerListener> serverListeners = new TreeSet<>(Ordered.COMPARATOR);
    private final Supplier<DynamicProtocolChannelHandler> channelHandlerSupplier;
    protected NettyProperties properties;

    public NettyTcpServerFactory() {
        this(new NettyProperties(), DynamicProtocolChannelHandler::new);
    }

    public NettyTcpServerFactory(NettyProperties properties,
                                 Supplier<DynamicProtocolChannelHandler> channelHandlerSupplier) {
        this.properties = properties;
        this.channelHandlerSupplier = channelHandlerSupplier;
    }

    public static InetSocketAddress getServerSocketAddress(InetAddress address, int port) {
        if (address == null) {
            try {
                address = InetAddress.getByAddress(new byte[]{0, 0, 0, 0});
                if (!address.isAnyLocalAddress()) {
                    address = InetAddress.getByName("::1");
                }
                if (!address.isAnyLocalAddress()) {
                    address = new InetSocketAddress(port).getAddress();
                }
            } catch (UnknownHostException e) {
                address = new InetSocketAddress(port).getAddress();
            }
        }
        return new InetSocketAddress(address, port);
    }

    /**
     * Reactive container (temporarily replaced by servlets)
     *
     * @param httpHandler httpHandler
     * @return NettyTcpServer
     */
    @Override
    public WebServer getWebServer(HttpHandler httpHandler) {
        try {
            //Server port
            InetSocketAddress serverAddress = getServerSocketAddress(getAddress(), getPort());
            ServletContext servletContext = getServletContext();
            if (servletContext != null) {
                ServletRegistration.Dynamic servletRegistration = servletContext.addServlet("default", new ServletHttpHandlerAdapter(httpHandler));
                servletRegistration.setAsyncSupported(true);
                servletRegistration.addMapping("/");
                servletContext.setServerAddress(serverAddress);
            }
            return new NettyTcpServer(serverAddress, properties, protocolHandlers, serverListeners, channelHandlerSupplier);
        } catch (Exception e) {
            throw new IllegalStateException(e.getMessage(), e);
        }
    }

    /**
     * Get servlet container
     *
     * @param initializers Initialize the
     * @return NettyTcpServer
     */
    @Override
    public WebServer getWebServer(ServletContextInitializer... initializers) {
        ServletContext servletContext = Objects.requireNonNull(getServletContext());
        try {
            //Server port
            InetSocketAddress serverAddress = getServerSocketAddress(getAddress(), getPort());
            servletContext.setServerAddress(serverAddress);
            configurableServletContext();

            //The default servlet
            if (!super.isRegisterDefaultServlet()) {
                servletContext.setDefaultServlet(null);
            }

            //JSP is not supported
            if (super.shouldRegisterJspServlet()) {
                Jsp jsp = getJsp();
            }

            //Initialize the
            for (ServletContextInitializer initializer : super.mergeInitializers(initializers)) {
                initializer.onStartup(servletContext);
            }
            return new NettyTcpServer(serverAddress, properties, protocolHandlers, serverListeners, channelHandlerSupplier);
        } catch (Exception e) {
            throw new IllegalStateException(e.getMessage(), e);
        }
    }

    private void configurableServletContext() throws Exception {
        for (ProtocolHandler protocolHandler : protocolHandlers) {
            if (protocolHandler instanceof HttpServletProtocolSpringAdapter) {
                ((HttpServletProtocolSpringAdapter) protocolHandler).configurableServletContext(this);
            }
        }
    }

    @Override
    public File getDocumentRoot() {
        File dir = properties.getHttpServlet().getBasedir();
        if (dir == null) {
            dir = super.getDocumentRoot();
        }
        if (dir == null) {
            //The temporary directory
            File tempDir = super.createTempDir("netty-docbase");
            dir = tempDir;
            Runtime.getRuntime().addShutdownHook(new Thread(() -> IOUtil.deleteDir(tempDir)));
        }
        return dir;
    }

    public ServletContext getServletContext() {
        for (ProtocolHandler protocolHandler : protocolHandlers) {
            if (protocolHandler instanceof HttpServletProtocol) {
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
}
