package com.github.netty.protocol.dubbo;

import com.github.netty.core.AbstractChannelHandler;
import com.github.netty.protocol.dubbo.packet.BodyFail;
import com.github.netty.protocol.dubbo.packet.BodyHeartBeat;
import com.github.netty.protocol.dubbo.packet.BodyRequest;
import com.github.netty.protocol.dubbo.packet.BodyResponse;
import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.socket.SocketChannel;

import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class ProxyFrontendHandler extends AbstractChannelHandler<DubboPacket, ByteBuf> {
    private static final List<ProxyFrontendHandler> ACTIVE_LIST = Collections.synchronizedList(new ArrayList<>(100));
    private final Map<String, InetSocketAddress> serviceAddressMap = new ConcurrentHashMap<>();
    private final Map<InetSocketAddress, DubboClient> backendClientMap = new ConcurrentHashMap<>();
    protected volatile Throwable backendException;
    private String defaultServiceName;
    private ChannelHandlerContext ctx;

    public static List<ProxyFrontendHandler> getActiveList() {
        return ACTIVE_LIST;
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        super.channelActive(ctx);
        this.ctx = ctx;
        ACTIVE_LIST.add(this);
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        super.channelInactive(ctx);
        closeBackend();
        ACTIVE_LIST.remove(this);
    }

    @Override
    protected void onMessageReceived(ChannelHandlerContext ctx, DubboPacket packet) throws Exception {
        String backendServiceName = getBackendServiceName(packet);
        if (backendServiceName == null || backendServiceName.isEmpty()) {
            backendServiceName = this.defaultServiceName;
        }
        DubboClient backendClient = getDubboBackendClient(backendServiceName, ctx.channel());
        if (backendClient == null) {
            // 没有配置后端
            onBackendNonConfig(ctx, packet);
        } else {
            try {
                // 向后端写数据
                writeAndFlush(ctx, backendClient, packet);
            } catch (DubboClient.DubboConnectException connectException) {
                // 后端连不上
                onBackendConnectException(ctx, packet, backendClient, connectException);
            }
        }
    }

    /**
     * 向后端写数据
     */
    protected void writeAndFlush(ChannelHandlerContext ctx, DubboClient backendClient, DubboPacket packet) {
        SocketChannel clientChannel = backendClient.getChannel();
        ChannelFutureListener closeOnFailure = new ChannelFutureListener() {
            @Override
            public void operationComplete(ChannelFuture future) {
                if (!future.isSuccess()) {
                    onBackendWriteException(ctx, packet, backendClient, future.cause());
                }
            }
        };
        clientChannel.write(packet.getHeader().bytes()).addListener(closeOnFailure);
        clientChannel.writeAndFlush(packet.getBody().bytes()).addListener(closeOnFailure);
    }

    /**
     * 后端写不过去
     */
    protected void onBackendWriteException(ChannelHandlerContext ctx, DubboPacket packet,
                                           DubboClient backendClient,
                                           Throwable cause) {
        if (logger.isWarnEnabled()) {
            logger.warn("onBackendWriteException {} , {}", ctx.channel(), backendClient, cause);
        }
        this.backendException = cause;
    }

    /**
     * 后端连不上
     */
    protected void onBackendConnectException(ChannelHandlerContext ctx, DubboPacket packet,
                                             DubboClient backendClient,
                                             DubboClient.DubboConnectException connectException) {
        if (logger.isWarnEnabled()) {
            logger.warn("onBackendConnectException {} , {}", ctx.channel(), backendClient, connectException);
        }
        this.backendException = connectException;
        packet.release();
    }

    /**
     * 没配置后端地址
     */
    protected void onBackendNonConfig(ChannelHandlerContext ctx, DubboPacket packet) {
        if (logger.isWarnEnabled()) {
            logger.warn("onBackendNonConfig {} , {}", ctx.channel(), packet);
        }
        packet.release();
    }

    /**
     * 后端状态发生变化
     */
    protected void onChangeClientState(DubboClient.State state, DubboClient client) {

    }

    public DubboClient getDubboBackendClient(String serviceName, Channel fronendChannel) {
        if (serviceName == null || serviceName.isEmpty()) {
            return null;
        }
        InetSocketAddress address = getServiceAddress(serviceName);
        if (address == null) {
            return null;
        }
        List<String> serviceNames = getServiceNames(address);
        return backendClientMap.computeIfAbsent(address, n -> newBackendClient(serviceNames, address, fronendChannel));
    }

    /**
     * 新建后端链接
     */
    public DubboClient newBackendClient(List<String> serviceNames, InetSocketAddress address, Channel fronendChannel) {
        DubboClient client = new DubboClient(String.join(",", serviceNames), new ProxyBackendHandler(serviceNames, fronendChannel));
        client.connect(address);
        client.setStateConsumer(this::onChangeClientState);
        return client;
    }

    public Map<String, InetSocketAddress> getServiceAddressMap() {
        return Collections.unmodifiableMap(serviceAddressMap);
    }

    public List<String> getServiceNames(InetSocketAddress address) {
        List<String> list = new ArrayList<>(2);
        for (Map.Entry<String, InetSocketAddress> entry : serviceAddressMap.entrySet()) {
            if (Objects.equals(address, entry.getValue())) {
                list.add(entry.getKey());
            }
        }
        return list;
    }

    public String getBackendServiceName(DubboPacket packet) {
        Body body = packet.getBody();
        String serviceName = null;
        if (body instanceof BodyRequest) {
            Map<String, Object> attachments = ((BodyRequest) body).getAttachments();
            if (attachments != null) {
                serviceName = Objects.toString(attachments.get("remote.application"), null);
            }
        } else if (body instanceof BodyResponse) {
            Map<String, Object> attachments = ((BodyResponse) body).getAttachments();
            if (attachments != null) {
                serviceName = Objects.toString(attachments.get("remote.application"), null);
            }
        } else if (body instanceof BodyHeartBeat) {

        } else if (body instanceof BodyFail) {

        } else {
        }
        return serviceName;
    }

    public void closeBackend() {
        ArrayList<DubboClient> dubboClients = new ArrayList<>(backendClientMap.values());
        backendClientMap.clear();
        for (DubboClient dubboClient : dubboClients) {
            dubboClient.close();
        }
    }

    @Override
    public String toString() {
        List<String> joiner = new ArrayList<>();
        for (Map.Entry<String, InetSocketAddress> entry : serviceAddressMap.entrySet()) {
            String serviceName = entry.getKey();
            DubboClient dubboClient = backendClientMap.get(entry.getValue());
            if (dubboClient == null) {
                joiner.add(serviceName + "/NA");
            } else {
                joiner.add(serviceName + "/" + dubboClient.getRemoteAddress() + "(" + dubboClient.getState() + ")");
            }
        }
        return "DubboProxy{" + getRemoteAddress() + " => " + joiner + "}";
    }

    public Map<InetSocketAddress, DubboClient> getBackendClientMap() {
        return backendClientMap;
    }

    public boolean isActive() {
        return ctx != null && ctx.channel().isActive();
    }

    public SocketAddress getRemoteAddress() {
        return ctx == null ? null : ctx.channel().remoteAddress();
    }

    public ChannelHandlerContext getChannel() {
        return ctx;
    }

    public InetSocketAddress getServiceAddress(String serviceName) {
        return serviceAddressMap.get(serviceName);
    }

    public InetSocketAddress putServiceAddress(String serviceName, InetSocketAddress address) {
        return serviceAddressMap.put(serviceName, address);
    }

    public String getDefaultServiceName() {
        return defaultServiceName;
    }

    public void setDefaultServiceName(String defaultServiceName) {
        this.defaultServiceName = defaultServiceName;
    }

    public Throwable getBackendException() {
        return backendException;
    }

}