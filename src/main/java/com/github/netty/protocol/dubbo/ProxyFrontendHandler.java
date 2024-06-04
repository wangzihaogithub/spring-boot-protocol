package com.github.netty.protocol.dubbo;

import com.github.netty.core.AbstractChannelHandler;
import com.github.netty.core.util.AntPathMatcher;
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
import java.util.concurrent.CopyOnWriteArrayList;

public class ProxyFrontendHandler extends AbstractChannelHandler<DubboPacket, ByteBuf> {
    private static final List<ProxyFrontendHandler> ACTIVE_LIST = Collections.synchronizedList(new ArrayList<>(100));
    private static final AntPathMatcher PATH_MATCHER = new AntPathMatcher(".", Boolean.TRUE);
    private final Map<InetSocketAddress, DubboClient> backendClientMap = new ConcurrentHashMap<>();
    protected volatile Throwable backendException;
    private List<Application> applicationList = new CopyOnWriteArrayList<>();
    private ChannelHandlerContext ctx;

    public ProxyFrontendHandler() {
    }

    public ProxyFrontendHandler(Collection<Application> applicationList) {
        this.applicationList.addAll(applicationList);
    }

    public static List<ProxyFrontendHandler> getActiveList() {
        return ACTIVE_LIST;
    }

    /**
     * 选择一个后端应用
     *
     * @param packet dubbo请求
     * @return 后端应用
     */
    public Application selectBackendApplication(DubboPacket packet) {
        Application defaultApplication = null;
        for (Application application : applicationList) {
            // 1. path match
            String[] pathPatterns = application.getPathPatterns();
            String requestPath = packet.getRequestPath();
            if (requestPath != null && pathPatterns != null) {
                for (String pathPattern : pathPatterns) {
                    if (PATH_MATCHER.match(pathPattern, requestPath)) {
                        return application;
                    }
                }
            }

            // 2. attachment match
            String applicationName = application.getName();
            if (applicationName != null && !applicationName.isEmpty()) {
                String attachmentValue = packet.getAttachmentValue(application.getAttachmentApplicationName());
                if (applicationName.equals(attachmentValue)) {
                    return application;
                }
            }

            // 3. default
            if (defaultApplication == null && application.isDefaultApplication()) {
                defaultApplication = application;
            }
        }
        return defaultApplication;
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
        Application backendApplication = selectBackendApplication(packet);
        DubboClient backendClient = getBackendClient(backendApplication, ctx.channel());
        if (backendClient == null) {
            // 没有配置后端
            onBackendNonConfig(ctx, packet, backendApplication);
        } else {
            try {
                // 向后端写数据
                writeAndFlush(ctx, backendClient, packet, backendApplication);
            } catch (DubboClient.DubboConnectException connectException) {
                // 后端连不上
                onBackendConnectException(ctx, packet, backendClient, backendApplication, connectException);
            }
        }
    }

    /**
     * 向后端写数据
     */
    protected void writeAndFlush(ChannelHandlerContext ctx, DubboClient backendClient, DubboPacket packet, Application backendApplication) {
        SocketChannel backendChannel = backendClient.getChannel();
        ChannelFutureListener closeOnFailure = new ChannelFutureListener() {
            @Override
            public void operationComplete(ChannelFuture future) {
                if (!future.isSuccess()) {
                    onBackendWriteException(ctx, packet, backendClient, backendApplication, future.cause());
                }
            }
        };
        backendChannel.write(packet.getHeader().encode());
        backendChannel.writeAndFlush(packet.getBody().encode()).addListener(closeOnFailure);
    }

    /**
     * 后端写不过去
     */
    protected void onBackendWriteException(ChannelHandlerContext ctx, DubboPacket packet,
                                           DubboClient backendClient,
                                           Application backendApplication,
                                           Throwable cause) {
        if (logger.isWarnEnabled()) {
            logger.warn("onBackendWriteException {} , {}, {}", backendApplication, ctx.channel(), backendClient, cause);
        }
        this.backendException = cause;
        writeProxyError(ctx, packet, Constant.SERVICE_ERROR, "dubbo proxy backend write exception! service(" + backendApplication + ")");
    }

    /**
     * 后端连不上
     */
    protected void onBackendConnectException(ChannelHandlerContext ctx, DubboPacket packet,
                                             DubboClient backendClient,
                                             Application application,
                                             DubboClient.DubboConnectException connectException) {
        if (logger.isWarnEnabled()) {
            logger.warn("onBackendConnectException {} , {}, {}", application, ctx.channel(), backendClient, connectException);
        }
        this.backendException = connectException;
        writeProxyError(ctx, packet, Constant.SERVICE_ERROR, "dubbo proxy backend connect exception! service(" + application + "/" + backendClient.getRemoteAddress() + "(DOWN))");
    }

    /**
     * 没配置后端地址
     */
    protected void onBackendNonConfig(ChannelHandlerContext ctx, DubboPacket packet, Application application) {
        if (logger.isWarnEnabled()) {
            logger.warn("onBackendNonConfig {} , {}, {}", application, ctx.channel(), packet);
        }
        writeProxyError(ctx, packet, Constant.SERVICE_NOT_FOUND, "dubbo proxy backend non config exception! service(" + application + ")");
    }

    /**
     * 返回代理错误信息
     */
    protected void writeProxyError(ChannelHandlerContext ctx, DubboPacket packet, byte errorCode, String errorMessage) {
        try {
            ByteBuf rejectPacket = packet.buildErrorPacket(ctx.alloc(), errorCode, errorMessage);
            ctx.writeAndFlush(rejectPacket);
        } finally {
            packet.release();
        }
    }

    /**
     * 后端状态发生变化
     */
    protected void onChangeClientState(DubboClient.State state, DubboClient client) {

    }

    public DubboClient getBackendClient(Application application, Channel fronendChannel) {
        if (application == null) {
            return null;
        }
        InetSocketAddress address = application.getAddress();
        if (address == null) {
            return null;
        }
        Collection<String> applicationNames = getApplicationNames(address);
        return backendClientMap.computeIfAbsent(address, n -> newBackendClient(applicationNames, address, fronendChannel));
    }

    public DubboClient newBackendClient(Collection<String> applicationNames, InetSocketAddress address, Channel fronendChannel) {
        DubboClient client = new DubboClient(String.join(",", applicationNames), new ProxyBackendHandler(applicationNames, fronendChannel));
        client.connect(address);
        client.setStateConsumer(this::onChangeClientState);
        return client;
    }

    public Collection<String> getApplicationNames(InetSocketAddress address) {
        Set<String> list = new LinkedHashSet<>(3);
        for (Application application : applicationList) {
            if (Objects.equals(address, application.getAddress())) {
                list.add(application.getDisplayName());
            }
        }
        return list;
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
        for (Application application : applicationList) {
            String name = application.getDisplayName();
            DubboClient dubboClient = backendClientMap.get(application.getAddress());
            if (dubboClient == null) {
                joiner.add(name + "/NA");
            } else {
                joiner.add(name + "/" + dubboClient.getRemoteAddress() + "(" + dubboClient.getState() + ")");
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

    public Throwable getBackendException() {
        return backendException;
    }

    public void addApplication(Collection<Application> list) {
        applicationList.addAll(list);
    }

    public void addApplication(Application application) {
        applicationList.add(application);
    }

    public List<Application> getApplicationList() {
        return applicationList;
    }

    public void setApplicationList(Collection<Application> applicationList) {
        Objects.requireNonNull(applicationList);
        this.applicationList = new CopyOnWriteArrayList<>(applicationList);
    }
}