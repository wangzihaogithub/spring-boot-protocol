package io.netty.channel;

import io.netty.channel.epoll.EpollUtils;
import io.netty.channel.kqueue.KqueueUtils;
import io.netty.channel.nio.AbstractNioChannel;

public class ChannelUtils {

    public static void forceFlush(Channel channel) {
        Channel.Unsafe unsafe = channel.unsafe();
        if (EpollUtils.forceFlush(unsafe)) {
            return;
        }
        if (KqueueUtils.forceFlush(unsafe)) {
            return;
        }
        if (unsafe instanceof AbstractNioChannel.NioUnsafe) {
            ((AbstractNioChannel.NioUnsafe) unsafe).forceFlush();
        }
    }
}
