package com.github.netty.register;

import com.github.netty.core.ProtocolsRegister;
import com.github.netty.register.mqtt.MqttServerChannelHandler;
import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import io.netty.channel.ChannelPipeline;
import io.netty.handler.codec.mqtt.MqttDecoder;
import io.netty.handler.codec.mqtt.MqttEncoder;

/**
 * Created by acer01 on 2018/12/5/005.
 */
public class MqttProtocolsRegister implements ProtocolsRegister {
    public static final int ORDER = NRpcProtocolsRegister.ORDER + 100;

    private int messageMaxLength;
    private MqttServerChannelHandler channelHandler = new MqttServerChannelHandler();

    public MqttProtocolsRegister(int messageMaxLength) {
        this.messageMaxLength = messageMaxLength;
    }

    @Override
    public String getProtocolName() {
        return "mqtt";
    }

    @Override
    public boolean canSupport(ByteBuf msg) {
        return false;
    }

    @Override
    public void register(Channel channel) throws Exception {
        ChannelPipeline pipeline = channel.pipeline();
        pipeline.addLast(MqttEncoder.INSTANCE);
        pipeline.addLast(new MqttDecoder(messageMaxLength));
        pipeline.addLast(channelHandler);
    }

    @Override
    public int order() {
        return ORDER;
    }

    @Override
    public void onServerStart() throws Exception {

    }

    @Override
    public void onServerStop() throws Exception {

    }
}
