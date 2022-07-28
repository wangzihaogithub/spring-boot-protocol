package com.github.netty.protocol.servlet.websocket;

import com.github.netty.core.util.IOUtil;
import com.github.netty.core.util.TypeUtil;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.handler.codec.http.websocketx.*;
import io.netty.util.Attribute;
import io.netty.util.AttributeKey;
import io.netty.util.internal.PlatformDependent;

import javax.websocket.*;
import javax.websocket.server.ServerEndpointConfig;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.security.Principal;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Websocket session
 *
 * @author wangzihao
 * 2018/10/13/013
 */
public class WebSocketSession implements Session {
    public static final AttributeKey<WebSocketSession> CHANNEL_ATTR_KEY_SESSION = AttributeKey.valueOf(WebSocketSession.class + "#WebSocketSession");
    public static final SendResult SEND_RESULT_OK = new SendResult();
    private static AtomicLong ids = new AtomicLong(0);
    private final Channel channel;
    private final WebSocketServerHandshaker13Extension webSocketServerHandshaker;
    private final Endpoint localEndpoint;
    private final WebSocketServerContainer webSocketContainer;
    private final String id;
    private final URI requestUri;
    private final Map<String, List<String>> requestParameterMap;
    private final String queryString;
    private final Principal userPrincipal;
    private final String httpSessionId;
    private final List<Extension> negotiatedExtensions;
    private final Map<String, String> pathParameters;
    private final ServerEndpointConfig serverEndpointConfig;
    private final Set<MessageHandler> messageHandlers = new LinkedHashSet<>();
    private final List<EncoderEntry> encoderEntries = new ArrayList<>();
    private final Map<String, Object> userProperties = new ConcurrentHashMap<>();
    private AsyncRemoteEndpoint asyncRemoteEndpoint;
    private BasicRemoteEndpoint basicRemoteEndpoint;
    private volatile State state = State.OPEN;
    private int maxBinaryMessageBufferSize;
    private int maxTextMessageBufferSize;
    private long maxIdleTimeout;
    private long asyncSendTimeout;
    private int rsv;

    public WebSocketSession(Channel channel, WebSocketServerContainer webSocketContainer,
                            WebSocketServerHandshaker13Extension webSocketServerHandshaker,
                            Map<String, List<String>> requestParameterMap,
                            String queryString, Principal userPrincipal, String httpSessionId,
                            List<Extension> negotiatedExtensions, Map<String, String> pathParameters,
                            Endpoint localEndpoint, ServerEndpointConfig serverEndpointConfig) throws DeploymentException {
        this.id = Long.toHexString(ids.getAndIncrement());
        this.webSocketContainer = webSocketContainer;
        this.maxIdleTimeout = webSocketContainer.getDefaultMaxSessionIdleTimeout();
        this.asyncSendTimeout = webSocketContainer.getDefaultAsyncSendTimeout();
        this.channel = channel;
        this.webSocketServerHandshaker = webSocketServerHandshaker;
//        this.rsv = webSocketServerHandshaker.getRsv();
        this.maxTextMessageBufferSize = webSocketServerHandshaker.maxFramePayloadLength();
        this.maxBinaryMessageBufferSize = webSocketServerHandshaker.maxFramePayloadLength();
        this.localEndpoint = localEndpoint;

        this.requestUri = URI.create(webSocketServerHandshaker.uri());
        this.requestParameterMap = requestParameterMap;
        this.queryString = queryString;
        this.userPrincipal = userPrincipal;
        this.httpSessionId = httpSessionId;
        this.negotiatedExtensions = negotiatedExtensions;
        this.pathParameters = pathParameters;
        this.serverEndpointConfig = serverEndpointConfig;

        webSocketContainer.registerSession(localEndpoint, this);

        for (Class<? extends Encoder> encoderClazz : serverEndpointConfig.getEncoders()) {
            Encoder instance;
            try {
                instance = encoderClazz.getConstructor().newInstance();
                instance.init(serverEndpointConfig);
            } catch (ReflectiveOperationException e) {
                throw new DeploymentException("The specified encoder of type [" + encoderClazz.getName() + "] could not be instantiated",
                        e);
            }
            TypeUtil.TypeResult typeResult = TypeUtil.getGenericType(Encoder.class, encoderClazz);
            Class type = typeResult == null ? Object.class : typeResult.getClazz();
            EncoderEntry entry = new EncoderEntry(type, instance);
            encoderEntries.add(entry);
        }
        channel.closeFuture().addListener(e -> {
            if (isOpen()) {
                closeByAbort();
            }
        });
    }

