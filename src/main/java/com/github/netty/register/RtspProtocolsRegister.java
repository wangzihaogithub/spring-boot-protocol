package com.github.netty.register;

import com.github.netty.core.ProtocolsRegister;
import com.github.netty.core.util.IOUtil;
import com.github.netty.register.rtsp.RtspServerChannelHandler;
import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import io.netty.channel.ChannelPipeline;
import io.netty.handler.codec.http.HttpConstants;
import io.netty.handler.codec.rtsp.RtspDecoder;
import io.netty.handler.codec.rtsp.RtspEncoder;

/**
 *
 * 流媒体传输协议
 * @author acer01
 *  2018/12/5/005
 */
public class RtspProtocolsRegister implements ProtocolsRegister {
    public static final int ORDER = MqttProtocolsRegister.ORDER + 100;
    private final int maxInitialLineLength;
    private final int maxHeaderSize;
    private final int maxContentLength;
    private RtspServerChannelHandler channelHandler = new RtspServerChannelHandler();

    public RtspProtocolsRegister() {
        this(4096,8192,8192);
    }

    public RtspProtocolsRegister(int maxInitialLineLength, int maxHeaderSize, int maxContentLength) {
        this.maxInitialLineLength = maxInitialLineLength;
        this.maxHeaderSize = maxHeaderSize;
        this.maxContentLength = maxContentLength;
    }

    @Override
    public String getProtocolName() {
        return "rtsp";
    }

    @Override
    public boolean canSupport(ByteBuf msg) {
        int protocolEndIndex = IOUtil.indexOf(msg, HttpConstants.LF);
        if(protocolEndIndex < 9){
            return false;
        }

        if(msg.getByte(protocolEndIndex - 9) == 'R'
                && msg.getByte(protocolEndIndex - 8) == 'T'
                && msg.getByte(protocolEndIndex - 7) == 'S'
                &&  msg.getByte(protocolEndIndex - 6) == 'P'){
            return true;
        }
        return false;
    }

    @Override
    public void register(Channel channel) throws Exception {
        ChannelPipeline pipeline = channel.pipeline();
        pipeline.addLast(new RtspEncoder());
        pipeline.addLast(new RtspDecoder(maxInitialLineLength,maxHeaderSize,maxContentLength,false));
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
