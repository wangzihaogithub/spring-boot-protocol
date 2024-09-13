package com.github.netty.springboot.server;

import com.github.netty.Version;
import com.github.netty.core.AbstractNettyServer;
import com.github.netty.core.ProtocolHandler;
import com.github.netty.core.ServerListener;
import com.github.netty.core.TcpChannel;
import com.github.netty.core.util.SystemPropertyUtil;
import com.github.netty.protocol.DynamicProtocolChannelHandler;
import com.github.netty.springboot.NettyProperties;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelOption;
import io.netty.channel.WriteBufferWaterMark;
import io.netty.util.ResourceLeakDetector;
import io.netty.util.internal.PlatformDependent;
import org.springframework.boot.web.server.WebServer;
import org.springframework.boot.web.server.WebServerException;

import java.net.InetSocketAddress;
import java.util.Collection;
import java.util.function.Supplier;

/**
 * Netty container TCP layer server
 *
 * @author wangzihao
 * 2018/7/14/014
 */
public class NettyTcpServer extends AbstractNettyServer implements WebServer {
    /**
     * Container configuration information
     */
    private final NettyProperties properties;
    private final Collection<ProtocolHandler> protocolHandlers;
    private final Collection<ServerListener> serverListeners;
    private final Supplier<DynamicProtocolChannelHandler> channelHandlerSupplier;

    public NettyTcpServer(InetSocketAddress serverAddress, NettyProperties properties,
                          Collection<ProtocolHandler> protocolHandlers,
                          Collection<ServerListener> serverListeners,
                          Supplier<DynamicProtocolChannelHandler> channelHandlerSupplier) {
        super(serverAddress);
        this.properties = properties;
        this.serverListeners = serverListeners;
        this.protocolHandlers = protocolHandlers;
        this.channelHandlerSupplier = channelHandlerSupplier;
    }

    @Override
    public void start() throws WebServerException {
        try {
            super.setIoRatio(properties.getServerIoRatio());
            super.setIoThreadCount(properties.getServerIoThreads());
            super.init();
            for (ServerListener serverListener : serverListeners) {
                serverListener.onServerStart(this);
            }
            super.run();
        } catch (Exception e) {
            throw new WebServerException("tcp server start fail.. cause = " + e, e);
        }
    }

    @Override
    public void stop() throws WebServerException {
        for (ServerListener serverListener : serverListeners) {
            try {
                serverListener.onServerStop(this);
            } catch (Throwable t) {
                logger.warn("case by stop event [" + t.getMessage() + "]", t);
            }
        }

        try {
            super.stop();
            for (TcpChannel tcpChannel : TcpChannel.getChannels().values()) {
                tcpChannel.close();
            }
        } catch (Exception e) {
            throw new WebServerException(e.getMessage(), e);
        }
    }

    @Override
    protected void startAfter(ChannelFuture future) {
        //Exception thrown
        Throwable cause = future.cause();
        if (cause != null) {
            if (properties.getHttpServlet().isStartupFailExit()) {
                logger.error("server startup fail. cause={}", cause.toString(), cause);
                System.exit(-1);
                return;
            } else {
                PlatformDependent.throwException(cause);
                return;
            }
        }

        logger.info("{} start (version = {}, port = {}, protocol = {}, os = {}) ...",
                getName(),
                Version.getServerNumber(),
                getPort() + "",
                protocolHandlers,
                System.getProperty("os.name")
        );
    }

    @Override
    protected void config(ServerBootstrap bootstrap) throws Exception {
        super.config(bootstrap);
        if (SystemPropertyUtil.get("io.netty.leakDetectionLevel") == null &&
                SystemPropertyUtil.get("io.netty.leakDetection.level") == null) {
            ResourceLeakDetector.setLevel(properties.getResourceLeakDetectorLevel());
        }

        if (SystemPropertyUtil.get("io.netty.maxDirectMemory") == null) {
            long maxDirectMemory = -1;
            System.setProperty("io.netty.maxDirectMemory", String.valueOf(maxDirectMemory));
        }
//        bootstrap.childOption(ChannelOption.WRITE_SPIN_COUNT,Integer.MAX_VALUE);
        bootstrap.childOption(ChannelOption.WRITE_BUFFER_WATER_MARK, new WriteBufferWaterMark(properties.getLowWaterMark(), properties.getHighWaterMark()));
        bootstrap.childOption(ChannelOption.AUTO_CLOSE, properties.isAutoClose());
        if (properties.getSoRcvbuf() > 0) {
            bootstrap.childOption(ChannelOption.SO_RCVBUF, properties.getSoRcvbuf());
        }
        if (properties.getSoSndbuf() > 0) {
            bootstrap.childOption(ChannelOption.SO_SNDBUF, properties.getSoSndbuf());
        }
        if (properties.getSoBacklog() > 0) {
            bootstrap.option(ChannelOption.SO_BACKLOG, properties.getSoBacklog());
        }

        bootstrap.childOption(ChannelOption.TCP_NODELAY, properties.isTcpNodelay());
        for (ServerListener serverListener : serverListeners) {
            serverListener.config(bootstrap);
        }
    }

    /**
     * Initializes the IO executor
     *
     * @return DynamicProtocolChannelHandler
     */
    @Override
    protected ChannelHandler newWorkerChannelHandler() {
        //Dynamic protocol processor
        DynamicProtocolChannelHandler handler = channelHandlerSupplier.get();
        if (properties.isEnableTcpPackageLog()) {
            handler.enableTcpPackageLog(properties.getTcpPackageLogLevel());
        }
        handler.setFirstClientPacketReadTimeoutMs(properties.getFirstClientPacketReadTimeoutMs());
        handler.setMaxConnections(properties.getMaxConnections());
        handler.setProtocolHandlers(protocolHandlers);
        return handler;
    }

    /**
     * Gets the protocol registry list
     *
     * @return protocolHandlers
     */
    public Collection<ProtocolHandler> getProtocolHandlers() {
        return protocolHandlers;
    }

    /**
     * Gets the server listener list
     *
     * @return serverListeners
     */
    public Collection<ServerListener> getServerListeners() {
        return serverListeners;
    }
}
