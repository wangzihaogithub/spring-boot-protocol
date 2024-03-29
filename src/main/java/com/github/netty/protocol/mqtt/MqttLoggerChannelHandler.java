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
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandler.Sharable;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import io.netty.handler.codec.mqtt.*;

import java.io.IOException;
import java.util.List;

/**
 * @author wangzihao
 */
@Sharable
public class MqttLoggerChannelHandler extends AbstractChannelHandler<MqttMessage, MqttMessage> {
    public MqttLoggerChannelHandler() {
        super(false);
    }

    @Override
    public void onMessageReceived(ChannelHandlerContext ctx, MqttMessage message) throws Exception {
        logMQTTMessage(ctx, message, "C->B");
        ctx.fireChannelRead(message);
    }

    @Override
    protected void onMessageWriter(ChannelHandlerContext ctx, MqttMessage msg, ChannelPromise promise) throws Exception {
        logMQTTMessage(ctx, msg, "C<-B");
        if (promise.isVoid()) {
            ctx.write(msg, promise);
        } else {
            ctx.write(msg, promise).addListener(ChannelFutureListener.CLOSE_ON_FAILURE);
        }
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        String clientID = MqttUtil.clientID(ctx.channel());
        if (clientID != null && !clientID.isEmpty()) {
            logger.debug("Channel closed <{}>", clientID);
        }
        ctx.fireChannelInactive();
    }

    private void logMQTTMessage(ChannelHandlerContext ctx, MqttMessage msg, String direction) throws Exception {
        if (msg.fixedHeader() == null) {
            throw new IOException("Unknown packet");
        }
        String clientID = MqttUtil.clientID(ctx.channel());
        MqttMessageType messageType = msg.fixedHeader().messageType();
        switch (messageType) {
            case CONNACK:
            case PINGREQ:
            case PINGRESP:
                logger.debug("{} {} <{}>", direction, messageType, clientID);
                break;
            case CONNECT:
            case DISCONNECT:
                logger.debug("{} {} <{}>", direction, messageType, clientID);
                break;
            case SUBSCRIBE:
                MqttSubscribeMessage subscribe = (MqttSubscribeMessage) msg;
                logger.debug("{} SUBSCRIBE <{}> to topics {}", direction, clientID,
                        subscribe.payload().topicSubscriptions());
                break;
            case UNSUBSCRIBE:
                MqttUnsubscribeMessage unsubscribe = (MqttUnsubscribeMessage) msg;
                logger.debug("{} UNSUBSCRIBE <{}> to topics <{}>", direction, clientID, unsubscribe.payload().topics());
                break;
            case PUBLISH:
                MqttPublishMessage publish = (MqttPublishMessage) msg;
                logger.debug("{} PUBLISH <{}> to topics <{}>", direction, clientID, publish.variableHeader().topicName());
                break;
            case PUBREC:
            case PUBCOMP:
            case PUBREL:
            case PUBACK:
            case UNSUBACK:
                logger.debug("{} {} <{}> packetID <{}>", direction, messageType, clientID, MqttUtil.messageId(msg));
                break;
            case SUBACK:
                MqttSubAckMessage suback = (MqttSubAckMessage) msg;
                final List<Integer> grantedQoSLevels = suback.payload().grantedQoSLevels();
                logger.debug("{} SUBACK <{}> packetID <{}>, grantedQoses {}", direction, clientID, MqttUtil.messageId(msg),
                        grantedQoSLevels);
                break;
            default:
                break;
        }
    }

}
