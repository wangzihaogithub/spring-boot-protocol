package com.github.netty.protocol.servlet.websocket;

import com.github.netty.protocol.servlet.util.HttpHeaderConstants;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPipeline;
import io.netty.handler.codec.http.*;
import io.netty.handler.codec.http.websocketx.WebSocketServerHandshaker13;
import io.netty.handler.codec.http.websocketx.extensions.*;
import io.netty.handler.codec.http.websocketx.extensions.compression.DeflateFrameServerExtensionHandshaker;
import io.netty.handler.codec.http.websocketx.extensions.compression.PerMessageDeflateServerExtensionHandshaker;

import java.util.*;

/**
 * Websocket13 handshake, with protocol extensions
 * @author wangzihao
 */
public class WebSocketServerHandshaker13Extension extends WebSocketServerHandshaker13 {
    private static final char EXTENSION_SEPARATOR = ',';
    private static final char PARAMETER_SEPARATOR = ';';
    private static final char PARAMETER_EQUAL = '=';
    private int rsv = 0;
    private String httpDecoderContextName;
    private Channel channel;

    private List<WebSocketServerExtensionHandshaker> extensionHandshakers =
            Arrays.asList(new PerMessageDeflateServerExtensionHandshaker(),new DeflateFrameServerExtensionHandshaker());

    public WebSocketServerHandshaker13Extension(String webSocketURL, String subprotocols, boolean allowExtensions, int maxFramePayloadLength) {
        super(webSocketURL, subprotocols, allowExtensions, maxFramePayloadLength);
    }

    public WebSocketServerHandshaker13Extension(String webSocketURL, String subprotocols, boolean allowExtensions, int maxFramePayloadLength, boolean allowMaskMismatch) {
        super(webSocketURL, subprotocols, allowExtensions, maxFramePayloadLength, allowMaskMismatch);
    }

    @Override
    public ChannelFuture handshake(Channel channel, FullHttpRequest req) {
        this.httpDecoderContextName = getHttpDecoderContextName(channel.pipeline());
        this.channel = channel;

        return handshake(channel, req, null, channel.newPromise());
    }

    @Override
    protected FullHttpResponse newHandshakeResponse(FullHttpRequest req, HttpHeaders headers) {
        FullHttpResponse response = super.newHandshakeResponse(req, headers);
        String requestHeaderValue = req.headers().getAsString(HttpHeaderConstants.SEC_WEBSOCKET_EXTENSIONS);
        if(requestHeaderValue == null || requestHeaderValue.isEmpty()){
            return response;
        }

        String responseHeaderValue = response.headers().getAsString(HttpHeaderNames.SEC_WEBSOCKET_EXTENSIONS);
        String newResponseHeaderValue = handshakeExtension(requestHeaderValue,responseHeaderValue);
        response.headers().set(HttpHeaderConstants.SEC_WEBSOCKET_EXTENSIONS, newResponseHeaderValue);
        return response;
    }

    /**
     * Handshake websocket protocol extension
     * @param requestHeaderValue requestHeaderValue
     * @param responseHeaderValue responseHeaderValue
     * @return responseHeaderValue
     */
    private String handshakeExtension(String requestHeaderValue,String responseHeaderValue){
        List<WebSocketServerExtension> validExtensions = getWebSocketServerExtension(requestHeaderValue);
        if(validExtensions != null) {
            for (WebSocketServerExtension extension : validExtensions) {
                WebSocketExtensionData extensionData = extension.newReponseData();
                responseHeaderValue = appendExtension(responseHeaderValue, extensionData.name(), extensionData.parameters());

                if(httpDecoderContextName != null && channel != null) {
                    WebSocketExtensionDecoder decoder = extension.newExtensionDecoder();
                    WebSocketExtensionEncoder encoder = extension.newExtensionEncoder();
                    channel.pipeline().addAfter(httpDecoderContextName, decoder.getClass().getName(), decoder);
                    channel.pipeline().addAfter(httpDecoderContextName, encoder.getClass().getName(), encoder);
                }
            }
        }
        return responseHeaderValue;
    }

    private String getHttpDecoderContextName(ChannelPipeline pipeline){
        ChannelHandlerContext ctx = pipeline.context(HttpRequestDecoder.class);
        if (ctx == null) {
            ctx = pipeline.context(HttpServerCodec.class);
        }
        return ctx == null? null : ctx.name();
    }

    /**
     * Gets the implementation class for the websocket protocol extension
     * @param extensionsHeader extensionsHeader
     * @return WebSocketServerExtension
     */
    private List<WebSocketServerExtension> getWebSocketServerExtension(String extensionsHeader){
        List<WebSocketServerExtension> validExtensions = null;
        if (extensionsHeader != null) {
            List<WebSocketExtensionData> extensions = WebSocketExtensionUtil.extractExtensions(extensionsHeader);


            for (WebSocketExtensionData extensionData : extensions) {
                Iterator<WebSocketServerExtensionHandshaker> extensionHandshakersIterator =
                        extensionHandshakers.iterator();
                WebSocketServerExtension validExtension = null;

                while (validExtension == null && extensionHandshakersIterator.hasNext()) {
                    WebSocketServerExtensionHandshaker extensionHandshaker = extensionHandshakersIterator.next();
                    validExtension = extensionHandshaker.handshakeExtension(extensionData);
                }

                if (validExtension != null && ((validExtension.rsv() & rsv) == 0)) {
                    if (validExtensions == null) {
                        validExtensions = new ArrayList<>(1);
                    }
                    rsv = rsv | validExtension.rsv();
                    validExtensions.add(validExtension);
                }
            }
        }
        return validExtensions;
    }

    public int getRsv() {
        return rsv;
    }

    /**
     * Concatenate the extended string for the response header
     * @param currentHeaderValue currentHeaderValue
     * @param extensionName extensionName
     * @param extensionParameters extensionParameters
     * @return extensionHeaderValue
     */
    private static String appendExtension(String currentHeaderValue, String extensionName,Map<String, String> extensionParameters) {
        StringBuilder newHeaderValue = new StringBuilder(
                currentHeaderValue != null ? currentHeaderValue.length() : extensionName.length() + 1);
        if (currentHeaderValue != null && !currentHeaderValue.trim().isEmpty()) {
            newHeaderValue.append(currentHeaderValue);
            newHeaderValue.append(EXTENSION_SEPARATOR);
        }
        newHeaderValue.append(extensionName);
        for (Map.Entry<String, String> extensionParameter : extensionParameters.entrySet()) {
            newHeaderValue.append(PARAMETER_SEPARATOR);
            newHeaderValue.append(extensionParameter.getKey());
            if (extensionParameter.getValue() != null) {
                newHeaderValue.append(PARAMETER_EQUAL);
                newHeaderValue.append(extensionParameter.getValue());
            }
        }
        return newHeaderValue.toString();
    }
}
