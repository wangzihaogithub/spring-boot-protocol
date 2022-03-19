package org.springframework.web.socket.server.standard;

import com.github.netty.springboot.server.NettyRequestUpgradeStrategy;

public class TomcatRequestUpgradeStrategy extends NettyRequestUpgradeStrategy {
    @Override
    public String toString() {
        return "NettyRequestUpgradeStrategy";
    }
}
