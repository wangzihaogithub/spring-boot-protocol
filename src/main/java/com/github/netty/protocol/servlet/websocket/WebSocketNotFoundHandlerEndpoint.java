package com.github.netty.protocol.servlet.websocket;

import javax.websocket.CloseReason;
import javax.websocket.Endpoint;
import javax.websocket.EndpointConfig;
import javax.websocket.Session;
import java.io.IOException;

public class WebSocketNotFoundHandlerEndpoint extends Endpoint {
    public static final WebSocketNotFoundHandlerEndpoint INSTANCE = new WebSocketNotFoundHandlerEndpoint();

    @Override
    public void onOpen(Session session, EndpointConfig config) {
        session.getAsyncRemote().sendText("close! cause not found endpoint! ");
        try {
            session.close(new CloseReason(CloseReason.CloseCodes.UNEXPECTED_CONDITION, null));
        } catch (IOException ignored) {

        }
    }
}
