package com.github.netty.protocol.mysql;

import com.github.netty.core.util.LoggerFactoryX;
import com.github.netty.core.util.LoggerX;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.util.Attribute;
import io.netty.util.AttributeKey;
import io.netty.util.concurrent.GenericFutureListener;

import java.util.EnumSet;
import java.util.Set;

public class Session {
    private static final AttributeKey<Session> SESSION_KEY = AttributeKey.valueOf(Session.class.getName() + "#Session");
    private static final AttributeKey<Integer> CONNECTION_ID_KEY = AttributeKey.valueOf(Integer.class.getName() + "#connectionId");
    private static final AttributeKey<MysqlCharacterSet> SERVER_CHARSET_KEY = AttributeKey.valueOf(MysqlCharacterSet.class.getName() + "#server");
    private static final AttributeKey<MysqlCharacterSet> CLIENT_CHARSET_KEY = AttributeKey.valueOf(MysqlCharacterSet.class.getName() + "#client");
    private static final AttributeKey<EnumSet<CapabilityFlags>> CAPABILITIES_ATTR = AttributeKey.valueOf(CapabilityFlags.class.getName() + "#CapabilityFlags");

    private static final LoggerX logger = LoggerFactoryX.getLogger(Session.class);
    private volatile Channel frontendChannel;
    private volatile Channel backendChannel;
    private String id;
    private Integer connectionId;
    private MysqlCharacterSet clientCharacterSet;
    private MysqlCharacterSet serverCharacterSet;

    public Session() {
    }

    public Session(String id) {
        this.id = id;
    }

    public static Session getSession(Channel channel) {
        return channel.attr(SESSION_KEY).get();
    }

    public String getId() {
        return id;
    }

    public EnumSet<CapabilityFlags> getFrontendCapabilities() {
        return getCapabilities(frontendChannel);
    }

    public void setFrontendCapabilities(Set<CapabilityFlags> capabilities) {
        Attribute<EnumSet<CapabilityFlags>> attr = frontendChannel.attr(CAPABILITIES_ATTR);
        attr.set(EnumSet.copyOf(capabilities));
    }

    public EnumSet<CapabilityFlags> getBackendCapabilities() {
        return getCapabilities(backendChannel);
    }

    public void setBackendCapabilities(Set<CapabilityFlags> capabilities) {
        Attribute<EnumSet<CapabilityFlags>> attr = backendChannel.attr(CAPABILITIES_ATTR);
        attr.set(EnumSet.copyOf(capabilities));
    }

    private EnumSet<CapabilityFlags> getCapabilities(Channel frontendChannel) {
        Attribute<EnumSet<CapabilityFlags>> attr = frontendChannel.attr(CAPABILITIES_ATTR);
        EnumSet<CapabilityFlags> capabilityFlags = attr.get();
        if (capabilityFlags == null) {
            capabilityFlags = CapabilityFlags.getImplicitCapabilities();
            attr.set(capabilityFlags);
        }
        return capabilityFlags;
    }

    public void setClientCharsetAttr(MysqlCharacterSet characterSet) {
        this.clientCharacterSet = characterSet;
        frontendChannel.attr(CLIENT_CHARSET_KEY).set(characterSet);
        backendChannel.attr(CLIENT_CHARSET_KEY).set(characterSet);
    }

    public void setServerCharsetAttr(MysqlCharacterSet characterSet) {
        this.serverCharacterSet = characterSet;
        frontendChannel.attr(SERVER_CHARSET_KEY).set(characterSet);
        backendChannel.attr(SERVER_CHARSET_KEY).set(characterSet);
    }

    public MysqlCharacterSet getServerCharset() {
        if (serverCharacterSet != null) {
            return serverCharacterSet;
        } else {
            return MysqlCharacterSet.DEFAULT;
        }
    }

    public MysqlCharacterSet getClientCharset() {
        if (clientCharacterSet != null) {
            return clientCharacterSet;
        } else {
            return MysqlCharacterSet.DEFAULT;
        }
    }

    public Integer getConnectionId() {
        return connectionId;
    }

    public void setConnectionId(int connectionId) {
        this.connectionId = connectionId;
        frontendChannel.attr(CONNECTION_ID_KEY).set(connectionId);
        backendChannel.attr(CONNECTION_ID_KEY).set(connectionId);
    }

    public Channel getBackendChannel() {
        return this.backendChannel;
    }

    public void setBackendChannel(Channel backendChannel) {
        this.backendChannel = backendChannel;
        backendChannel.attr(SESSION_KEY).set(this);
        backendChannel.closeFuture().addListener(new ConnectionCloseFutureListener(this));
    }

    public Channel getFrontendChannel() {
        return this.frontendChannel;
    }

    public void setFrontendChannel(Channel frontendChannel) {
        this.frontendChannel = frontendChannel;
        frontendChannel.attr(SESSION_KEY).set(this);
        frontendChannel.closeFuture().addListener(new ConnectionCloseFutureListener(this));
    }

    @Override
    public String toString() {
        return "Session[" + id + "]";
    }

    private static class ConnectionCloseFutureListener implements GenericFutureListener<ChannelFuture> {
        private final Session session;

        ConnectionCloseFutureListener(Session session) {
            this.session = session;
        }

        @Override
        public void operationComplete(ChannelFuture future) throws Exception {
            Channel ch = future.channel();
            Channel backendChannel = session.backendChannel;
            Channel frontendChannel = session.frontendChannel;
            if (ch == frontendChannel) {
                logger.info("client channel closed ! [{}]", frontendChannel);
                // frontendChannel connection close but it's mysqlChannel connection is still active or open, close it!
                if (backendChannel != null) {
                    if (backendChannel.isActive() || backendChannel.isOpen()) {
                        backendChannel.close();
                    }
                }
            } else {
                logger.info("server channel closed ! [{}] ", backendChannel);
                // mysqlChannel connection close but it's frontendChannel connection is still active or open, close it!
                if (frontendChannel != null) {
                    if (frontendChannel.isActive() || frontendChannel.isOpen()) {
                        frontendChannel.close();
                    }
                }
            }
        }
    }
}
