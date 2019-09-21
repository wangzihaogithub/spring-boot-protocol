package com.github.netty.springboot.server;

import com.github.netty.core.AbstractNettyServer;
import com.github.netty.core.ProtocolHandler;
import com.github.netty.core.ServerListener;
import com.github.netty.core.util.HostUtil;
import com.github.netty.protocol.DynamicProtocolChannelHandler;
import com.github.netty.springboot.NettyProperties;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandler;
import io.netty.util.concurrent.Future;
import io.netty.util.internal.PlatformDependent;
import org.springframework.boot.web.server.WebServer;
import org.springframework.boot.web.server.WebServerException;

import java.net.InetSocketAddress;
import java.util.Collection;

/**
 * Netty container TCP layer server
 * @author wangzihao
 *  2018/7/14/014
 */
public class NettyTcpServer extends AbstractNettyServer implements WebServer {
    /**
     * Container configuration information
     */
    private final NettyProperties properties;
    private Collection<ProtocolHandler> protocolHandlers;
    private Collection<ServerListener> serverListeners;

    public NettyTcpServer(InetSocketAddress serverAddress, NettyProperties properties,
                          Collection<ProtocolHandler> protocolHandlers,
                          Collection<ServerListener> serverListeners){
        super(serverAddress);
        this.properties = properties;
        this.protocolHandlers = protocolHandlers;
        this.serverListeners = serverListeners;
    }

    @Override
    public void start() throws WebServerException {
        try{
            super.setIoRatio(properties.getServerIoRatio());
            super.setIoThreadCount(properties.getServerIoThreads());
            for(ServerListener serverListener : serverListeners){
                serverListener.onServerStart();
            }
            super.run();
        } catch (Exception e) {
            throw new WebServerException(e.getMessage(),e);
        }
    }

    @Override
    public void stop() throws WebServerException {
        for(ServerListener serverListener : serverListeners){
            try {
	            serverListener.onServerStop();
            }catch (Throwable t){
                logger.error("case by stop event [" + t.getMessage()+"]",t);
            }
        }

        try{
            super.stop();
        } catch (Exception e) {
            throw new WebServerException(e.getMessage(),e);
        }
    }

    @Override
    protected void startAfter(ChannelFuture future){
        //Exception thrown
        Throwable cause = future.cause();
        if(cause != null){
            PlatformDependent.throwException(cause);
        }

        logger.info("{} start (port = {}, pid = {}, protocol = {}, os = {}) ...",
                getName(),
                getPort()+"",
                HostUtil.getPid()+"",
                protocolHandlers,
                HostUtil.getOsName()
                );
    }

    /**
     * Initializes the IO executor
     * @return DynamicProtocolChannelHandler
     */
    @Override
    protected ChannelHandler newWorkerChannelHandler() {
        //Dynamic protocol processor
        return new DynamicProtocolChannelHandler(protocolHandlers,properties.isEnableTcpPackageLog(),properties.getTcpPackageLogLevel(),properties.getMaxConnections());
    }

    /**
     * Gets the protocol registry list
     * @return protocolHandlers
     */
    public Collection<ProtocolHandler> getProtocolHandlers(){
        return protocolHandlers;
    }

    /**
     * Gets the server listener list
     * @return serverListeners
     */
    public Collection<ServerListener> getServerListeners() {
        return serverListeners;
    }
}
