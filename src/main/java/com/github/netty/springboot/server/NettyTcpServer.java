package com.github.netty.springboot.server;

import com.github.netty.core.AbstractChannelHandler;
import com.github.netty.core.AbstractNettyServer;
import com.github.netty.core.ProtocolsRegister;
import com.github.netty.core.util.HostUtil;
import com.github.netty.core.util.NettyThreadX;
import com.github.netty.springboot.NettyProperties;
import io.netty.buffer.ByteBuf;
import io.netty.channel.*;
import io.netty.channel.socket.SocketChannel;
import io.netty.util.internal.PlatformDependent;
import org.springframework.boot.web.server.WebServer;
import org.springframework.boot.web.server.WebServerException;

import java.net.InetSocketAddress;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * netty容器 tcp层面的服务器
 *
 * @author acer01
 *  2018/7/14/014
 */
public class NettyTcpServer extends AbstractNettyServer implements WebServer {

    /**
     * 容器配置信息
     */
    private final NettyProperties properties;
    /**
     * servlet线程
     */
    private Thread servletServerThread;
    /**
     * 协议注册器列表
     */
    private List<ProtocolsRegister> protocolsRegisterList = new LinkedList<>();

    public NettyTcpServer(InetSocketAddress serverAddress, NettyProperties properties){
        super(serverAddress);
        this.properties = properties;
    }

    @Override
    public void start() throws WebServerException {
        try{
            super.setIoRatio(properties.getServerIoRatio());
            super.setIoThreadCount(properties.getServerIoThreads());
            for(ProtocolsRegister protocolsRegister : protocolsRegisterList){
                protocolsRegister.onServerStart();
            }

            List<ProtocolsRegister> inApplicationProtocolsRegisterList = new ArrayList<>(properties.getApplication().findBeanForType(ProtocolsRegister.class));
            inApplicationProtocolsRegisterList.sort(Comparator.comparing(ProtocolsRegister::order));
            for(ProtocolsRegister protocolsRegister : inApplicationProtocolsRegisterList){
                protocolsRegister.onServerStart();
                addProtocolsRegister(protocolsRegister);
            }

            protocolsRegisterList.sort(Comparator.comparing(ProtocolsRegister::order));
        } catch (Exception e) {
            throw new WebServerException(e.getMessage(),e);
        }
        servletServerThread = new NettyThreadX(this,getName());
        servletServerThread.start();
    }

    @Override
    public void stop() throws WebServerException {
        try{
            for(ProtocolsRegister protocolsRegister : protocolsRegisterList){
                protocolsRegister.onServerStop();
            }
        } catch (Exception e) {
            throw new WebServerException(e.getMessage(),e);
        }
        super.stop();
        if(servletServerThread != null) {
            servletServerThread.interrupt();
        }
    }

    @Override
    protected void startAfter(Throwable cause) {
        //有异常抛出
        if(cause != null){
            PlatformDependent.throwException(cause);
        }

        List<String> protocols = protocolsRegisterList.stream().map(ProtocolsRegister::getProtocolName).collect(Collectors.toList());
        logger.info("{0} start (port = {1}, pid = {2}, protocol = {3}, os = {4}) ...",
                getName(),
                getPort()+"",
                HostUtil.getPid()+"",
                protocols,
                HostUtil.getOsName()
                );
    }

    @Override
    public int getPort() {
        return super.getPort();
    }

    /**
     * 初始化 IO执行器
     * @return
     */
    @Override
    protected ChannelInitializer<? extends Channel> newInitializerChannelHandler() {
        return new ChannelInitializer<SocketChannel>() {
            ChannelHandler dynamicProtocolHandler = new DynamicProtocolHandler();
            @ChannelHandler.Sharable
            class DynamicProtocolHandler extends AbstractChannelHandler<ByteBuf> {
                private DynamicProtocolHandler() {
                    super(false);
                }

                @Override
                protected void onMessageReceived(ChannelHandlerContext ctx, ByteBuf msg) throws Exception {
                    Channel channel = ctx.channel();
                    channel.pipeline().remove(this);
                    for(ProtocolsRegister protocolsRegister : protocolsRegisterList){
                        if(protocolsRegister.canSupport(msg)){
                            logger.info("Channel protocols register by [{0}]",protocolsRegister.getProtocolName());
                            protocolsRegister.register(channel);
                            channel.pipeline().fireChannelRead(msg);
                            return;
                        }
                    }
                    logger.info("Received no support protocols. message=[{0}]",msg.toString(Charset.forName("UTF-8")));
                }
            }

            @Override
            protected void initChannel(SocketChannel ch) throws Exception {
                ChannelPipeline pipeline = ch.pipeline();
                //HTTP编码解码
                pipeline.addLast("DynamicProtocolHandler", dynamicProtocolHandler);
            }
        };
    }

    /**
     * 添加协议注册器
     * @param protocolsRegister
     */
    public void addProtocolsRegister(ProtocolsRegister protocolsRegister){
        protocolsRegisterList.add(protocolsRegister);
        logger.info("addProtocolsRegister({0})",protocolsRegister.getProtocolName());
    }

}
