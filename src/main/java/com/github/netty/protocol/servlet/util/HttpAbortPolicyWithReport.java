package com.github.netty.protocol.servlet.util;

import com.github.netty.core.util.AbortPolicyWithReport;
import com.github.netty.protocol.servlet.NettyMessageToServletRunnable;
import com.github.netty.protocol.servlet.ServletHttpExchange;

import javax.servlet.http.HttpServletResponse;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * http refusal to handle the strategy, send 503 status code
 * <p>
 * Status code (503) indicating that the HTTP server is
 * temporarily overloaded, and unable to handle the request.
 *
 * @author wangzihaogithub 2020-11-21
 * @see #rejectedExecution(NettyMessageToServletRunnable.HttpRunnable, ThreadPoolExecutor, ServletHttpExchange)
 */
public class HttpAbortPolicyWithReport extends AbortPolicyWithReport {
    public HttpAbortPolicyWithReport(String threadName, String dumpPath, String info) {
        super(threadName, dumpPath, info);
    }

    @Override
    public void rejectedExecution(Runnable r, ThreadPoolExecutor e) {
        if (r instanceof NettyMessageToServletRunnable.HttpRunnable) {
            NettyMessageToServletRunnable.HttpRunnable httpRunnable = (NettyMessageToServletRunnable.HttpRunnable) r;
            rejectedExecution(httpRunnable, e, httpRunnable.getExchange());
        } else {
            super.rejectedExecution(r, e);
        }
    }

    protected void rejectedExecution(NettyMessageToServletRunnable.HttpRunnable httpRunnable,
                                     ThreadPoolExecutor e, ServletHttpExchange exchange) {
        exchange.getResponse().setStatus(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
        exchange.getResponse().setHeader("Connection", "Close");
        super.rejectedExecution(httpRunnable, e);
    }
}
