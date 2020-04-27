package com.github.netty.nrpc.client.example;

import com.github.netty.springboot.client.NettyRpcFilter;
import com.github.netty.springboot.client.NettyRpcFullRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Order(2)
@Component
public class HelloNettyRpcFilter2 implements NettyRpcFilter {
    private final Logger logger = LoggerFactory.getLogger(getClass());

    @Override
    public void doFilter(NettyRpcFullRequest request, FilterChain chain) throws Throwable {
        logger.info("Filter2 begin.");
        Object response = null;
        try {
            response = request.getResponse();
            chain.doFilter(request);
        }finally {
            logger.info("Filter2 end. response = " + response);
        }
    }
}
