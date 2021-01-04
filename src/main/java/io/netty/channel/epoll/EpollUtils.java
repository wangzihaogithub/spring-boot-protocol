package io.netty.channel.epoll;

import io.netty.channel.Channel;

public class EpollUtils {

    public static void forceFlush(Channel.Unsafe unsafe){
        if(unsafe instanceof AbstractEpollChannel.AbstractEpollUnsafe){
            AbstractEpollChannel.AbstractEpollUnsafe epollUnsafe = (AbstractEpollChannel.AbstractEpollUnsafe) unsafe;
            epollUnsafe.epollOutReady();
        }
    }
}
