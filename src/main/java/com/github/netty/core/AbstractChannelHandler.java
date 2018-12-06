package com.github.netty.core;

import com.github.netty.core.util.LoggerFactoryX;
import com.github.netty.core.util.LoggerX;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.timeout.IdleStateEvent;
import io.netty.util.ReferenceCountUtil;
import io.netty.util.internal.TypeParameterMatcher;

/**
 *  用于兼容 netty4 与netty5
 * @author 84215
 */
public abstract class AbstractChannelHandler<I> extends ChannelDuplexHandler {

    protected LoggerX logger = LoggerFactoryX.getLogger(getClass());

    private final TypeParameterMatcher matcher;
    private final boolean autoRelease;

    protected AbstractChannelHandler() {
        this(true);
    }

    protected AbstractChannelHandler(boolean autoRelease) {
        matcher = TypeParameterMatcher.find(this, AbstractChannelHandler.class, "I");
        this.autoRelease = autoRelease;
    }

    @Override
    public final void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        boolean release = true;
        try {
            if (matcher.match(msg)) {
                I imsg = (I) msg;
                onMessageReceived(ctx, imsg);
            } else {
                release = false;
                ctx.fireChannelRead(msg);
            }
        } finally {
            if (autoRelease && release) {
                ReferenceCountUtil.release(msg);
            }
        }
    }

    protected abstract void onMessageReceived(ChannelHandlerContext ctx, I msg) throws Exception;

    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
        if(evt instanceof IdleStateEvent){
            IdleStateEvent e = (IdleStateEvent) evt;
            switch (e.state()) {
                case READER_IDLE:
                    onReaderIdle(ctx);
                    break;
                case WRITER_IDLE:
                    onWriterIdle(ctx);
                    break;
                case ALL_IDLE:
                    onAllIdle(ctx);
                    break;
                default:
                    break;
            }
        }
    }

    protected void onAllIdle(ChannelHandlerContext ctx){

    }

    protected void onWriterIdle(ChannelHandlerContext ctx){

    }

    protected void onReaderIdle(ChannelHandlerContext ctx){

    }

}
