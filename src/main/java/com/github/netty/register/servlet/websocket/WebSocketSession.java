package com.github.netty.register.servlet.websocket;

import com.github.netty.core.util.IOUtil;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFutureListener;
import io.netty.handler.codec.http.websocketx.*;
import io.netty.util.Attribute;
import io.netty.util.AttributeKey;

import javax.websocket.*;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.security.Principal;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * websocket会话
 * @author acer01
 *  2018/10/13/013
 */
public class WebSocketSession implements Session {

    public static final AttributeKey<WebSocketSession> CHANNEL_ATTR_KEY_SESSION = AttributeKey.valueOf(WebSocketSession.class + "#WebSocketSession");
    private static AtomicLong ids = new AtomicLong(0);

    private int rsv;
    private Channel channel;
    private WebSocketServerHandshaker webSocketServerHandshaker;
    private Endpoint localEndpoint;
    private AsyncRemoteEndpoint asyncRemoteEndpoint;
    private BasicRemoteEndpoint basicRemoteEndpoint;

    private WebSocketServerContainer webSocketContainer;
    private Set<MessageHandler> messageHandlers = new LinkedHashSet<>();

    private String id;
    private URI requestUri;
    private Map<String, List<String>> requestParameterMap;
    private String queryString;
    private Principal userPrincipal;
    private String httpSessionId;
    private List<Extension> negotiatedExtensions;
    private Map<String, String> pathParameters;
    private final Map<String, Object> userProperties = new ConcurrentHashMap<>();
    private State state = State.OPEN;

    private int maxBinaryMessageBufferSize;
    private int maxTextMessageBufferSize;
    private long maxIdleTimeout = 10000;

    public WebSocketSession(Channel channel, WebSocketServerContainer webSocketContainer,
                            WebSocketServerHandshaker webSocketServerHandshaker,
                            Map<String, List<String>> requestParameterMap,
                            String queryString, Principal userPrincipal, String httpSessionId,
                            List<Extension> negotiatedExtensions, Map<String, String> pathParameters,
                            Endpoint localEndpoint) {
        this.id = Long.toHexString(ids.getAndIncrement());
        this.webSocketContainer = webSocketContainer;
        this.channel = channel;
        this.webSocketServerHandshaker = webSocketServerHandshaker;
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
    }

    public WebSocketServerHandshaker getWebSocketServerHandshaker() {
        return webSocketServerHandshaker;
    }

    /**
     * 从管道中绑定的属性中获取 httpSession
     * @return
     */
    public static WebSocketSession getSession(Channel channel){
        if(isChannelActive(channel)) {
            Attribute<WebSocketSession> attribute = channel.attr(CHANNEL_ATTR_KEY_SESSION);
            if(attribute != null){
                return attribute.get();
            }
        }
        return null;
    }

    /**
     * 把 WebsocketSession绑定到管道属性中
     * @param websocketSession
     */
    public static void setSession(Channel channel, WebSocketSession websocketSession){
        if(isChannelActive(channel)) {
            channel.attr(CHANNEL_ATTR_KEY_SESSION).set(websocketSession);
        }
    }

    /**
     * 管道是否处于活动状态
     * @return
     */
    public static boolean isChannelActive(Channel channel){
        if(channel != null && channel.isActive()) {
            return true;
        }
        return false;
    }

    @Override
    public WebSocketContainer getContainer() {
        return webSocketContainer;
    }

    @Override
    public Set<MessageHandler> getMessageHandlers() {
        return messageHandlers;
    }

    @Override
    public void removeMessageHandler(MessageHandler listener) {
        messageHandlers.remove(listener);
    }

    @Override
    public String getProtocolVersion() {
        return webSocketServerHandshaker.version().toHttpHeaderValue();
    }

    @Override
    public String getNegotiatedSubprotocol() {
        return String.join(",",webSocketServerHandshaker.subprotocols());
    }

    @Override
    public List<Extension> getNegotiatedExtensions() {
        return negotiatedExtensions;
    }

    @Override
    public boolean isSecure() {
        return "wss".equalsIgnoreCase(requestUri.getScheme());
    }

    @Override
    public boolean isOpen() {
        return state == State.OPEN;
    }

