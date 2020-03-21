package com.github.netty.core;

import com.github.netty.core.util.LoggerFactoryX;
import com.github.netty.core.util.LoggerX;
import com.github.netty.core.util.RecyclableUtil;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import io.netty.handler.timeout.IdleStateEvent;
import io.netty.util.internal.TypeParameterMatcher;

/**
 *  An abstract netty ChannelHandler
 * @author wangzihao
 */
public abstract class AbstractChannelHandler<I,O> extends ChannelDuplexHandler {
    protected LoggerX logger = LoggerFactoryX.getLogger(getClass());
    private final TypeParameterMatcher matcherInbound;
    private final TypeParameterMatcher matcherOutbound;
    private final boolean autoRelease;

    protected AbstractChannelHandler() {
        this(true);
    }

    protected AbstractChannelHandler(boolean autoRelease) {
        matcherInbound = TypeParameterMatcher.find(this, AbstractChannelHandler.class, "I");
        matcherOutbound = TypeParameterMatcher.find(this, AbstractChannelHandler.class, "O");
        this.autoRelease = autoRelease;
    }

    @Override
    public final void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        boolean release = true;
        try {
            boolean match = matcherInbound.match(msg);
            if (logger.isTraceEnabled()) {
                logger.trace("ChannelRead({}) -> match({}) ",messageToString(msg),match);
            }
            if (match) {
                I imsg = (I) msg;
                onMessageReceived(ctx, imsg);
            } else {
                release = false;
                ctx.fireChannelRead(msg);
            }
        } finally {
            if (autoRelease && release) {
                RecyclableUtil.release(msg);
            }
        }
    }

    protected void onMessageReceived(ChannelHandlerContext ctx, I msg) throws Exception{
        ctx.fireChannelRead(msg);
    }

    @Override
    public final void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
        boolean match = matcherOutbound.match(msg);
        if(logger.isTraceEnabled()) {
            logger.trace("ChannelWrite({}) -> match({}) ", messageToString(msg), match);
        }
        if (match) {
            O imsg = (O) msg;
            onMessageWriter(ctx, imsg,promise);
        }else {
            ctx.write(msg, promise);
        }
    }

    protected void onMessageWriter(ChannelHandlerContext ctx, O msg, ChannelPromise promise) throws Exception{
        ctx.write(msg,promise);
    }

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
        }else {
            onUserEventTriggered(ctx,evt);
        }
        ctx.fireUserEventTriggered(evt);
    }

    protected void onUserEventTriggered(ChannelHandlerContext ctx,Object evt){

    }

    protected void onAllIdle(ChannelHandlerContext ctx){

    }

    protected void onWriterIdle(ChannelHandlerContext ctx){

    }

    protected void onReaderIdle(ChannelHandlerContext ctx){

    }

    public String messageToString(Object msg){
        if(msg == null){
            return "null";
        }
        return msg.getClass().getSimpleName();
    }
}
