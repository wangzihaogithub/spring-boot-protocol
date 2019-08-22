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
import io.netty.channel.ChannelHandler.Sharable;
import io.netty.channel.ChannelHandlerContext;

import static io.netty.channel.ChannelFutureListener.CLOSE_ON_FAILURE;

@Sharable
public class MqttIdleTimeoutChannelHandler extends AbstractChannelHandler {
    public MqttIdleTimeoutChannelHandler() {
        super(false);
    }

    @Override
    protected void onReaderIdle(ChannelHandlerContext ctx) {
        logger.trace("Firing channel inactive event. MqttClientId = {}.", MqttUtil.clientID(ctx.channel()));
        // fire a close that then fire channelInactive to trigger publish of Will
        ctx.close().addListener(CLOSE_ON_FAILURE);
    }
}