    /**
     * Get httpSession from the properties bound in the pipe
     *
     * @param channel channel
     * @return WebSocketSession
     */
    public static WebSocketSession getSession(Channel channel) {
        if (isChannelActive(channel)) {
            Attribute<WebSocketSession> attribute = channel.attr(CHANNEL_ATTR_KEY_SESSION);
            if (attribute != null) {
                return attribute.get();
            }
        }
        return null;
    }

    /**
     * Bind WebsocketSession to the pipe property
     *
     * @param websocketSession websocketSession
     * @param channel          channel
     */
    public static void setSession(Channel channel, WebSocketSession websocketSession) {
        if (isChannelActive(channel)) {
            channel.attr(CHANNEL_ATTR_KEY_SESSION).set(websocketSession);
        }
    }

    /**
     * Whether the pipe is active
     *
     * @param channel channel
     * @return boolean isChannelActive
     */
    public static boolean isChannelActive(Channel channel) {
        return channel != null && channel.isActive();
    }

    private static CloseReason valueOf(CloseWebSocketFrame frame) {
        int code = frame.statusCode();
        if (code < 1000 || code > 4999) {
            return new CloseReason(CloseReason.CloseCodes.NORMAL_CLOSURE, "");
        } else {
            return new CloseReason(CloseReason.CloseCodes.getCloseCode(code), frame.reasonText());
        }
    }

    public ServerEndpointConfig getServerEndpointConfig() {
        return serverEndpointConfig;
    }

