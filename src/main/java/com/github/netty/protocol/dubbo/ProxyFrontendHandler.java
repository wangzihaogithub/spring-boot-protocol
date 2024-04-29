package com.github.netty.protocol.dubbo;

import com.github.netty.core.AbstractChannelHandler;
import com.github.netty.protocol.dubbo.packet.BodyFail;
import com.github.netty.protocol.dubbo.packet.BodyHeartBeat;
import com.github.netty.protocol.dubbo.packet.BodyRequest;
import com.github.netty.protocol.dubbo.packet.BodyResponse;
import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.socket.SocketChannel;

import java.net.InetSocketAddress;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

public class ProxyFrontendHandler extends AbstractChannelHandler<DubboPacket, ByteBuf> {
    private final Map<String, InetSocketAddress> serviceAddressMap = new ConcurrentHashMap<>();
    private final Map<String, DubboClient> dubboClients = new ConcurrentHashMap<>();
    private String defaultServiceName;

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
                writeAndFlush(backendClient, packet);
            } catch (DubboClient.DubboConnectException connectException) {
                // 后端连不上
                onBackendConnectException(ctx, packet, backendClient, connectException);
            }
        }
    }

    /**
     * 向后端写数据
     */
    public void writeAndFlush(DubboClient backendClient, DubboPacket packet) {
        SocketChannel clientChannel = backendClient.getChannel();
        clientChannel.write(packet.getHeader().bytes());
        clientChannel.writeAndFlush(packet.getBody().bytes());
    }

    /**
     * 后端连不上
     */
    public void onBackendConnectException(ChannelHandlerContext ctx, DubboPacket packet,
                                          DubboClient backendClient,
                                          DubboClient.DubboConnectException connectException) {
        packet.release();
        ctx.close();
    }

    /**
     * 没配置后端地址
     */
    public void onBackendNonConfig(ChannelHandlerContext ctx, DubboPacket packet) {
        packet.release();
        ctx.close();
    }

    public DubboClient getDubboBackendClient(String serviceName, Channel fronendChannel) {
        if (serviceName == null || serviceName.isEmpty()) {
            return null;
        }
        InetSocketAddress address = getServiceAddress(serviceName);
        if (address == null) {
            return null;
        }
        return dubboClients.computeIfAbsent(serviceName + address, n -> newBackendClient(n, address, fronendChannel));
    }

    /**
     * 新建后端链接
     */
    public DubboClient newBackendClient(String serviceName, InetSocketAddress address, Channel fronendChannel) {
        DubboClient client = new DubboClient(new ProxyBackendHandler(serviceName, fronendChannel));
        client.connect(address);
        return client;
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

}