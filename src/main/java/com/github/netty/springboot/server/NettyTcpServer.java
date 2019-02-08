package com.github.netty.springboot.server;

import com.github.netty.core.AbstractNettyServer;
import com.github.netty.core.ProtocolsRegister;
import com.github.netty.core.util.ApplicationX;
import com.github.netty.core.util.HostUtil;
import com.github.netty.protocol.DynamicProtocolChannelHandler;
import com.github.netty.protocol.NRpcProtocolsRegister;
import com.github.netty.springboot.NettyProperties;
import io.netty.channel.ChannelHandler;
import io.netty.util.concurrent.Future;
import io.netty.util.internal.PlatformDependent;
import org.springframework.boot.web.server.WebServer;
import org.springframework.boot.web.server.WebServerException;

import java.net.InetSocketAddress;
import java.util.*;
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
    private List<ProtocolsRegister> protocolsRegisterList = new ProtocolsRegisterList();

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

            ApplicationX application = properties.getApplication();
            //添加内部rpc协议注册器
            if(application.getBean(NRpcProtocolsRegister.class) == null){
                application.addInstance(new HRpcProtocolsRegisterSpringAdapter(properties.getRpcServerMessageMaxLength(),application));
            }

            List<ProtocolsRegister> inApplicationProtocolsRegisterList = new ArrayList<>(application.findBeanForType(ProtocolsRegister.class));
            inApplicationProtocolsRegisterList.sort(Comparator.comparing(ProtocolsRegister::order));
            for(ProtocolsRegister protocolsRegister : inApplicationProtocolsRegisterList){
                protocolsRegister.onServerStart();
                protocolsRegisterList.add(protocolsRegister);
            }

            protocolsRegisterList.sort(Comparator.comparing(ProtocolsRegister::order));
        } catch (Exception e) {
            throw new WebServerException(e.getMessage(),e);
        }
        super.run();
    }

    @Override
    public void stop() throws WebServerException {
        protocolsRegisterList.sort(Comparator.comparing(ProtocolsRegister::order));
        for(ProtocolsRegister protocolsRegister : protocolsRegisterList){
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
        //有异常抛出
        Throwable cause = future.cause();
        //有异常抛出
        if(cause != null){
            PlatformDependent.throwException(cause);
        }

        List<String> protocols = protocolsRegisterList.stream().map(ProtocolsRegister::getProtocolName).collect(Collectors.toList());
        logger.info("{} start (port = {}, pid = {}, protocol = {}, os = {}) ...",
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
    protected ChannelHandler newInitializerChannelHandler() {
        //动态协议处理器
        return new DynamicProtocolChannelHandler(protocolsRegisterList,properties.isEnableTcpPackageLog());
    }

    /**
     * 获取协议注册器列表
     * @return
     */
    public List<ProtocolsRegister> getProtocolsRegisterList(){
        return protocolsRegisterList;
    }

    /**
     * 协议注册列表
     */
    class ProtocolsRegisterList extends LinkedList<ProtocolsRegister> {
        @Override
        public void add(int index, ProtocolsRegister element) {
            logger.info("addProtocolsRegister({})",element.getProtocolName());
            super.add(index, element);
        }

        @Override
        public boolean add(ProtocolsRegister element) {
            logger.info("addProtocolsRegister({})",element.getProtocolName());
            return super.add(element);
        }

        @Override
        public boolean addAll(Collection<? extends ProtocolsRegister> c) {
            logger.info("addProtocolsRegister({})", c.stream().map(ProtocolsRegister::getProtocolName).collect(Collectors.joining(",")));
            return super.addAll(c);
        }

        @Override
        public boolean addAll(int index, Collection<? extends ProtocolsRegister> c) {
            logger.info("addProtocolsRegister({})", c.stream().map(ProtocolsRegister::getProtocolName).collect(Collectors.joining(",")));
            return super.addAll(index, c);
        }

        @Override
        public boolean remove(Object o) {
            logger.info("removeProtocolsRegister({})",o);
            return super.remove(o);
        }
    }
}
