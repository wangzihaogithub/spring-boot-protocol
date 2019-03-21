package com.github.netty.springboot.server;

import com.github.netty.core.AbstractNettyServer;
import com.github.netty.core.ProtocolsRegister;
import com.github.netty.core.util.HostUtil;
import com.github.netty.protocol.DynamicProtocolChannelHandler;
import com.github.netty.springboot.NettyProperties;
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
    private Collection<ProtocolsRegister> protocolsRegisters;

    public NettyTcpServer(InetSocketAddress serverAddress, NettyProperties properties,Collection<ProtocolsRegister> protocolsRegisters){
        super(serverAddress);
        this.properties = properties;
        this.protocolsRegisters = protocolsRegisters;
    }

    @Override
    public void start() throws WebServerException {
        try{
            super.setIoRatio(properties.getServerIoRatio());
            super.setIoThreadCount(properties.getServerIoThreads());
            for(ProtocolsRegister protocolsRegister : protocolsRegisters){
                protocolsRegister.onServerStart();
            }
            super.run();
        } catch (Exception e) {
            throw new WebServerException(e.getMessage(),e);
        }
    }

    @Override
    public void stop() throws WebServerException {
        for(ProtocolsRegister protocolsRegister : protocolsRegisters){
            try {
                protocolsRegister.onServerStop();
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
    protected void startAfter(Future<?super Void> future){
        //Exception thrown
        Throwable cause = future.cause();
        if(cause != null){
            PlatformDependent.throwException(cause);
        }

        logger.info("{} start (port = {}, pid = {}, protocol = {}, os = {}) ...",
                getName(),
                getPort()+"",
                HostUtil.getPid()+"",
                protocolsRegisters,
                HostUtil.getOsName()
                );
    }

    /**
     * Initializes the IO executor
     * @return DynamicProtocolChannelHandler
     */
    @Override
    protected ChannelHandler newInitializerChannelHandler() {
        //Dynamic protocol processor
        return new DynamicProtocolChannelHandler(protocolsRegisters,properties.isEnableTcpPackageLog(),properties.getMaxConnections());
    }

    /**
     * Gets the protocol registry list
     * @return protocolsRegisters
     */
    public Collection<ProtocolsRegister> getProtocolsRegisters(){
        return protocolsRegisters;
    }

}
