package com.github.netty.springboot.client;

import java.util.Collections;
import java.util.List;

/**
 * Information about the RPC request
 * @author wangzihao
 */
@FunctionalInterface
public interface NettyRpcFilter {

    void doFilter(NettyRpcFullRequest request, FilterChain chain) throws Throwable;

    interface FilterChain {
        void doFilter(NettyRpcFullRequest request) throws Throwable;
        /**
         * get a unmodifiable NettyRpcFilterList {@link Collections#unmodifiableList(List)}
         * @return NettyRpcFilter
         */
        List<NettyRpcFilter> getNettyRpcFilterList();
    }
}
