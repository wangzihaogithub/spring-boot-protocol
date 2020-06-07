package com.github.netty.protocol.servlet;

import com.github.netty.core.MessageToRunnable;
import com.github.netty.core.util.Recyclable;
import com.github.netty.core.util.Recycler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.FullHttpRequest;

import javax.servlet.ReadListener;
import javax.servlet.RequestDispatcher;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletResponse;
//import java.util.concurrent.atomic.AtomicLong;

/**
 * NettyMessageToServletRunnable
 * @author wangzihao
 */
public class NettyMessageToServletRunnable implements MessageToRunnable {
    private static final Recycler<HttpRunnable> RECYCLER = new Recycler<>(HttpRunnable::new);
//    public static final AtomicLong SERVLET_AND_FILTER_TIME = new AtomicLong();
//    public static final AtomicLong SERVLET_QUERY_COUNT = new AtomicLong();

    private ServletContext servletContext;

    public NettyMessageToServletRunnable(ServletContext servletContext) {
        this.servletContext = servletContext;
    }

    @Override
    public Runnable newRunnable(ChannelHandlerContext context, Object msg) {
        if(!(msg instanceof FullHttpRequest)) {
            throw new IllegalStateException("Message type not supported");
        }

        HttpRunnable instance = RECYCLER.getInstance();
        instance.servletHttpExchange = ServletHttpExchange.newInstance(
                servletContext,
                context,
                (FullHttpRequest) msg);
        return instance;
    }

    /**
     * http task
     */
    public static class HttpRunnable implements Runnable, Recyclable {
        private ServletHttpExchange servletHttpExchange;

        public ServletHttpExchange getServletHttpExchange() {
            return servletHttpExchange;
        }

        public void setServletHttpExchange(ServletHttpExchange servletHttpExchange) {
            this.servletHttpExchange = servletHttpExchange;
        }

        @Override
        public void run() {
            ServletHttpServletRequest httpServletRequest = servletHttpExchange.getRequest();
            ServletHttpServletResponse httpServletResponse = servletHttpExchange.getResponse();
            Throwable realThrowable = null;

//            long beginTime = System.currentTimeMillis();
            try {
                ServletRequestDispatcher dispatcher = servletHttpExchange.getServletContext().getRequestDispatcher(httpServletRequest.getRequestURI());
                if (dispatcher == null) {
                    httpServletResponse.sendError(HttpServletResponse.SC_NOT_FOUND);
                    return;
                }
//                servletHttpExchange.touch(this);
                dispatcher.dispatch(httpServletRequest, httpServletResponse);

                if(httpServletRequest.isAsync()){
                    ReadListener readListener = httpServletRequest.getInputStream0().getReadListener();
                    if(readListener != null){
                        readListener.onAllDataRead();
                    }
                }
            }catch (ServletException se){
                realThrowable = se.getRootCause();
            }catch (Throwable throwable){
                realThrowable = throwable;
            }finally {
//                long totalTime = System.currentTimeMillis() - beginTime;
//                SERVLET_AND_FILTER_TIME.addAndGet(totalTime);

                /*
                 * Error pages are obtained according to two types: 1. By exception type; 2. By status code
                 */
                if(realThrowable == null) {
                    realThrowable = (Throwable) httpServletRequest.getAttribute(RequestDispatcher.ERROR_EXCEPTION);
                }
                ServletErrorPage errorPage = null;
                ServletErrorPageManager errorPageManager = servletHttpExchange.getServletContext().getErrorPageManager();
                if(realThrowable != null){
                    errorPage = errorPageManager.find(realThrowable);
                    if(errorPage == null) {
                        httpServletResponse.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
                        errorPage = errorPageManager.find(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
                    }
                    if(errorPage == null) {
                        errorPage = errorPageManager.find(0);
                    }
                }else if(httpServletResponse.isError()) {
                    errorPage = errorPageManager.find(httpServletResponse.getStatus());
                    if(errorPage == null) {
                        errorPage = errorPageManager.find(0);
                    }
                }
                //Error page
                if(realThrowable != null || errorPage != null) {
                    errorPageManager.handleErrorPage(errorPage, realThrowable, httpServletRequest, httpServletResponse);
                }
                /*
                 * If not asynchronous, or asynchronous has ended
                 * each response object is valid only if it is within the scope of the servlet's service method or the filter's doFilter method, unless the
                 * the request object associated with the component has started asynchronous processing. If the relevant request has already started asynchronous processing, then up to the AsyncContext
                 * complete method is called, and the request object remains valid. To avoid the performance overhead of creating response objects, the container typically recycles the response object.
                 * before the startAsync of the relevant request is invoked, the developer must be aware that the response object reference remains outside the scope described above
                 * circumference may lead to uncertain behavior
                 */
                if(httpServletRequest.isAsync()){
                    ServletAsyncContext asyncContext = httpServletRequest.getAsyncContext();
                    //If the asynchronous execution completes, recycle
                    if(asyncContext.isComplete()){
                        asyncContext.recycle();
                    }else {
                        //Marks the end of execution for the main thread
                        httpServletRequest.getAsyncContext().markIoThreadOverFlag();
                        if(asyncContext.isComplete()) {
                            asyncContext.recycle();
                        }
                    }
                }else {
                    //Not asynchronous direct collection
                    servletHttpExchange.recycle();
                }

                recycle();
//                SERVLET_QUERY_COUNT.incrementAndGet();
            }
        }

        @Override
        public void recycle() {
            servletHttpExchange = null;
            RECYCLER.recycleInstance(HttpRunnable.this);
        }
    }

}
