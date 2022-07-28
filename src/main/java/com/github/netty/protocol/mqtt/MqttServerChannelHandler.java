/*
 * Copyright (c) 2012-2018 The original author or authors
 * ------------------------------------------------------
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * and Apache License v2.0 which accompanies this distribution.
 *
 * The Eclipse Public License is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * The Apache License v2.0 is available at
 * http://www.opensource.org/licenses/apache2.0.php
 *
 * You may elect to redistribute this code under either of these licenses.
 */

package com.github.netty.protocol.mqtt;

import com.github.netty.core.AbstractChannelHandler;
import com.github.netty.core.AutoFlushChannelHandler;
import com.github.netty.protocol.mqtt.config.BrokerConfiguration;
import com.github.netty.protocol.mqtt.interception.BrokerInterceptor;
import com.github.netty.protocol.mqtt.security.IAuthenticator;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandler.Sharable;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.mqtt.MqttMessage;
import io.netty.util.AttributeKey;

import java.io.IOException;

import static io.netty.channel.ChannelFutureListener.CLOSE_ON_FAILURE;

/**
 * 开发一个MQTT库需要提供如下命令：
 * Connect ：当一个TCP/IP套接字在服务器端和客户端连接建立时需要使用的命令。
 * publish  ： 是由客户端向服务端发送，告诉服务器端自己感兴趣的Topic。每一个publishMessage 都会与一个Topic的名字联系在一起。
 * pubRec:   是publish命令的响应，只不过使用了2级QoS协议。它是2级QoS协议的第二条消息
 * pubRel:    是2级QoS协议的第三条消息
 * publComp: 是2级QoS协议的第四条消息
 * subscribe： 允许一个客户端注册自已感兴趣的Topic 名字，发布到这些Topic的消息会以publish Message的形式由服务器端发送给客户端。
 * unsubscribe:  从客户端到服务器端，退订一个Topic。
 * Ping： 有客户端向服务器端发送的“are you alive”的消息。
 * disconnect：断开这个TCP/IP协议。
 */
@Sharable
public class MqttServerChannelHandler extends AbstractChannelHandler<MqttMessage, Object> {

    private static final AttributeKey<MqttConnection> ATTR_KEY_CONNECTION = AttributeKey.valueOf(MqttConnection.class + "#MQTTConnection");

    private final BrokerConfiguration brokerConfig;
    private final IAuthenticator authenticator;
    private final MqttSessionRegistry sessionRegistry;
    private final MqttPostOffice postOffice;
    private final BrokerInterceptor interceptor;

    public MqttServerChannelHandler(BrokerInterceptor interceptor, BrokerConfiguration brokerConfig, IAuthenticator authenticator,
                                    MqttSessionRegistry sessionRegistry, MqttPostOffice postOffice) {
        super(true);
        this.interceptor = interceptor;
        this.brokerConfig = brokerConfig;
        this.authenticator = authenticator;
        this.sessionRegistry = sessionRegistry;
        this.postOffice = postOffice;
    }

    private MqttConnection mqttConnection(Channel channel) {
        return channel.attr(ATTR_KEY_CONNECTION).get();
    }

    private void mqttConnection(Channel channel, MqttConnection connection) {
        channel.attr(ATTR_KEY_CONNECTION).set(connection);
    }

    @Override
    public void onMessageReceived(ChannelHandlerContext ctx, MqttMessage msg) throws Exception {
        if (msg.fixedHeader() == null) {
            throw new IOException("Unknown packet");
        }

        MqttConnection mqttConnection = mqttConnection(ctx.channel());
        mqttConnection.setAuthFlushed(AutoFlushChannelHandler.isAutoFlush(ctx.pipeline()));
        try {
            mqttConnection.handleMessage(msg);
        } catch (Throwable ex) {
            //ctx.fireExceptionCaught(ex);
            logger.error("Error processing protocol message: " + msg.fixedHeader().messageType(), ex);
            ctx.channel().close().addListener(new ChannelFutureListener() {
                @Override
                public void operationComplete(ChannelFuture future) {
                    logger.debug("Closed client channel due to exception in processing");
                }
            });
        }
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) {
        Channel channel = ctx.channel();
        MqttConnection connection = new MqttConnection(interceptor, channel, brokerConfig, authenticator, sessionRegistry, postOffice);
        connection.setAuthFlushed(AutoFlushChannelHandler.isAutoFlush(ctx.pipeline()));
        mqttConnection(channel, connection);
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) {
        final MqttConnection mqttConnection = mqttConnection(ctx.channel());
        mqttConnection.handleConnectionLost();
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        logger.error("Unexpected exception while processing MQTT message. Closing Netty channel. CId=" + MqttUtil.clientID(ctx.channel()), cause);
        ctx.close().addListener(CLOSE_ON_FAILURE);
    }

    @Override
    public void channelWritabilityChanged(ChannelHandlerContext ctx) {
//        if (ctx.channel().isWritable()) {
//            m_processor.notifyChannelWritable(ctx.channel());
//        }
        final MqttConnection mqttConnection = mqttConnection(ctx.channel());
        mqttConnection.writabilityChanged();
        ctx.fireChannelWritabilityChanged();
    }

    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) {
        if (evt instanceof MqttInflightResenderChannelHandler.ResendNotAckedPublishes) {
            final MqttConnection mqttConnection = mqttConnection(ctx.channel());
            mqttConnection.resendNotAckedPublishes();
        }
        ctx.fireUserEventTriggered(evt);
    }

}
