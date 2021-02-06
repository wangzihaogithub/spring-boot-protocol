/*
 * Copyright 2014 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License, version 2.0 (the
 * "License"); you may not use this file except in compliance with the License. You may obtain a
 * copy of the License at:
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package com.github.netty.protocol.servlet.http2;

import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.Unpooled;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.http.*;
import io.netty.handler.codec.http2.*;
import io.netty.handler.logging.LogLevel;
import io.netty.handler.ssl.*;
import io.netty.handler.ssl.ApplicationProtocolConfig.Protocol;
import io.netty.handler.ssl.ApplicationProtocolConfig.SelectedListenerFailureBehavior;
import io.netty.handler.ssl.ApplicationProtocolConfig.SelectorFailureBehavior;
import io.netty.handler.ssl.util.InsecureTrustManagerFactory;
import io.netty.handler.timeout.ReadTimeoutException;
import io.netty.handler.timeout.WriteTimeoutException;
import io.netty.util.concurrent.*;
import io.netty.util.internal.PlatformDependent;
import io.netty.util.internal.logging.InternalLogger;
import io.netty.util.internal.logging.InternalLoggerFactory;

import javax.net.ssl.SSLException;
import javax.net.ssl.SSLProtocolException;
import java.io.Closeable;
import java.io.Flushable;
import java.net.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.stream.Stream;

/**
 * 一个客户端维护一个http长连接
 *
 * <p>
 * An HTTP2 client that allows you to send HTTP2 frames to a server using HTTP1-style approaches
 * (via {@link InboundHttp2ToHttpAdapter}). Inbound and outbound
 * frames are logged.
 * When run from the command-line, sends a single HEADERS frame to the server and gets back
 * a "Hello World" response.
 * See the ./http2/helloworld/frame/client/ example for a HTTP2 client example which does not use
 * HTTP1-style objects and patterns.
 * <p>
 * 1. 包协议
 * +-----------------------------------------------------------------------------+
 * |                 Length (24) 帧总长度                                       |
 * +---------------+---------------+---------------------------------------------+
 * |    Type (8) {@link Http2FrameTypes}   |   Flags (8) {@link Http2Flags}   |
 * +-+-------------+---------------+-------------------------------------------+
 * |R 保留字段 |   Stream Identifier (31) {@link Http2FrameStream#id()}  |
 * +=+===========================================================------------==+
 * | Frame Payload (0...) ...不同的FrameTypes类型,不同的字段.{@link Http2Frame}   |
 * +---------------------------------------------------------------------------+
 * <p>
 * Length：帧有效载荷的长度，表示为无符号的24位整数。除非接收方为SETTINGS_MAX_FRAME_SIZE设置了较大的值，否则不得发送大于2 ^ 14（16,384）的值。帧头的9个八位字节不包含在该值中。
 * <p>
 * Type：帧的8位类型。帧类型决定帧的格式和语义。实现必须忽略并丢弃任何类型未知的帧。
 * <p>
 * Flags：为帧类型专用的布尔标志保留的8位字段。标志被分配特定于指定帧类型的语义。没有为特定帧类型定义语义的标志务必被忽略，并且在发送时务必保持未设置（0x0）。
 * <p>
 * R：保留的1位字段。该位的语义是未定义的，并且该位必须在发送时保持未设置（0x0），并且在接收时必须忽略。
 * <p>
 * Stream Identifier：流标识符 表示为一个无符号的31位整数。值0x0保留给与整个连接相关联的帧，而不是单个流。
 * <p>
 * 详情参看RFC标准 https://www.rfc-editor.org/rfc/pdfrfc/rfc7540.txt.pdf
 *
 * @author wangzihaogithub 2021年2月3日14:11:17
 * @see Http2FrameTypes (这个类型位由服务端返回, 告诉客户端, 这个包是什么类型)
 * @see Http2Flags (这个状态位由服务端返回, 告诉客户端, 是否结束header, 是否结束body)
 */
public class NettyHttp2Client {
    private static final InternalLogger logger = InternalLoggerFactory.getInstance(NettyHttp2Client.class);
    private final AtomicInteger streamIdIncr = new AtomicInteger(3);
    private final Queue<H2Response> pendingWriteQueue = new LinkedBlockingQueue<>(Integer.MAX_VALUE);
    private final AtomicBoolean connectIng = new AtomicBoolean(false);
    private final HttpScheme scheme;
    private final Bootstrap bootstrap;
    private final Http2Handler http2Handler;
    private final InetSocketAddress remoteAddress;
    private final URL url;
    private final LinkedList<H2Response> removeStreamIdList = new LinkedList<>();
    private int connectCount = 0;
    private int connectTimeout = 5000;
    private int requestTimeout = 5000;
    private int maxPendingSize = 5000;
    private int timeoutCheckScheduleInterval = 30;
    private long beginConnectTimestamp;
    private long endConnectTimestamp;
    private Http2Settings settings;
    private ScheduledFuture<?> timeoutScheduledFuture;
    private volatile boolean connectAfterAutoFlush = true;
    private volatile Channel channel;
    private volatile Promise<Channel> connectPromise;
    private volatile Promise<Long> closePromise;

