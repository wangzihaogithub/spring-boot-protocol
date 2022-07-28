package com.github.netty.protocol;

import com.github.netty.core.AbstractNettyServer;
import com.github.netty.core.AbstractProtocol;
import com.github.netty.core.util.IOUtil;
import com.github.netty.protocol.rtsp.RtspServerChannelHandler;
import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelPipeline;
import io.netty.handler.codec.http.HttpConstants;
import io.netty.handler.codec.rtsp.RtspDecoder;
import io.netty.handler.codec.rtsp.RtspEncoder;

/**
 * Real-time streaming media transfer protocol (commonly used for live streaming, video)
 *
 * @author wangzihao
 * 2018/12/5/005
 */
public class RtspProtocol extends AbstractProtocol {
    private final int maxInitialLineLength;
    private final int maxHeaderSize;
    private final int maxContentLength;
    private ChannelHandler channelHandler;

    public RtspProtocol() {
        this(4096, 8192, 8192, new RtspServerChannelHandler());
    }

    public RtspProtocol(int maxInitialLineLength, int maxHeaderSize, int maxContentLength, ChannelHandler channelHandler) {
        this.maxInitialLineLength = maxInitialLineLength;
        this.maxHeaderSize = maxHeaderSize;
        this.maxContentLength = maxContentLength;
        this.channelHandler = channelHandler;
    }

    @Override
    public String getProtocolName() {
        return "rtsp";
    }

    @Override
    public boolean canSupport(ByteBuf msg) {
        int protocolEndIndex = IOUtil.indexOf(msg, HttpConstants.LF);
        if (protocolEndIndex < 9) {
            return false;
        }

        return msg.getByte(protocolEndIndex - 9) == 'R'
                && msg.getByte(protocolEndIndex - 8) == 'T'
                && msg.getByte(protocolEndIndex - 7) == 'S'
                && msg.getByte(protocolEndIndex - 6) == 'P';
    }

    @Override
    public void addPipeline(Channel channel, ByteBuf clientFirstMsg) throws Exception {
        ChannelPipeline pipeline = channel.pipeline();
        pipeline.addLast(new RtspEncoder());
        pipeline.addLast(new RtspDecoder(maxInitialLineLength, maxHeaderSize, maxContentLength, false));
        pipeline.addLast(channelHandler);
    }

    @Override
    public int getOrder() {
        return 400;
    }

    @Override
    public <T extends AbstractNettyServer> void onServerStart(T server) throws Exception {

    }

    @Override
    public <T extends AbstractNettyServer> void onServerStop(T server) throws Exception {

    }
}
