package com.github.netty.protocol.mysql;

import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.util.AttributeKey;
import io.netty.util.concurrent.GenericFutureListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Session {
    public static final AttributeKey<Session> SESSION_KEY = AttributeKey.valueOf("session");
	private static Logger logger = LoggerFactory.getLogger(Session.class);
    private volatile Channel clientChannel;
    private volatile Channel serverChannel;

	public void setClientChannel(Channel clientChannel) {
		clientChannel.attr(Session.SESSION_KEY).set(this);
		clientChannel.closeFuture().addListener(new ConnectionCloseFutureListener(this));
		this.clientChannel = clientChannel;
	}

	public void setServerChannel(Channel serverChannel) {
		serverChannel.attr(Session.SESSION_KEY).set(this);
		serverChannel.closeFuture().addListener(new ConnectionCloseFutureListener(this));
		this.serverChannel = serverChannel;
	}

	public Channel getServerChannel() {
		return this.serverChannel;
	}

	public Channel getClientChannel() {
		return this.clientChannel;
	}

    private static class ConnectionCloseFutureListener implements GenericFutureListener<ChannelFuture> {
    	private final Session session;
    	ConnectionCloseFutureListener(Session session) {
    		this.session = session;
    	}
		@Override
		public void operationComplete(ChannelFuture future) throws Exception {
			Channel ch = future.channel();
			Channel serverChannel = session.serverChannel;
			Channel clientChannel = session.clientChannel;
			if (ch == clientChannel) {
				logger.info("client channel closed ! [{}]", clientChannel);
				// clientChannel connection close but it's mysqlChannel connection is still active or open, close it!
				if(serverChannel != null) {
					if (serverChannel.isActive() || serverChannel.isOpen()) {
						serverChannel.close();
					}
				}
			} else {
				logger.info("server channel closed ! [{}] ", serverChannel);
				// mysqlChannel connection close but it's clientChannel connection is still active or open, close it!
				if(clientChannel != null) {
					if (clientChannel.isActive() || clientChannel.isOpen()) {
						clientChannel.close();
					}
				}
			}
		}
    }

}
