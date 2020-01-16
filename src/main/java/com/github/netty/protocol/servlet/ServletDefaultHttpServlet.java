package com.github.netty.protocol.servlet;

import com.github.netty.core.util.LoggerFactoryX;
import com.github.netty.core.util.LoggerX;

import javax.servlet.AsyncContext;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * a default servlet
 * @author wangzihao
 *  2018/7/15/015
 */
public class ServletDefaultHttpServlet extends HttpServlet {
    private LoggerX logger = LoggerFactoryX.getLogger(getClass());

    @Override
    protected void service(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        AsyncContext context = request.startAsync();
        context.start(()->{
            try {
                response.getWriter().write("404");
                context.complete();
            } catch (IOException e) {
                logger.error("service error ={}",e.toString(),e);
            }
        });
    }
}
