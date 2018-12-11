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
import com.github.netty.core.metrics.ConsoleReporter;
import com.github.netty.core.metrics.Counter;
import com.github.netty.core.metrics.Meter;
import com.github.netty.core.metrics.MetricRegistry;
import com.github.netty.protocol.mqtt.config.IConfig;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.mqtt.MqttMessage;
import io.netty.handler.codec.mqtt.MqttMessageType;

import java.util.concurrent.TimeUnit;

import static com.github.netty.protocol.mqtt.config.BrokerConstants.*;

/**
 * Pipeline handler use to track some MQTT metrics.
 */
@ChannelHandler.Sharable
public final class MqttDropWizardMetricsChannelHandler extends AbstractChannelHandler<MqttMessage,Object> {
    private MetricRegistry metrics = new MetricRegistry();
    private Meter publishesMetrics = metrics.meter("publish.requests");;
    private Meter subscribeMetrics = metrics.meter("subscribe.requests");
    private Counter connectedClientsMetrics = metrics.counter("connect.num_clients");

    public MqttDropWizardMetricsChannelHandler() {
        super(false);
    }

    public void init(IConfig props) {
        String email = props.getProperty(METRICS_LIBRATO_EMAIL_PROPERTY_NAME);
        String token = props.getProperty(METRICS_LIBRATO_TOKEN_PROPERTY_NAME);
        String source = props.getProperty(METRICS_LIBRATO_SOURCE_PROPERTY_NAME);
        init(email,token,source);
    }

    public void init(String email,String token,String source) {
        ConsoleReporter reporter = ConsoleReporter.forRegistry(metrics)
            .convertRatesTo(TimeUnit.SECONDS)
            .convertDurationsTo(TimeUnit.MILLISECONDS)
            .build();
        reporter.start(1, TimeUnit.MINUTES);


//        Librato.reporter(this.metrics, email, token)
//            .setSource(source)
//            .start(10, TimeUnit.SECONDS);
    }

    @Override
    protected void onMessageReceived(ChannelHandlerContext ctx, MqttMessage msg) throws Exception {
        MqttMessageType messageType = msg.fixedHeader().messageType();
        switch (messageType) {
            case PUBLISH:
                this.publishesMetrics.mark();
                break;
            case SUBSCRIBE:
                this.subscribeMetrics.mark();
                break;
            case CONNECT:
                this.connectedClientsMetrics.inc();
                break;
            case DISCONNECT:
                this.connectedClientsMetrics.dec();
                break;
            default:
                break;
        }
        ctx.fireChannelRead(msg);
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        String clientID = MqttUtil.clientID(ctx.channel());
        if (clientID != null && !clientID.isEmpty()) {
            this.connectedClientsMetrics.dec();
        }
        ctx.fireChannelInactive();
    }

}
