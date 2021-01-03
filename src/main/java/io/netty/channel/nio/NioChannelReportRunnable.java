package io.netty.channel.nio;

import com.github.netty.core.util.LoggerX;
import com.github.netty.protocol.TcpChannel;
import io.netty.channel.ChannelOutboundBuffer;
import io.netty.channel.EventLoop;
import io.netty.channel.SingleThreadEventLoop;

import java.nio.channels.SelectionKey;

public class NioChannelReportRunnable implements Runnable {
    private LoggerX logger;

    public NioChannelReportRunnable(LoggerX logger) {
        this.logger = logger;
    }

    @Override
    public void run() {
        if (!TcpChannel.getChannels().values().isEmpty()) {
            for (TcpChannel ctx : TcpChannel.getChannels().values()) {
                SelectionKey selectionKey = ((AbstractNioChannel) ctx.getChannel()).selectionKey();
                boolean isFlushPending = selectionKey.isValid() && (selectionKey.interestOps() & SelectionKey.OP_WRITE) != 0;
                ChannelOutboundBuffer outboundBuffer = ctx.getChannel().unsafe().outboundBuffer();
                long totalPendingWriteBytes = outboundBuffer == null ? 0 : outboundBuffer.totalPendingWriteBytes();
                EventLoop eventLoop = ctx.getChannel().eventLoop();

                boolean inEventLoop = eventLoop.inEventLoop();
                int pendingTasks = ((SingleThreadEventLoop) ctx.getChannel().eventLoop()).pendingTasks();

                logger.info("remote = {}, isFlushPending = {}, totalPendingWriteBytes = {}/B, eventLoop = {}, pendingTasks = {}",
                        ctx.getChannel().remoteAddress(),
                        isFlushPending,
                        totalPendingWriteBytes,
                        eventLoop,
                        pendingTasks);
            }
            logger.info("-----------------------");
        }
    }

}
