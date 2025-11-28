package io.netty.channel.kqueue;

import io.netty.channel.Channel;

public class KqueueUtils {

    public static boolean forceFlush(Channel.Unsafe unsafe) {
        if (unsafe instanceof AbstractKQueueChannel.AbstractKQueueUnsafe) {
            AbstractKQueueChannel.AbstractKQueueUnsafe epollUnsafe = (AbstractKQueueChannel.AbstractKQueueUnsafe) unsafe;
            epollUnsafe.flush0();
            return true;
        } else {
            return false;
        }
    }
}
