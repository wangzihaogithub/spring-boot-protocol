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

import io.netty.channel.*;
import io.netty.channel.ChannelHandler.Sharable;
import io.netty.handler.codec.mqtt.MqttMessage;
import io.netty.util.Attribute;
import io.netty.util.AttributeKey;
import io.netty.util.ReferenceCountUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

import static io.netty.channel.ChannelFutureListener.CLOSE_ON_FAILURE;

/**
 *  开发一个MQTT库需要提供如下命令：
     Connect ：当一个TCP/IP套接字在服务器端和客户端连接建立时需要使用的命令。
     publish  ： 是由客户端向服务端发送，告诉服务器端自己感兴趣的Topic。每一个publishMessage 都会与一个Topic的名字联系在一起。
     pubRec:   是publish命令的响应，只不过使用了2级QoS协议。它是2级QoS协议的第二条消息
     pubRel:    是2级QoS协议的第三条消息
     publComp: 是2级QoS协议的第四条消息
     subscribe： 允许一个客户端注册自已感兴趣的Topic 名字，发布到这些Topic的消息会以publish Message的形式由服务器端发送给客户端。
     unsubscribe:  从客户端到服务器端，退订一个Topic。
     Ping： 有客户端向服务器端发送的“are you alive”的消息。
     disconnect：断开这个TCP/IP协议。
 */
@Sharable
public class MqttServerChannelHandler extends ChannelInboundHandlerAdapter {

    private static final Logger LOG = LoggerFactory.getLogger(MqttServerChannelHandler.class);

    private static final String ATTR_CONNECTION = "connection";
    private static final AttributeKey<MQTTConnection> ATTR_KEY_CONNECTION = AttributeKey.valueOf(ATTR_CONNECTION);

    private MQTTConnectionFactory connectionFactory;

    public MqttServerChannelHandler(MQTTConnectionFactory connectionFactory) {
        this.connectionFactory = connectionFactory;
    }

    private MQTTConnection mqttConnection(Channel channel) {
        Attribute<MQTTConnection> attribute = channel.attr(ATTR_KEY_CONNECTION);
        MQTTConnection connection = attribute.get();
        if(connection == null) {
            connection = connectionFactory.create(channel);
            attribute.set(connection);
        }
        return connection;
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object message) throws Exception {
        MqttMessage msg = (MqttMessage) message;
        if (msg.fixedHeader() == null) {
            throw new IOException("Unknown packet");
        }
        final MQTTConnection mqttConnection = mqttConnection(ctx.channel());
        try {
            mqttConnection.handleMessage(msg);
        } catch (Throwable ex) {
            //ctx.fireExceptionCaught(ex);
            LOG.error("Error processing protocol message: {}", msg.fixedHeader().messageType(), ex);
            ctx.channel().close().addListener(new ChannelFutureListener() {
                @Override
                public void operationComplete(ChannelFuture future) {
                    LOG.info("Closed client channel due to exception in processing");
                }
            });
        } finally {
            ReferenceCountUtil.release(msg);
        }
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) {
        mqttConnection(ctx.channel());
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) {
        final MQTTConnection mqttConnection = mqttConnection(ctx.channel());
        mqttConnection.handleConnectionLost();
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        LOG.error("Unexpected exception while processing MQTT message. Closing Netty channel. CId={}",
                  NettyUtils.clientID(ctx.channel()), cause);
        ctx.close().addListener(CLOSE_ON_FAILURE);
    }

    @Override
    public void channelWritabilityChanged(ChannelHandlerContext ctx) {
//        if (ctx.channel().isWritable()) {
//            m_processor.notifyChannelWritable(ctx.channel());
//        }
        final MQTTConnection mqttConnection = mqttConnection(ctx.channel());
        mqttConnection.writabilityChanged();
        ctx.fireChannelWritabilityChanged();
    }

    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) {
        if (evt instanceof InflightResender.ResendNotAckedPublishes) {
            final MQTTConnection mqttConnection = mqttConnection(ctx.channel());
            mqttConnection.resendNotAckedPublishes();
        }
        ctx.fireUserEventTriggered(evt);
    }

}