    public NettyHttp2Client(String domain) throws SSLException, MalformedURLException, UnknownHostException {
        this(new URL(domain), new NioEventLoopGroup(0));
    }

    public NettyHttp2Client(URL domain) throws SSLException, UnknownHostException {
        this(domain, new NioEventLoopGroup(0));
    }

    public NettyHttp2Client(URL domain, EventLoopGroup worker) throws UnknownHostException, SSLException {
        this.url = domain;
        this.scheme = "https".equalsIgnoreCase(domain.getProtocol()) ? HttpScheme.HTTPS : HttpScheme.HTTP;
        this.remoteAddress = newInetSocketAddress(domain, scheme.port());
        this.http2Handler = new Http2Handler(scheme, Integer.MAX_VALUE, connectTimeout, remoteAddress);
        this.bootstrap = new Bootstrap();
        this.bootstrap.group(worker);
        this.bootstrap.channel(NioSocketChannel.class);
        this.bootstrap.option(ChannelOption.SO_KEEPALIVE, true);
        this.bootstrap.handler(http2Handler);
    }

    public static void main(String[] args) throws Exception {
        NettyHttp2Client http2Client = new NettyHttp2Client("https://maimai.cn")
                .logger(LogLevel.INFO)
                .awaitConnectIfNoActive();

        for (int i = 0; i < 50000; i++) {
            DefaultFullHttpRequest request = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET,
                    "/sdk/company/is_admin", Unpooled.EMPTY_BUFFER);
            http2Client.write(request).onSuccess(FullHttpResponse::release);
        }

        List<H2Response> httpPromises = http2Client.flush().get();

