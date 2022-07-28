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

import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.mqtt.MqttMessage;
import io.netty.handler.codec.mqtt.MqttMessageIdVariableHeader;
import io.netty.util.Attribute;
import io.netty.util.AttributeKey;

import java.nio.charset.Charset;
import java.util.Map;

/**
 * Some Netty's channels utilities.
 */
public final class MqttUtil {

    private static final AttributeKey<Integer> ATTR_KEY_KEEPALIVE = AttributeKey.valueOf(String.class + "#keepAlive");
    private static final AttributeKey<Boolean> ATTR_KEY_CLEANSESSION = AttributeKey.valueOf(Boolean.class + "#removeTemporaryQoS2");
    private static final AttributeKey<String> ATTR_KEY_CLIENTID = AttributeKey.valueOf(String.class + "#ClientID");
    private static final AttributeKey<String> ATTR_KEY_USERNAME = AttributeKey.valueOf(String.class + "#username");

    private MqttUtil() {
    }

    public static Object getAttribute(ChannelHandlerContext ctx, AttributeKey<Object> key) {
        Attribute<Object> attr = ctx.channel().attr(key);
        return attr.get();
    }

    public static void keepAlive(Channel channel, int keepAlive) {
        channel.attr(MqttUtil.ATTR_KEY_KEEPALIVE).set(keepAlive);
    }

    public static void cleanSession(Channel channel, boolean cleanSession) {
        channel.attr(MqttUtil.ATTR_KEY_CLEANSESSION).set(cleanSession);
    }

    public static boolean cleanSession(Channel channel) {
        return channel.attr(MqttUtil.ATTR_KEY_CLEANSESSION).get();
    }

    public static void clientID(Channel channel, String clientID) {
        channel.attr(MqttUtil.ATTR_KEY_CLIENTID).set(clientID);
    }

    public static String clientID(Channel channel) {
        return channel.attr(MqttUtil.ATTR_KEY_CLIENTID).get();
    }

    public static void userName(Channel channel, String username) {
        channel.attr(MqttUtil.ATTR_KEY_USERNAME).set(username);
    }

    public static String userName(Channel channel) {
        return channel.attr(MqttUtil.ATTR_KEY_USERNAME).get();
    }

    public static <T, K> T defaultGet(Map<K, T> map, K key, T defaultValue) {
        T value = map.get(key);
        if (value != null) {
            return value;
        }
        return defaultValue;
    }

    public static int messageId(MqttMessage msg) {
        return ((MqttMessageIdVariableHeader) msg.variableHeader()).messageId();
    }

    public static byte[] readBytesAndRewind(ByteBuf payload) {
        byte[] payloadContent = new byte[payload.readableBytes()];
        int mark = payload.readerIndex();
        payload.readBytes(payloadContent);
        payload.readerIndex(mark);
        return payloadContent;
    }

    public static String payload2Str(ByteBuf content) {
        final ByteBuf copy = content.copy();
        final byte[] bytesContent;
        if (copy.isDirect()) {
            final int size = copy.readableBytes();
            bytesContent = new byte[size];
            copy.readBytes(bytesContent);
        } else {
            bytesContent = copy.array();
        }
        return new String(bytesContent, Charset.forName("UTF-8"));
    }
}
