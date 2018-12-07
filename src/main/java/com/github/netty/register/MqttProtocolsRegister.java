package com.github.netty.register;

import com.github.netty.core.ProtocolsRegister;
import com.github.netty.register.mqtt.MqttServerChannelHandler;
import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelPipeline;
import io.netty.handler.codec.mqtt.MqttDecoder;
import io.netty.handler.codec.mqtt.MqttEncoder;

/**
 * 物联网传输协议
 * @author acer01
 *  2018/12/5/005
 */
public class MqttProtocolsRegister implements ProtocolsRegister {
    public static final int ORDER = NRpcProtocolsRegister.ORDER + 100;

    private int messageMaxLength;
    private ChannelHandler channelHandler;

    public MqttProtocolsRegister() {
        this(8092);
    }

    public MqttProtocolsRegister(int messageMaxLength) {
        this(messageMaxLength, new MqttServerChannelHandler());
    }

    public MqttProtocolsRegister(int messageMaxLength,ChannelHandler channelHandler) {
        this.messageMaxLength = messageMaxLength;
        this.channelHandler = channelHandler;
    }

    @Override
    public String getProtocolName() {
        return "mqtt";
    }

    @Override
    public boolean canSupport(ByteBuf msg) {
        if(msg.readableBytes() < 9){
            return false;
        }

        if( msg.getByte(4) == 'M'
                &&  msg.getByte(5) == 'Q'
                &&  msg.getByte(6) == 'T'
                &&   msg.getByte(7) == 'T'){
            return true;
        }
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
