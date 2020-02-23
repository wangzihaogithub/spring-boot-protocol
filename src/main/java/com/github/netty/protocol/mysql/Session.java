package com.github.netty.protocol.mysql;

import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.util.AttributeKey;
import io.netty.util.concurrent.GenericFutureListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Session {
	private static Logger logger = LoggerFactory.getLogger(Session.class);
    public static final AttributeKey<Session> SESSION_KEY = AttributeKey.valueOf("session");
    private volatile Channel clientChannel;
    private volatile Channel mysqlChannel;
    
    private static class ConnectionCloseFutureListener implements GenericFutureListener<ChannelFuture> {
    	private final Session session;
    	public ConnectionCloseFutureListener(Session session) {
    		this.session = session;
    	}
    	
		@Override
		public void operationComplete(ChannelFuture future) throws Exception {
			Channel ch = future.channel();
			Channel mysqlChannel = session.mysqlChannel;
			Channel clientChannel = session.clientChannel;
			if (ch == clientChannel) {
				logger.info("clientChannel channel [{}] closed!", clientChannel);
				// clientChannel connection close but it's mysqlChannel connection is still active or open, close it!
				if(mysqlChannel != null) {
					if (mysqlChannel.isActive() || mysqlChannel.isOpen()) {
						mysqlChannel.close();
					}
				}
			} else {
				logger.info("mysqlChannel channel [{}] closed!", mysqlChannel);
				// mysqlChannel connection close but it's clientChannel connection is still active or open, close it!
				if(clientChannel != null) {
					if (clientChannel.isActive() || clientChannel.isOpen()) {
						clientChannel.close();
					}
				}
			}
		}
    }

	public void setClientChannel(Channel clientChannel) {
		clientChannel.attr(Session.SESSION_KEY).set(this);
		clientChannel.closeFuture().addListener(new ConnectionCloseFutureListener(this));
		this.clientChannel = clientChannel;
	}

	public void setMysqlChannel(Channel mysqlChannel) {
		mysqlChannel.attr(Session.SESSION_KEY).set(this);
		mysqlChannel.closeFuture().addListener(new ConnectionCloseFutureListener(this));
		this.mysqlChannel = mysqlChannel;
	}

    public Channel getMysqlChannel() {
        return this.mysqlChannel;
    }
    
    public Channel getClientChannel() {
        return this.clientChannel;
    }

}