        Long closeTime = http2Client.close(true).get();
        System.out.printf("connectTime = %d, closeTime = %d \n",
                http2Client.getEndConnectTimestamp() - http2Client.getBeginConnectTimestamp(), closeTime);
    }

    public NettyHttp2Client requestTimeout(int requestTimeout) {
        this.requestTimeout = requestTimeout;
        return this;
    }

    public ScheduledFuture<?> getTimeoutScheduledFuture() {
        return timeoutScheduledFuture;
    }

    public int getRequestTimeout() {
        return requestTimeout;
    }

    public NettyHttp2Client connectTimeout(int connectTimeout) {
        this.connectTimeout = connectTimeout;
        return this;
    }

    public int getConnectTimeout() {
        return connectTimeout;
    }

    public NettyHttp2Client logger(LogLevel logLevel) {
        http2Handler.setLogger(new Http2FrameLogger(logLevel, NettyHttp2Client.class));
        return this;
    }

    public NettyHttp2Client maxPendingSize(int maxPendingSize) {
        this.maxPendingSize = maxPendingSize;
        return this;
    }

    public NettyHttp2Client awaitConnectIfNoActive() throws ConnectException {
        if (!isActive()) {
            try {
                this.connectAfterAutoFlush = false;
                Promise<Channel> connect = connect();
                connect.await(connectTimeout, TimeUnit.MILLISECONDS);
                if (connect.isDone()) {
                    if (connect.isSuccess()) {
                        this.channel = connect.getNow();
                        this.settings = http2Handler.settingsHandler().getHttp2Settings();
                    } else {
                        throw new ConnectException(remoteAddress.toString() + "," + connect.cause());
                    }
                } else {
                    throw new ConnectException(remoteAddress.toString());
                }
            } catch (InterruptedException e) {
                close();
                throw new ConnectException(remoteAddress.toString() + "," + e);
            }
        }
        return this;
    }

    private static InetSocketAddress newInetSocketAddress(URL url, int defaultPort) throws UnknownHostException {
        InetAddress inetAddress = InetAddress.getByName(url.getHost());
        int port = url.getPort();
        return new InetSocketAddress(inetAddress, port == -1 ? defaultPort : port);
    }

    public URL getUrl() {
        return url;
    }

    public long getBeginConnectTimestamp() {
        return beginConnectTimestamp;
    }

    public long getEndConnectTimestamp() {
        return endConnectTimestamp;
    }

    public boolean isActive() {
        Channel channel = this.channel;
        return channel != null && channel.isActive();
    }

    @Override
    protected void finalize() throws Throwable {
        try {
            close(false, "GC finalize");
        } finally {
            super.finalize();
        }
    }

    public Promise<Channel> connect() {
        if (isClose()) {
            throw new IllegalStateException("http2 close. " + remoteAddress);
        }
        if (connectIng.compareAndSet(false, true)) {
            this.connectCount++;
            this.connectPromise = newPromise();
            this.beginConnectTimestamp = System.currentTimeMillis();
            this.endConnectTimestamp = 0;
            this.bootstrap.connect(remoteAddress)
                    .addListener((ChannelFutureListener) future -> onHttp2Connect(future, connectPromise));
        }
        return connectPromise;
    }

    protected void onConnectFail(Throwable cause) {
        this.endConnectTimestamp = System.currentTimeMillis();
        this.channel = null;
        this.settings = null;
        this.connectIng.set(false);
        this.connectPromise = null;
        this.connectAfterAutoFlush = true;
        if (cause != null) {
            logger.warn("http2 connect fail. remoteAddress = '{}',  connectTimeout = {}/ms, connectTime = {}/ms. cause = {}",
                    remoteAddress, connectTimeout, endConnectTimestamp - beginConnectTimestamp, cause.toString());
        }
    }

    protected void onConnectSuccess(Channel channel) {
        this.endConnectTimestamp = System.currentTimeMillis();
        this.channel = channel;
        this.settings = http2Handler.settingsHandler().getHttp2Settings();
        this.connectIng.set(false);
        this.connectPromise = null;
        if (connectAfterAutoFlush && !pendingWriteQueue.isEmpty()) {
            flush();
        }
        this.connectAfterAutoFlush = true;
        scheduleTimeoutCheck(timeoutCheckScheduleInterval, TimeUnit.MILLISECONDS);
    }

    public NettyHttp2Client timeoutCheckScheduleInterval(int timeoutCheckScheduleInterval) {
        if (this.timeoutCheckScheduleInterval == timeoutCheckScheduleInterval) {
            return this;
        }
        this.timeoutCheckScheduleInterval = timeoutCheckScheduleInterval;
        if (timeoutScheduledFuture != null) {
            scheduleTimeoutCheck(timeoutCheckScheduleInterval, TimeUnit.MILLISECONDS);
        }
        return this;
    }

    public ScheduledFuture scheduleTimeoutCheck(int timeoutCheckScheduleInterval, TimeUnit timeUnit) {
        ScheduledFuture<?> oldScheduledFuture = this.timeoutScheduledFuture;
        if (oldScheduledFuture != null) {
            oldScheduledFuture.cancel(false);
        }
        this.timeoutScheduledFuture = channel.eventLoop().scheduleWithFixedDelay(this::checkTimeout, timeoutCheckScheduleInterval, timeoutCheckScheduleInterval, timeUnit);
        return oldScheduledFuture;
    }

    public void checkTimeout() {
        // write timeout
        for (H2Response value : pendingWriteQueue) {
            if (!value.isDone() && value.isTimeout()) {
                value.tryFailure(WriteTimeoutException.INSTANCE);
            }
        }

        // read timeout
        Map<Integer, H2Response> streamIdPromiseMap = http2Handler.responseHandler.getStreamIdPromiseMap();
        for (H2Response value : streamIdPromiseMap.values()) {
            if (!value.isDone() && value.isTimeout()) {
                value.tryFailure(ReadTimeoutException.INSTANCE);
                removeStreamIdList.add(value);
            }
        }
        H2Response remove;
        while ((remove = removeStreamIdList.poll()) != null) {
            streamIdPromiseMap.remove(remove.streamId);
        }
    }

    public void onHttp2Connect(ChannelFuture future, Promise<Channel> connectPromise) {
        if (future.isSuccess()) {
            http2Handler.settingsHandler().promise()
                    .addListener((ChannelFutureListener) settingsFuture
                            -> onHttp2Setting(http2Handler.settingsHandler(), settingsFuture, connectPromise));
        } else {
            onConnectFail(future.cause());
            connectPromise.tryFailure(future.cause());
        }
    }

    public void onHttp2Setting(Http2SettingsHandler settingsHandler, ChannelFuture future, Promise<Channel> connectPromise) throws ConnectException {
        if (future.isSuccess()) {
            onConnectSuccess(future.channel());
            connectPromise.trySuccess(future.channel());
        } else {
            onConnectFail(future.cause());
            connectPromise.tryFailure(future.cause());
        }
    }

    /**
     * 用完需要使用者主动释放内存,不然会内存泄漏 {@link FullHttpResponse#release()}
     *
     * @param request        请求
     * @param requestTimeout 超时时间, 小于0则永不超时 {@link H2Response#isTimeout()}
     * @return 未来的响应
     */
    public H2Response write(FullHttpRequest request, int requestTimeout) {
        if (isClose()) {
            throw new IllegalStateException("http2 close. " + remoteAddress + ", request = " + request);
        }

        HttpHeaders headers = request.headers();
        headers.set(HttpHeaderNames.HOST, getHostString(remoteAddress) + ":" + remoteAddress.getPort());
        headers.set(HttpConversionUtil.ExtensionHeaderNames.SCHEME.text(), scheme.name());
        headers.add(HttpHeaderNames.ACCEPT_ENCODING, HttpHeaderValues.GZIP);
        headers.add(HttpHeaderNames.ACCEPT_ENCODING, HttpHeaderValues.DEFLATE);

        H2Response promise = new H2Response(this, request, requestTimeout, newStreamId());
        int pendingSize = pendingWriteQueue.size();
        if (pendingSize < maxPendingSize) {
            pendingWriteQueue.offer(promise);
        } else {
            try {
                promise.await();
            } catch (InterruptedException ignored) {
            } finally {
                logger.warn("out of max pending size. trigger once block flush method. pendingSize = {}, maxPendingSize={}, blockTime = {}/ms",
                        pendingSize, maxPendingSize, promise.getExecuteTime());
            }
        }
        return promise;
    }

    /**
     * 用完需要使用者主动释放内存,不然会内存泄漏 {@link FullHttpResponse#release()}
     *
     * @param request 请求
     * @return 未来的响应
     */
    public H2Response write(FullHttpRequest request) {
        return write(request, requestTimeout);
    }

    /**
     * 用完需要使用者主动释放内存,不然会内存泄漏 {@link FullHttpResponse#release()}
     *
     * @param request 请求
     * @return 未来的响应
     */
    public H2Response writeAndFlush(FullHttpRequest request) {
        return writeAndFlush(request, requestTimeout);
    }

    /**
     * 用完需要使用者主动释放内存,不然会内存泄漏 {@link FullHttpResponse#release()}
     *
     * @param request        请求
     * @param requestTimeout 超时时间, 小于0则永不超时 {@link H2Response#isTimeout()}
     * @return 未来的响应
     */
    public H2Response writeAndFlush(FullHttpRequest request, int requestTimeout) {
        H2Response write = write(request, requestTimeout);
        if (isActive()) {
            flush();
        } else {
            connectAfterAutoFlush = true;
            connect();
        }
        return write;
    }

    private <T> Promise<T> newPromise() {
        return new DefaultPromise<>(bootstrap.config().group().next());
    }

    public Promise<List<H2Response>> flush() {
        return flush(newPromise());
    }

    public boolean isClose() {
        return closePromise != null;
    }

    public Map<Integer, H2Response> getStreamIdPromiseMap() {
        return http2Handler.responseHandler().getStreamIdPromiseMap();
    }

    public Promise<List<H2Response>> flush(Promise<List<H2Response>> promise) {
        if (pendingWriteQueue.isEmpty()) {
            promise.trySuccess(Collections.emptyList());
            return promise;
        }
        if (!isActive()) {
            if (connectCount == 0) {
                this.connectAfterAutoFlush = false;
                try {
                    connect().sync();
                } catch (InterruptedException ignored) {
                }
            }
            if (!isActive()) {
                promise.tryFailure(new ConnectException(remoteAddress.toString()));
                return promise;
            }
        }
        H2Response responsePromise;
        AtomicInteger total = new AtomicInteger();
        EventLoop executor = bootstrap.config().group().next();
        List<H2Response> list = Collections.synchronizedList(new ArrayList<>(pendingWriteQueue.size()));
        while ((responsePromise = pendingWriteQueue.poll()) != null) {
            if (responsePromise.isDone()) {
                continue;
            }
            if (responsePromise.flush.compareAndSet(false, true)) {
                total.incrementAndGet();
                responsePromise.executor = executor;
                writeChannel(responsePromise);

                final H2Response finalResponsePromise = responsePromise;
                responsePromise.addListener(future -> {
                    list.add(finalResponsePromise);
                    if (list.size() >= total.get()) {
                        promise.trySuccess(list);
                    }
                });
            }
        }

        if (total.get() == 0) {
            promise.trySuccess(list);
        } else {
            if (isActive()) {
                channel.flush();
            } else {
                connect();
                promise.tryFailure(new ConnectException(remoteAddress.toString()));
            }
        }
        return promise;
    }

    private void writeChannel(H2Response httpPromise) {
        if (httpPromise.isTimeout()) {
            httpPromise.tryFailure(WriteTimeoutException.INSTANCE);
        } else {
            http2Handler.responseHandler().put(httpPromise.streamId, httpPromise);
            httpPromise.writeFuture = channel.write(httpPromise.request, channel.voidPromise());
        }
    }

    private void flushChannel() {
        channel.flush();
    }

    private int newStreamId() {
        int id = streamIdIncr.getAndAdd(2);
        if (id <= 0) {
            streamIdIncr.set(1);
            id = streamIdIncr.getAndAdd(2);
        }
        return id;
    }

    public Promise<Long> close() {
        return close(false);
    }

    public Promise<Long> close(boolean shutdownWorker) {
        return close(shutdownWorker, "user close");
    }

    public Promise<Long> close(boolean shutdownWorker, String closeCause) {
        if (closePromise != null) {
            return closePromise;
        }
        synchronized (this) {
            if (closePromise != null) {
                return closePromise;
            }
            Promise<Long> promise = closePromise = new DefaultPromise<>(GlobalEventExecutor.INSTANCE);
            long startTime = System.currentTimeMillis();
            flush().addListener(future1 -> {
                ScheduledFuture timeoutScheduledFuture = this.timeoutScheduledFuture;
                if (timeoutScheduledFuture != null) {
                    timeoutScheduledFuture.cancel(false);
                }

                Channel channel = this.channel;
                if (channel != null) {
                    channel.close().addListener(future2 -> {
                        if (shutdownWorker) {
                            bootstrap.config().group().shutdownGracefully().addListener(future3 -> {
                                promise.trySuccess(System.currentTimeMillis() - startTime);
                            });
                        } else {
                            promise.trySuccess(System.currentTimeMillis() - startTime);
                        }
                    });
                } else {
                    if (shutdownWorker) {
                        bootstrap.config().group().shutdownGracefully().addListener(future2 -> {
                            promise.trySuccess(System.currentTimeMillis() - startTime);
                        });
                    } else {
                        promise.trySuccess(System.currentTimeMillis() - startTime);
                    }
                }
            });
            promise.addListener(future -> logger.info("http2 close success. closeCause = '{}', remoteAddress = '{}', shutdownWorker = {}, time = {}/ms",
                    closeCause, remoteAddress, shutdownWorker, future.getNow()));
        }
        return closePromise;
    }

    public interface H2FutureListener extends GenericFutureListener<H2Response> {
    }

    public static class H2Response extends DefaultPromise<FullHttpResponse> implements Future<FullHttpResponse>, Closeable, Flushable {
        private final int timeout;
        private final NettyHttp2Client client;
        private final FullHttpRequest request;
        private final long beginTimestamp = System.currentTimeMillis();
        private final int streamId;
        private final AtomicBoolean flush = new AtomicBoolean();
        private final AtomicBoolean done = new AtomicBoolean();
        private long endTimestamp = -1L;
        private ChannelFuture writeFuture;
        private EventExecutor executor;

        public H2Response(NettyHttp2Client client, FullHttpRequest request, int timeout, int streamId) {
            super(client.bootstrap.config().group().next());
            this.client = client;
            this.request = request;
            this.timeout = timeout;
            this.streamId = streamId;
        }

        @Override
        public EventExecutor executor() {
            if (executor != null) {
                return executor;
            }
            return super.executor();
        }

        @Override
        public boolean isDone() {
            boolean isDone = super.isDone();
            if (isDone) {
                done();
            }
            return isDone;
        }

        private void done() {
            if (done.compareAndSet(false, true)) {
                endTimestamp = System.currentTimeMillis();
            }
        }

        public long getExecuteTime() {
            if (isDone()) {
                return endTimestamp - beginTimestamp;
            } else {
                return System.currentTimeMillis() - beginTimestamp;
            }
        }

        public int getStreamId() {
            return streamId;
        }

        public boolean isTimeout() {
            if (timeout <= 0) {
                return false;
            }
            return getExecuteTime() > timeout;
        }

        public int getTimeout() {
            return timeout;
        }

        public long getBeginTimestamp() {
            return beginTimestamp;
        }

        public long getEndTimestamp() {
            return endTimestamp;
        }

        public ChannelFuture getWriteFuture() {
            return writeFuture;
        }

        public FullHttpRequest getRequest() {
            return request;
        }

        public FullHttpResponse getResponse() {
            if (isDone()) {
                if (isSuccess()) {
                    return getNow();
                } else {
                    Throwable cause = cause();
                    if (cause != null) {
                        PlatformDependent.throwException(cause);
                    }
                    return getNow();
                }
            } else if (isTimeout()) {
                throw writeFuture == null ? WriteTimeoutException.INSTANCE : ReadTimeoutException.INSTANCE;
            } else {
                return null;
            }
        }

        @Override
        public void close() {
            FullHttpResponse response = getNow();
            if (response != null && response.refCnt() > 0) {
                response.release();
            }
        }

        public H2Response onFailure(Consumer<Throwable> consumer) {
            super.addListener(future -> {
                if (!future.isSuccess()) {
                    consumer.accept(future.cause());
                }
            });
            return this;
        }

        public H2Response onSuccess(Consumer<FullHttpResponse> consumer) {
            super.addListener(future -> {
                if (future.isSuccess()) {
                    consumer.accept((FullHttpResponse) future.getNow());
                }
            });
            return this;
        }

        public H2Response onComplete(H2FutureListener listener) {
            super.addListener(listener);
            return this;
        }

        public H2Response addListener(H2FutureListener listener) {
            super.addListener(listener);
            return this;
        }

        @Override
        public H2Response addListeners(GenericFutureListener<? extends io.netty.util.concurrent.Future<? super FullHttpResponse>>... listeners) {
            super.addListeners(listeners);
            return this;
        }

        @Override
        public H2Response addListener(GenericFutureListener<? extends io.netty.util.concurrent.Future<? super FullHttpResponse>> listener) {
            super.addListener(listener);
            return this;
        }

        @Override
        public boolean await(long timeoutMillis) throws InterruptedException {
            flush();
            return super.await(timeoutMillis);
        }

        @Override
        public boolean await(long timeout, TimeUnit unit) throws InterruptedException {
            flush();
            return super.await(timeout, unit);
        }

        @Override
        public H2Response await() throws InterruptedException {
            flush();
            super.await();
            return this;
        }

        @Override
        public boolean awaitUninterruptibly(long timeoutMillis) {
            flush();
            return super.awaitUninterruptibly(timeoutMillis);
        }

        @Override
        public boolean awaitUninterruptibly(long timeout, TimeUnit unit) {
            flush();
            return super.awaitUninterruptibly(timeout, unit);
        }

        @Override
        public H2Response awaitUninterruptibly() {
            flush();
            super.awaitUninterruptibly();
            return this;
        }

        @Override
        public H2Response sync() throws InterruptedException {
            flush();
            super.sync();
            return this;
        }

        @Override
        public H2Response syncUninterruptibly() {
            flush();
            super.syncUninterruptibly();
            return this;
        }

        @Override
        public FullHttpResponse get() throws InterruptedException, WriteTimeoutException, ReadTimeoutException {
            await();
            return getResponse();
        }

        @Override
        public FullHttpResponse get(long timeout, TimeUnit unit) throws InterruptedException, WriteTimeoutException, ReadTimeoutException {
            await(timeout, unit);
            return getResponse();
        }

        @Override
        public String toString() {
            String toString = "streamId = " + streamId + ", time = " + getExecuteTime() + "/ms, ";
            if (isDone()) {
                if (isSuccess()) {
                    return toString + getNow();
                } else {
                    Throwable cause = cause();
                    if (cause != null) {
                        return toString + cause;
                    }
                }
            }
            return toString + "No arrived";
        }

        @Override
        public void flush() {
            if (flush.compareAndSet(false, true)) {
                if (client.isActive()) {
                    client.writeChannel(this);
                    client.flushChannel();
                } else {
                    client.connect().addListener(future -> {
                        if (future.isSuccess()) {
                            client.writeChannel(H2Response.this);
                            client.flushChannel();
                        } else {
                            tryFailure(WriteTimeoutException.INSTANCE);
                        }
                    });
                }
            }
        }
    }

    public boolean isSsl() {
        return HttpScheme.HTTPS == scheme;
    }

    public InetSocketAddress getRemoteAddress() {
        return remoteAddress;
    }

    public Http2Settings getSettings() {
        return settings;
    }

    /**
     * Configures the client pipeline to support HTTP/2 frames.
     */
    public static class Http2Handler extends ChannelInitializer<SocketChannel> {
        private Http2FrameLogger logger;
        private final SslContext sslCtx;
        private final int maxContentLength;
        private final HttpToHttp2ConnectionHandler connectionHandler;
        private final HttpResponseHandler responseHandler;
        private final Http2SettingsHandler settingsHandler;
        private final int connectTimeout;
        private final InetSocketAddress remoteAddress;
        private final Http2Connection connection;

        public Http2Handler(HttpScheme scheme, int maxContentLength, int connectTimeout, InetSocketAddress remoteAddress) throws SSLException {
            this.sslCtx = newSslContext(scheme);
            this.maxContentLength = maxContentLength;
            this.connectTimeout = connectTimeout;
            this.remoteAddress = remoteAddress;
            this.connection = new DefaultHttp2Connection(false);
            this.connectionHandler = newConnectionHandler(connection);
            this.responseHandler = new HttpResponseHandler();
            this.settingsHandler = new Http2SettingsHandler();
        }

        @Override
        public String toString() {
            return remoteAddress.toString();
        }

        protected SslContext newSslContext(HttpScheme scheme) throws SSLException {
            SslContext sslCtx;
            if (HttpScheme.HTTPS == scheme) {
                Optional<SslProvider> sslProvider = Stream.of(SslProvider.values()).filter(SslProvider::isAlpnSupported).findAny();
                if (!sslProvider.isPresent()) {
                    throw new SSLProtocolException(
                            "Not found SslProvider. place add maven dependency\n" +
                                    "        <dependency>\n" +
                                    "            <groupId>io.netty</groupId>\n" +
                                    "            <artifactId>netty-tcnative-boringssl-static</artifactId>\n" +
                                    "            <version>any version. example = 2.0.34.Final</version>\n" +
                                    "            <scope>compile</scope>\n" +
                                    "        </dependency>\n");
                }
                sslCtx = SslContextBuilder.forClient()
                        .sslProvider(sslProvider.get())
                        /* NOTE: the cipher filter may not include all ciphers required by the HTTP/2 specification.
                         * Please refer to the HTTP/2 specification for cipher requirements. */
                        .ciphers(Http2SecurityUtil.CIPHERS, SupportedCipherSuiteFilter.INSTANCE)
                        .trustManager(InsecureTrustManagerFactory.INSTANCE)
                        .applicationProtocolConfig(new ApplicationProtocolConfig(
                                Protocol.ALPN,
                                // NO_ADVERTISE is currently the only mode supported by both OpenSsl and JDK providers.
                                SelectorFailureBehavior.NO_ADVERTISE,
                                // ACCEPT is currently the only mode supported by both OpenSsl and JDK providers.
                                SelectedListenerFailureBehavior.ACCEPT,
                                ApplicationProtocolNames.HTTP_2,
                                ApplicationProtocolNames.HTTP_1_1))
                        .build();
            } else {
                sslCtx = null;
            }
            return sslCtx;
        }

        public void setLogger(Http2FrameLogger logger) {
            this.logger = logger;
        }

        protected HttpToHttp2ConnectionHandler newConnectionHandler(Http2Connection connection) {
            HttpToHttp2ConnectionHandlerBuilder builder = new HttpToHttp2ConnectionHandlerBuilder();
            InboundHttp2ToHttpAdapter http2ToHttpAdapter = new InboundHttp2ToHttpAdapterBuilder(connection)
                    .maxContentLength(maxContentLength)
                    .propagateSettings(true)
                    .build();
            builder.frameListener(new DelegatingDecompressorFrameListener(connection, http2ToHttpAdapter));
            if (logger != null) {
                builder.frameLogger(logger);
            }
            return builder
                    .connection(connection)
                    .build();
        }

        @Override
        public void initChannel(SocketChannel ch) throws Exception {
            ChannelPromise promise = ch.newPromise();
            settingsHandler.setPromise(promise);
            ch.eventLoop().schedule(() -> {
                promise.setFailure(ReadTimeoutException.INSTANCE);
            }, connectTimeout, TimeUnit.MILLISECONDS);

            if (sslCtx != null) {
                configureSsl(ch);
            } else {
                configureClearText(ch);
            }
        }

        public Http2Connection getConnection() {
            return connection;
        }

        public SslContext getSslCtx() {
            return sslCtx;
        }

        public HttpResponseHandler responseHandler() {
            return responseHandler;
        }

        public Http2SettingsHandler settingsHandler() {
            return settingsHandler;
        }

        protected void configureEndOfPipeline(ChannelPipeline pipeline) {
            pipeline.addLast(settingsHandler, responseHandler);
        }

        /**
         * Configure the pipeline for TLS NPN negotiation to HTTP/2.
         */
        private void configureSsl(SocketChannel ch) {
            ChannelPipeline pipeline = ch.pipeline();
            // Specify Host in SSLContext New Handler to add TLS SNI Extension
            if (sslCtx != null) {
                pipeline.addLast(sslCtx.newHandler(ch.alloc(), getHostString(remoteAddress), remoteAddress.getPort()));
            }
            // We must wait for the handshake to finish and the protocol to be negotiated before configuring
            // the HTTP/2 components of the pipeline.
            pipeline.addLast(new ApplicationProtocolNegotiationHandler("") {
                @Override
                protected void configurePipeline(ChannelHandlerContext ctx, String protocol) {
                    if (ApplicationProtocolNames.HTTP_2.equalsIgnoreCase(protocol)) {
                        ChannelPipeline p = ctx.pipeline();
                        p.addLast(connectionHandler);
                        configureEndOfPipeline(p);
                        return;
                    }
                    ctx.close();
                    throw new IllegalStateException("unknown protocol: " + protocol);
                }
            });
        }

        /**
         * Configure the pipeline for a cleartext upgrade from HTTP to HTTP/2.
         */
        private void configureClearText(SocketChannel ch) {
            HttpClientCodec sourceCodec = new HttpClientCodec();
            Http2ClientUpgradeCodec upgradeCodec = new Http2ClientUpgradeCodec(connectionHandler);
            HttpClientUpgradeHandler upgradeHandler = new HttpClientUpgradeHandler(sourceCodec, upgradeCodec, 65536);

            ch.pipeline().addLast(sourceCodec,
                    upgradeHandler,
                    new Http2Handler.UpgradeRequestHandler());
        }

        /**
         * A handler that triggers the cleartext upgrade to HTTP/2 by sending an initial HTTP request.
         */
        private final class UpgradeRequestHandler extends ChannelInboundHandlerAdapter {

            @Override
            public void channelActive(ChannelHandlerContext ctx) throws Exception {
                DefaultFullHttpRequest upgradeRequest =
                        new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, "/", Unpooled.EMPTY_BUFFER, false);

                // Set HOST header as the remote peer may require it.
                InetSocketAddress remote = (InetSocketAddress) ctx.channel().remoteAddress();
                upgradeRequest.headers().set(HttpHeaderNames.HOST, getHostString(remote) + ':' + remote.getPort());

                ctx.writeAndFlush(upgradeRequest);

                ctx.fireChannelActive();

                // Done with this handler, remove it from the pipeline.
                ctx.pipeline().remove(this);

                configureEndOfPipeline(ctx.pipeline());
            }
        }
    }

    public static class Http2SettingsHandler extends SimpleChannelInboundHandler<Http2Settings> {
        // Promise object used to notify when first settings are received
        private ChannelPromise promise;
        private Http2Settings http2Settings;

        @Override
        protected void channelRead0(ChannelHandlerContext ctx, Http2Settings msg) throws Exception {
            this.http2Settings = msg;
            promise.setSuccess();

            // Only care about the first settings message
            ctx.pipeline().remove(this);
        }

        public Http2Settings getHttp2Settings() {
            return http2Settings;
        }

        public void setPromise(ChannelPromise promise) {
            this.promise = promise;
        }

        public ChannelPromise promise() {
            return promise;
        }

        @Override
        public String toString() {
            return String.valueOf(http2Settings);
        }
    }

    /**
     * Process {@link FullHttpResponse} translated from HTTP/2 frames
     */
    public static class HttpResponseHandler extends SimpleChannelInboundHandler<FullHttpResponse> {
        private static InternalLogger logger = InternalLoggerFactory.getInstance(HttpResponseHandler.class);
        private final Map<Integer, H2Response> streamIdPromiseMap = new ConcurrentHashMap<>(64);

        /**
         * Create an association between an anticipated response stream id and a {@link ChannelPromise}
         *
         * @param streamId The stream for which a response is expected
         * @param promise  The promise object that will be used to wait/notify events
         * @return The previous object associated with {@code streamId}
         */
        public H2Response put(int streamId, H2Response promise) {
            return streamIdPromiseMap.put(streamId, promise);
        }

        public Map<Integer, H2Response> getStreamIdPromiseMap() {
            return streamIdPromiseMap;
        }

        public HttpResponseHandler() {
            super(false);
        }

        @Override
        protected void channelRead0(ChannelHandlerContext ctx, FullHttpResponse msg) throws Exception {
            Integer streamId = msg.headers().getInt(HttpConversionUtil.ExtensionHeaderNames.STREAM_ID.text());
            if (streamId == null) {
                logger.warn("HttpResponseHandler unexpected message received: {}", msg);
                return;
            }
            H2Response promise = streamIdPromiseMap.remove(streamId);
            if (promise != null) {
                promise.setSuccess(msg);
            }
        }

        @Override
        public String toString() {
            return streamIdPromiseMap.toString();
        }
    }

    private static String getHostString(InetSocketAddress address) {
        String hostString = address.getHostString();
        if (hostString == null) {
            hostString = address.getAddress().getHostAddress();
        }
        return hostString;
    }

    @Override
    public String toString() {
        if (isClose()) {
            return "closed, ! " + remoteAddress;
        } else {
            String toString = channel == null ? String.valueOf(remoteAddress) : channel + ", setting=" + settings;
            return "pending=" + pendingWriteQueue.size() + ", " + toString;
        }
    }
}
