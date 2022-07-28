package com.github.netty.protocol.mqtt;

import io.netty.handler.codec.mqtt.MqttQoS;

import java.io.Serializable;

public class MqttRetainedMessage implements Serializable {

    private final MqttQoS qos;
    private final byte[] payload;

    public MqttRetainedMessage(MqttQoS qos, byte[] payload) {
        this.qos = qos;
        this.payload = payload;
    }

    public MqttQoS qosLevel() {
        return qos;
    }

    public byte[] getPayload() {
        return payload;
    }
}