    @Override
    public long getMaxIdleTimeout() {
        return maxIdleTimeout;
    }

    @Override
    public void setMaxIdleTimeout(long timeout) {
        this.maxIdleTimeout = timeout;
    }

    @Override
    public void setMaxBinaryMessageBufferSize(int max) {
        this.maxBinaryMessageBufferSize = max;
    }

    @Override
    public int getMaxBinaryMessageBufferSize() {
        return maxBinaryMessageBufferSize;
    }

    @Override
    public void setMaxTextMessageBufferSize(int max) {
        this.maxTextMessageBufferSize = max;
    }

    @Override
    public int getMaxTextMessageBufferSize() {
        return maxTextMessageBufferSize;
    }

    @Override
    public RemoteEndpoint.Async getAsyncRemote() {
        if(asyncRemoteEndpoint == null){
            synchronized (this){
                if(asyncRemoteEndpoint == null){
                    asyncRemoteEndpoint = new AsyncRemoteEndpoint();
                }
            }
        }
        return asyncRemoteEndpoint;
    }

    @Override
    public RemoteEndpoint.Basic getBasicRemote() {
        if(basicRemoteEndpoint == null){
            synchronized (this){
                if(basicRemoteEndpoint == null){
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
        state = State.OUTPUT_CLOSED;
        CloseWebSocketFrame frame = new CloseWebSocketFrame(closeReason.getCloseCode().getCode(),closeReason.getReasonPhrase());
        webSocketServerHandshaker.close(channel, frame.retain())
                .addListener(future -> {
                    state = State.CLOSED;
                    if (future.isSuccess()) {
                        localEndpoint.onClose(this,closeReason);
                    }else {
                        localEndpoint.onError(this,future.cause());
                    }
                });
    }

    @Override
    public URI getRequestURI() {
        return requestUri;
    }

    @Override
    public Map<String, List<String>> getRequestParameterMap() {
        return requestParameterMap;
    }

    @Override
    public String getQueryString() {
        return queryString;
    }

    @Override
    public Map<String, String> getPathParameters() {
        return pathParameters;
    }

    @Override
    public Map<String, Object> getUserProperties() {
        return userProperties;
    }

    @Override
    public Principal getUserPrincipal() {
        return userPrincipal;
    }

    @Override
    public Set<Session> getOpenSessions() {
        return webSocketContainer.getOpenSessions(localEndpoint);
    }

    @Override
    public void addMessageHandler(MessageHandler handler) throws IllegalStateException {
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
            throw new IllegalStateException("wsSession.closed [" + id + "]" );
        }
    }

    private enum State {
        OPEN,
        OUTPUT_CLOSED,
        CLOSED
    }

    class AsyncRemoteEndpoint implements RemoteEndpoint.Async{
        private final AtomicBoolean batchingAllowed = new AtomicBoolean(false);
        private long sendTimeout = 10000;

        @Override
        public long getSendTimeout() {
            return sendTimeout;
        }

        @Override
        public void setSendTimeout(long timeout) {
            // TODO: 10月16日/0016 未实现socket发送超时的设置
            sendTimeout = timeout;
        }

        @Override
        public void sendText(String text, SendHandler completion) {
            channel.writeAndFlush(new TextWebSocketFrame(text))
                    .addListener((ChannelFutureListener) future -> completion.onResult(new SendResult(future.cause())));
        }

        @Override
        public Future<Void> sendText(String text) {
            return channel.writeAndFlush(new TextWebSocketFrame(text));
        }

        @Override
        public Future<Void> sendBinary(ByteBuffer data) {
            return channel.writeAndFlush(new TextWebSocketFrame(Unpooled.wrappedBuffer(data)));
        }

        @Override
        public void sendBinary(ByteBuffer data, SendHandler completion) {
            channel.writeAndFlush(new TextWebSocketFrame(Unpooled.wrappedBuffer(data)))
                    .addListener((ChannelFutureListener) future -> completion.onResult(new SendResult(future.cause())));
        }

        @Override
        public Future<Void> sendObject(Object obj) {
            return sendText(obj.toString());
        }

        @Override
        public void sendObject(Object obj, SendHandler completion) {
            sendText(obj.toString(),completion);
        }

        @Override
        public void setBatchingAllowed(boolean batchingAllowed) throws IOException {
            boolean oldValue = this.batchingAllowed.getAndSet(batchingAllowed);
            if (oldValue && !batchingAllowed) {
                flushBatch();
            }
        }

        @Override
        public boolean getBatchingAllowed() {
            return false;
        }

        @Override
        public void flushBatch() throws IOException {
            // TODO: 10月16日/0016 未实现socket批量刷新
            channel.flush();
        }

        @Override
        public void sendPing(ByteBuffer applicationData) throws IOException, IllegalArgumentException {
            channel.writeAndFlush(new PingWebSocketFrame(Unpooled.wrappedBuffer(applicationData)));
        }

        @Override
        public void sendPong(ByteBuffer applicationData) throws IOException, IllegalArgumentException {
            channel.writeAndFlush(new PongWebSocketFrame(Unpooled.wrappedBuffer(applicationData)));
        }
    }

    class BasicRemoteEndpoint implements RemoteEndpoint.Basic{
        class BasicRemoteOutputStream extends OutputStream{
            @Override
            public void write(int b) throws IOException {
                int byteLen = 1;
                byte[] bytes = new byte[byteLen];
                IOUtil.setByte(bytes,0,b);
                write(bytes,0,byteLen);
            }

            @Override
            public void write(byte[] b, int off, int len) throws IOException {
                ByteBuf ioByteBuf = channel.alloc().heapBuffer(len);
                ioByteBuf.writeBytes(b, off, len);
                channel.write(ioByteBuf);
            }

            @Override
            public void flush() throws IOException {
                channel.flush();
            }
        }

        private Writer writer;
        private OutputStream outputStream;
        private final AtomicBoolean batchingAllowed = new AtomicBoolean(false);

        @Override
        public void sendText(String text) throws IOException {
            channel.writeAndFlush(new TextWebSocketFrame(text));
        }

        @Override
        public void sendBinary(ByteBuffer data) throws IOException {
            channel.writeAndFlush(new TextWebSocketFrame(Unpooled.wrappedBuffer(data)));
        }

        @Override
        public void sendText(String fragment, boolean isLast) throws IOException {
            channel.writeAndFlush(new TextWebSocketFrame(isLast,rsv,fragment));
        }

        @Override
        public void sendBinary(ByteBuffer partialByte, boolean isLast) throws IOException {
            channel.writeAndFlush(new TextWebSocketFrame(isLast,rsv,Unpooled.wrappedBuffer(partialByte)));
        }

        @Override
        public OutputStream getSendStream() throws IOException {
            if(outputStream == null){
                synchronized (this){
                    if(outputStream == null){
                        outputStream = new BasicRemoteOutputStream();
                    }
                }
            }
            return outputStream;
        }

        @Override
        public Writer getSendWriter() throws IOException {
            if(writer == null){
                synchronized (this){
                    if(writer == null){
                        writer = new OutputStreamWriter(getSendStream(), Charset.forName("UTF-8"));
                    }
                }
            }
            return writer;
        }

        @Override
        public void sendObject(Object data) throws IOException, EncodeException {
            sendText(data.toString());
        }

        @Override
        public void setBatchingAllowed(boolean batchingAllowed) throws IOException {
            boolean oldValue = this.batchingAllowed.getAndSet(batchingAllowed);
            if (oldValue && !batchingAllowed) {
                flushBatch();
            }
        }

        @Override
        public boolean getBatchingAllowed() {
            return batchingAllowed.get();
        }

        @Override
        public void flushBatch() throws IOException {
            // TODO: 10月16日/0016 未实现socket批量刷新
            channel.flush();
        }

        @Override
        public void sendPing(ByteBuffer applicationData) throws IOException, IllegalArgumentException {
            channel.writeAndFlush(new PingWebSocketFrame(Unpooled.wrappedBuffer(applicationData)));
        }

        @Override
        public void sendPong(ByteBuffer applicationData) throws IOException, IllegalArgumentException {
            channel.writeAndFlush(new PongWebSocketFrame(Unpooled.wrappedBuffer(applicationData)));
        }
    }

}