    public WebSocketServerHandshaker13Extension getWebSocketServerHandshaker() {
        return webSocketServerHandshaker;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof WebSocketSession) {
            return Objects.equals(((WebSocketSession) obj).id, this.id);
        }
        return false;
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }

    @Override
    public WebSocketServerContainer getContainer() {
        return webSocketContainer;
    }

    @Override
    public Set<MessageHandler> getMessageHandlers() {
        checkState();
        return messageHandlers;
    }

    @Override
    public void removeMessageHandler(MessageHandler listener) {
        checkState();
        messageHandlers.remove(listener);
    }

    @Override
    public String getProtocolVersion() {
        checkState();
        return webSocketServerHandshaker.version().toHttpHeaderValue();
    }

    @Override
    public String getNegotiatedSubprotocol() {
        checkState();
        return String.join(",", webSocketServerHandshaker.subprotocols());
    }

    @Override
    public List<Extension> getNegotiatedExtensions() {
        checkState();
        return negotiatedExtensions;
    }

    public String getHttpSessionId() {
        return httpSessionId;
    }

    @Override
    public boolean isSecure() {
        checkState();
        return "wss".equalsIgnoreCase(requestUri.getScheme());
    }

    @Override
    public boolean isOpen() {
        return state == State.OPEN;
    }

    @Override
    public long getMaxIdleTimeout() {
        checkState();
        return maxIdleTimeout;
    }

    @Override
    public void setMaxIdleTimeout(long timeout) {
        checkState();
        this.maxIdleTimeout = timeout;
    }

    @Override
    public int getMaxBinaryMessageBufferSize() {
        checkState();
        return maxBinaryMessageBufferSize;
    }

    @Override
    public void setMaxBinaryMessageBufferSize(int max) {
        checkState();
        this.maxBinaryMessageBufferSize = max;
    }

    @Override
    public int getMaxTextMessageBufferSize() {
        checkState();
        return maxTextMessageBufferSize;
    }

    @Override
    public void setMaxTextMessageBufferSize(int max) {
        checkState();
        this.maxTextMessageBufferSize = max;
    }

    @Override
    public RemoteEndpoint.Async getAsyncRemote() {
        checkState();
        if (asyncRemoteEndpoint == null) {
            synchronized (this) {
                if (asyncRemoteEndpoint == null) {
                    asyncRemoteEndpoint = new AsyncRemoteEndpoint();
                }
            }
        }
        return asyncRemoteEndpoint;
    }

    @Override
    public RemoteEndpoint.Basic getBasicRemote() {
        checkState();
        if (basicRemoteEndpoint == null) {
            synchronized (this) {
                if (basicRemoteEndpoint == null) {
                    basicRemoteEndpoint = new BasicRemoteEndpoint();
                }
            }
        }
        return basicRemoteEndpoint;
    }

    @Override
    public String getId() {
        return id;
    }

    @Override
    public void close() throws IOException {
        close(new CloseReason(CloseReason.CloseCodes.NORMAL_CLOSURE, ""));
    }

    @Override
    public void close(CloseReason closeReason) throws IOException {
        destroy(closeReason);
        webSocketServerHandshaker.close(channel, new CloseWebSocketFrame(true, rsv, closeReason.getCloseCode().getCode(), closeReason.getReasonPhrase()));
    }

    void closeByClient(CloseWebSocketFrame frame) {
        destroy(valueOf(frame));
        channel.close();
    }

    void closeByAbort() {
        destroy(new CloseReason(CloseReason.CloseCodes.CLOSED_ABNORMALLY, ""));
    }

    void destroy(CloseReason closeReason) {
        state = State.OUTPUT_CLOSED;
        localEndpoint.onClose(this, closeReason);
        for (EncoderEntry entry : encoderEntries) {
            try {
                entry.getEncoder().destroy();
            } catch (Throwable e) {
                onError(e);
            }
        }
        webSocketContainer.unregisterSession(localEndpoint, this);
        state = State.CLOSED;
    }

    public void onError(Throwable thr) {
        localEndpoint.onError(this, thr);
    }

    @Override
    public URI getRequestURI() {
        checkState();
        return requestUri;
    }

    @Override
    public Map<String, List<String>> getRequestParameterMap() {
        checkState();
        return requestParameterMap;
    }

    @Override
    public String getQueryString() {
        checkState();
        return queryString;
    }

    @Override
    public Map<String, String> getPathParameters() {
        checkState();
        return pathParameters;
    }

    @Override
    public Map<String, Object> getUserProperties() {
        checkState();
        return userProperties;
    }

    @Override
    public Principal getUserPrincipal() {
        checkState();
        return userPrincipal;
    }

    @Override
    public Set<Session> getOpenSessions() {
        return webSocketContainer.getOpenSessions(localEndpoint);
    }

    @Override
    public void addMessageHandler(MessageHandler handler) throws IllegalStateException {
        checkState();
        messageHandlers.add(handler);
    }

    @Override
    public <T> void addMessageHandler(Class<T> clazz, MessageHandler.Partial<T> handler) throws IllegalStateException {
        addMessageHandler(handler);
    }

    @Override
    public <T> void addMessageHandler(Class<T> clazz, MessageHandler.Whole<T> handler) throws IllegalStateException {
        addMessageHandler(handler);
    }

    private void checkState() {
        if (state == State.CLOSED) {
            /*
             * As per RFC 6455, a WebSocket connection is considered to be
             * closed once a peer has sent and received a WebSocket close frame.
             */
            throw new IllegalStateException("The WebSocket session [" + id + "] has been closed and no method (apart from close()) may be called on a closed session");
        }
    }

    private Encoder findEncoder(Object obj) {
        for (EncoderEntry entry : encoderEntries) {
            if (entry.getClazz().isAssignableFrom(obj.getClass())) {
                return entry.getEncoder();
            }
        }
        return null;
    }

    public Future<Void> sendObjectImpl(Object obj, SendHandler completion) {
        if (obj == null) {
            throw new IllegalArgumentException("Invalid null data argument");
        }

        /*
         * Note that the implementation will convert primitives and their object
         * equivalents by default but that users are free to specify their own
         * encoders and decoders for this if they wish.
         */
        Encoder encoder = findEncoder(obj);
        ChannelFuture future = null;
        Exception exception = null;
        try {
            if (encoder == null && TypeUtil.isPrimitive(obj.getClass())) {
                String msg = obj.toString();
                future = channel.writeAndFlush(new TextWebSocketFrame(true, rsv, msg));
            } else if (encoder == null && obj instanceof ByteBuffer) {
                ByteBuffer msg = (ByteBuffer) obj;
                future = channel.writeAndFlush(new BinaryWebSocketFrame(true, rsv, Unpooled.wrappedBuffer(msg)));
            } else if (encoder == null && byte[].class.isAssignableFrom(obj.getClass())) {
                ByteBuffer msg = ByteBuffer.wrap((byte[]) obj);
                future = channel.writeAndFlush(new BinaryWebSocketFrame(true, rsv, Unpooled.wrappedBuffer(msg)));
            } else if (encoder instanceof Encoder.Text) {
                String msg = ((Encoder.Text) encoder).encode(obj);
                future = channel.writeAndFlush(new TextWebSocketFrame(true, rsv, msg));
            } else if (encoder instanceof Encoder.TextStream) {
                try (Writer w = getBasicRemote().getSendWriter()) {
                    ((Encoder.TextStream) encoder).encode(obj, w);
                }
            } else if (encoder instanceof Encoder.Binary) {
                ByteBuffer msg = ((Encoder.Binary) encoder).encode(obj);
                future = channel.writeAndFlush(new BinaryWebSocketFrame(true, rsv, Unpooled.wrappedBuffer(msg)));
                if (completion != null) {
                    future.addListener((ChannelFutureListener) future12 -> completion.onResult(newSendResult(future12)));
                }
            } else if (encoder instanceof Encoder.BinaryStream) {
                try (OutputStream os = getBasicRemote().getSendStream()) {
                    ((Encoder.BinaryStream) encoder).encode(obj, os);
                }
            } else {
                throw new EncodeException(obj, "No encoder specified for object of class [" + obj.getClass() + "]");
            }
        } catch (Exception e) {
            exception = e;
        }

        if (completion != null) {
            if (future == null) {
                completion.onResult(exception == null ? new SendResult() : new SendResult(exception));
            } else {
                future.addListener((ChannelFutureListener) future1 -> completion.onResult(newSendResult(future1)));
            }
        }
        if (exception != null) {
            onError(exception);
        }
        return future == null ? new SimpleFuture(exception) : future;
    }

    private SendResult newSendResult(ChannelFuture future) {
        if (future.isSuccess()) {
            return SEND_RESULT_OK;
        } else {
            return new SendResult(future.cause());
        }
    }

    private void sync(ChannelFuture future) {
        if (future.channel().eventLoop().inEventLoop()) {
            return;
        }
        try {
            future.sync();
        } catch (InterruptedException e) {
            PlatformDependent.throwException(e);
        }
    }

    public enum State {
        OPEN,
        OUTPUT_CLOSED,
        CLOSED
    }

    public static class SimpleFuture implements Future<Void> {
        private Exception exception;

        SimpleFuture(Exception exception) {
            this.exception = exception;
        }

        @Override
        public boolean cancel(boolean mayInterruptIfRunning) {
            return false;
        }

        @Override
        public boolean isCancelled() {
            return false;
        }

        @Override
        public boolean isDone() {
            return true;
        }

        @Override
        public Void get() throws InterruptedException, ExecutionException {
            if (exception != null) {
                throw new ExecutionException(exception);
            }
            return null;
        }

        @Override
        public Void get(long timeout, TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException {
            return get();
        }
    }

    public static class EncoderEntry {
        private final Class<?> clazz;
        private final Encoder encoder;

        EncoderEntry(Class<?> clazz, Encoder encoder) {
            this.clazz = clazz;
            this.encoder = encoder;
        }

        public Class<?> getClazz() {
            return clazz;
        }

        public Encoder getEncoder() {
            return encoder;
        }
    }

    class AsyncRemoteEndpoint implements RemoteEndpoint.Async {
        private final AtomicBoolean batchingAllowed = new AtomicBoolean(false);

        @Override
        public long getSendTimeout() {
            return asyncSendTimeout;
        }

        @Override
        public void setSendTimeout(long timeout) {
            // TODO: 10-16/0016 The socket send timeout setting is not implemented
            asyncSendTimeout = timeout;
        }

        @Override
        public void sendText(String text, SendHandler completion) {
            channel.writeAndFlush(new TextWebSocketFrame(true, rsv, text))
                    .addListener((ChannelFutureListener) future -> completion.onResult(newSendResult(future)));
        }

        @Override
        public Future<Void> sendText(String text) {
            return channel.writeAndFlush(new TextWebSocketFrame(true, rsv, text));
        }

        @Override
        public Future<Void> sendBinary(ByteBuffer data) {
            return channel.writeAndFlush(new BinaryWebSocketFrame(true, rsv, Unpooled.wrappedBuffer(data)));
        }

        @Override
        public void sendBinary(ByteBuffer data, SendHandler completion) {
            if (completion == null) {
                throw new IllegalArgumentException("Invalid null handler argument");
            }
            channel.writeAndFlush(new BinaryWebSocketFrame(true, rsv, Unpooled.wrappedBuffer(data)))
                    .addListener((ChannelFutureListener) future -> completion.onResult(newSendResult(future)));
        }

        @Override
        public Future<Void> sendObject(Object obj) {
            return sendObjectImpl(obj, null);
        }

        @Override
        public void sendObject(Object obj, SendHandler completion) {
            if (completion == null) {
                throw new IllegalArgumentException("Invalid null handler argument");
            }
            sendObjectImpl(obj, completion);
        }

        @Override
        public boolean getBatchingAllowed() {
            return batchingAllowed.get();
        }

        @Override
        public void setBatchingAllowed(boolean batchingAllowed) throws IOException {
            boolean oldValue = this.batchingAllowed.getAndSet(batchingAllowed);
            if (oldValue && !batchingAllowed) {
                flushBatch();
            }
        }

        @Override
        public void flushBatch() throws IOException {
            channel.flush();
        }

        @Override
        public void sendPing(ByteBuffer applicationData) throws IOException, IllegalArgumentException {
            ChannelFuture future = channel.writeAndFlush(new PingWebSocketFrame(true, rsv, Unpooled.wrappedBuffer(applicationData)));
            sync(future);
        }

        @Override
        public void sendPong(ByteBuffer applicationData) throws IOException, IllegalArgumentException {
            ChannelFuture future = channel.writeAndFlush(new PongWebSocketFrame(true, rsv, Unpooled.wrappedBuffer(applicationData)));
            sync(future);
        }
    }

    class BasicRemoteEndpoint implements RemoteEndpoint.Basic {
        private final AtomicBoolean batchingAllowed = new AtomicBoolean(false);
        private Writer writer;
        private OutputStream outputStream;

        @Override
        public void sendText(String text) throws IOException {
            ChannelFuture future = channel.writeAndFlush(new TextWebSocketFrame(true, rsv, text));
            sync(future);
        }

        @Override
        public void sendBinary(ByteBuffer data) throws IOException {
            ChannelFuture future = channel.writeAndFlush(new BinaryWebSocketFrame(true, rsv, Unpooled.wrappedBuffer(data)));
            sync(future);
        }

        @Override
        public void sendText(String fragment, boolean isLast) throws IOException {
            ChannelFuture future = channel.writeAndFlush(new TextWebSocketFrame(isLast, rsv, fragment));
            sync(future);
        }

        @Override
        public void sendBinary(ByteBuffer partialByte, boolean isLast) throws IOException {
            ChannelFuture future = channel.writeAndFlush(new BinaryWebSocketFrame(isLast, rsv, Unpooled.wrappedBuffer(partialByte)));
            sync(future);
        }

        @Override
        public OutputStream getSendStream() throws IOException {
            if (outputStream == null) {
                synchronized (this) {
                    if (outputStream == null) {
                        outputStream = new BasicRemoteOutputStream();
                    }
                }
            }
            return outputStream;
        }

        @Override
        public Writer getSendWriter() throws IOException {
            if (writer == null) {
                synchronized (this) {
                    if (writer == null) {
                        writer = new OutputStreamWriter(getSendStream(), Charset.forName("UTF-8"));
                    }
                }
            }
            return writer;
        }

        @Override
        public void sendObject(Object data) throws IOException, EncodeException {
            Future<Void> future = sendObjectImpl(data, null);
            try {
                future.get();
            } catch (InterruptedException e) {
                PlatformDependent.throwException(e);
            } catch (ExecutionException e) {
                Throwable cause = e.getCause();
                if (cause == null) {
                    cause = e;
                }
                PlatformDependent.throwException(cause);
            }
        }

        @Override
        public boolean getBatchingAllowed() {
            return batchingAllowed.get();
        }

        @Override
        public void setBatchingAllowed(boolean batchingAllowed) throws IOException {
            boolean oldValue = this.batchingAllowed.getAndSet(batchingAllowed);
            if (oldValue && !batchingAllowed) {
                flushBatch();
            }
        }

        @Override
        public void flushBatch() throws IOException {
            channel.flush();
        }

        @Override
        public void sendPing(ByteBuffer applicationData) throws IOException, IllegalArgumentException {
            ChannelFuture future = channel.writeAndFlush(new PingWebSocketFrame(true, rsv, Unpooled.wrappedBuffer(applicationData)));
            sync(future);
        }

        @Override
        public void sendPong(ByteBuffer applicationData) throws IOException, IllegalArgumentException {
            ChannelFuture future = channel.writeAndFlush(new PongWebSocketFrame(true, rsv, Unpooled.wrappedBuffer(applicationData)));
            sync(future);
        }

        class BasicRemoteOutputStream extends OutputStream {
            @Override
            public void write(int b) throws IOException {
                int byteLen = 1;
                byte[] bytes = new byte[byteLen];
                IOUtil.setByte(bytes, 0, b);
                write(bytes, 0, byteLen);
            }

            @Override
            public void write(byte[] b, int off, int len) throws IOException {
                ByteBuf ioByteBuf = channel.alloc().heapBuffer(len);
                ioByteBuf.writeBytes(b, off, len);
                sync(channel.write(ioByteBuf));
            }

            @Override
            public void flush() throws IOException {
                channel.flush();
            }

            @Override
            public void close() throws IOException {
                flush();
            }
        }
    }

}
