package com.github.netty.core;

/**
 * An abstract Protocols Register
 * @author wangzihao
 */
public abstract class AbstractProtocol implements ProtocolHandler,ServerListener {

    @Override
    public String toString() {
        return getProtocolName();
    }
}
