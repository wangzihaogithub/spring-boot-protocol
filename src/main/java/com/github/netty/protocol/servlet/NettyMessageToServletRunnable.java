package com.github.netty.protocol.servlet;

import com.github.netty.core.MessageToRunnable;
import com.github.netty.core.util.*;
import com.github.netty.protocol.servlet.util.HttpHeaderUtil;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufHolder;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPipeline;
import io.netty.handler.codec.http.*;

import javax.servlet.RequestDispatcher;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletResponse;

import static com.github.netty.protocol.servlet.ServletHttpExchange.*;
import static com.github.netty.protocol.servlet.util.HttpHeaderConstants.*;

/**
 * Life cycle connection
 * NettyMessageToServletRunnable
 * @author wangzihao
 */
public class NettyMessageToServletRunnable implements MessageToRunnable {
    private static final LoggerX LOGGER = LoggerFactoryX.getLogger(NettyMessageToServletRunnable.class);
    private static final Recycler<HttpRunnable> RECYCLER = new Recycler<>(HttpRunnable::new);
    private static final FullHttpResponse CONTINUE =
            new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.CONTINUE, Unpooled.EMPTY_BUFFER);
    private static final FullHttpResponse EXPECTATION_FAILED = new DefaultFullHttpResponse(
            HttpVersion.HTTP_1_1, HttpResponseStatus.EXPECTATION_FAILED, Unpooled.EMPTY_BUFFER);
    private static final FullHttpResponse TOO_LARGE_CLOSE = new DefaultFullHttpResponse(
            HttpVersion.HTTP_1_1, HttpResponseStatus.REQUEST_ENTITY_TOO_LARGE, Unpooled.EMPTY_BUFFER);
    private static final FullHttpResponse TOO_LARGE = new DefaultFullHttpResponse(
            HttpVersion.HTTP_1_1, HttpResponseStatus.REQUEST_ENTITY_TOO_LARGE, Unpooled.EMPTY_BUFFER);
    private static final FullHttpResponse NOT_ACCEPTABLE_CLOSE = new DefaultFullHttpResponse(
            HttpVersion.HTTP_1_1, HttpResponseStatus.NOT_ACCEPTABLE, Unpooled.EMPTY_BUFFER);

    private ServletContext servletContext;
    private ServletHttpExchange exchange;
    private long maxContentLength;
    private ChannelFutureListener continueResponseWriteListener;
    private boolean handlingOversizedMessage;
    private boolean closeOnExpectationFailed = true;
    private ServletInputStreamWrapper inputStream;

    static {
        EXPECTATION_FAILED.headers().set(CONTENT_LENGTH, 0);
        TOO_LARGE.headers().set(CONTENT_LENGTH, 0);

        TOO_LARGE_CLOSE.headers().set(CONTENT_LENGTH, 0);
        TOO_LARGE_CLOSE.headers().set(CONNECTION, CLOSE);

        NOT_ACCEPTABLE_CLOSE.headers().set(CONTENT_LENGTH, 0);
        NOT_ACCEPTABLE_CLOSE.headers().set(CONNECTION, CLOSE);
    }

    public NettyMessageToServletRunnable(ServletContext servletContext,long maxContentLength) {
        this.servletContext = servletContext;
        this.maxContentLength = maxContentLength;
    }

    @Override
    public Runnable onMessage(ChannelHandlerContext context, Object msg) {
        ServletHttpExchange exchange = this.exchange;
        if(msg instanceof HttpRequest) {
            if(continueResponse(context, (HttpRequest) msg)){
                HttpRunnable instance = RECYCLER.getInstance();
                instance.servletHttpExchange = exchange = this.exchange = ServletHttpExchange.newInstance(
                        servletContext,
                        context,
                        (HttpRequest) msg);
                this.inputStream = exchange.getRequest().getInputStream0();
                return instance;
            }else {
                return null;
            }
        }else if(exchange != null && exchange.closeStatus() == CLOSE_NO && inputStream != null){
            inputStream.onMessage(msg);
            return null;
        }else if(exchange != null){
            if(exchange.closeStatus() == CLOSE_YES){
                ByteBuf byteBuf;
                if(msg instanceof ByteBufHolder){
                    byteBuf = ((ByteBufHolder) msg).content();
                }else if(msg instanceof ByteBuf){
                    byteBuf = (ByteBuf) msg;
                    context.close();
                }else {
                    byteBuf = null;
                }
                if(byteBuf != null && byteBuf.isReadable()){
                    LOGGER.warn("http packet discard = {}",msg);
                    context.close();
                }
            }
            RecyclableUtil.release(msg);
            return null;
        }else {
            RecyclableUtil.release(msg);
            return null;
        }
    }

    private boolean continueResponse(ChannelHandlerContext context,HttpRequest httpRequest){
        Object continueResponse = newContinueResponse(httpRequest, maxContentLength, context.pipeline());
        if (continueResponse != null) {
            // Cache the write listener for reuse.
            ChannelFutureListener listener = continueResponseWriteListener;
            if (listener == null) {
                continueResponseWriteListener = listener = future -> {
                    if (!future.isSuccess()) {
                        context.fireExceptionCaught(future.cause());
                    }
                };
            }

            // Make sure to call this before writing, otherwise reference counts may be invalid.
            boolean closeAfterWrite = closeAfterContinueResponse(continueResponse);
            handlingOversizedMessage = ignoreContentAfterContinueResponse(continueResponse);

            final ChannelFuture future = context.writeAndFlush(continueResponse).addListener(listener);
            if (closeAfterWrite) {
                future.addListener(ChannelFutureListener.CLOSE);
                return false;
            }
            if (handlingOversizedMessage) {
                return false;
            }
        } else if (isContentLengthInvalid(httpRequest, maxContentLength)) {
            // if content length is set, preemptively close if it's too large
            try{
                invokeHandleOversizedMessage(context);
            } finally {
                // Release the message in case it is a full one.
                RecyclableUtil.release(httpRequest);
            }
            return false;
        }
        return true;
    }

    public void setCloseOnExpectationFailed(boolean closeOnExpectationFailed) {
        this.closeOnExpectationFailed = closeOnExpectationFailed;
    }

    private void invokeHandleOversizedMessage(ChannelHandlerContext ctx) {
        handlingOversizedMessage = true;
        // send back a 413 and close the connection
        ChannelFuture future = ctx.writeAndFlush(TOO_LARGE_CLOSE.retainedDuplicate());
        future.addListener((ChannelFutureListener) future1 -> {
            if (!future1.isSuccess()) {
                LOGGER.debug("Failed to send a 413 Request Entity Too Large.", future1.cause());
            }
            future1.channel().close();
        });
    }

    protected boolean isContentLengthInvalid(HttpMessage start, long maxContentLength) {
        try {
//            return HttpHeaderUtil.getContentLength(start, -1L) > maxContentLength;
        } catch (NumberFormatException e) {
        }
        return false;
    }
    protected boolean closeAfterContinueResponse(Object msg) {
        return closeOnExpectationFailed && ignoreContentAfterContinueResponse(msg);
    }

    protected boolean ignoreContentAfterContinueResponse(Object msg) {
        if (msg instanceof HttpResponse) {
            final HttpResponse httpResponse = (HttpResponse) msg;
            return httpResponse.status().codeClass().equals(HttpStatusClass.CLIENT_ERROR);
        }
        return false;
    }
    protected Object newContinueResponse(HttpMessage start, long maxContentLength, ChannelPipeline pipeline) {
        Object response = continueResponse(start, maxContentLength, pipeline);
        // we're going to respond based on the request expectation so there's no
        // need to propagate the expectation further.
        if (response != null) {
            start.headers().remove(EXPECT);
        }
        return response;
    }

    private static Object continueResponse(HttpMessage start, long maxContentLength, ChannelPipeline pipeline) {
        if (HttpHeaderUtil.isUnsupportedExpectation(start)) {
            // if the request contains an unsupported expectation, we return 417
            pipeline.fireUserEventTriggered(HttpExpectationFailedEvent.INSTANCE);
            return EXPECTATION_FAILED.retainedDuplicate();
        } else if (HttpUtil.is100ContinueExpected(start)) {
            // if the request contains 100-continue but the content-length is too large, we return 413
            if (HttpHeaderUtil.getContentLength(start, -1L) <= maxContentLength) {
                return CONTINUE.retainedDuplicate();
            }
            pipeline.fireUserEventTriggered(HttpExpectationFailedEvent.INSTANCE);
            return TOO_LARGE.retainedDuplicate();
        }

        return null;
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

            try {
                ServletRequestDispatcher dispatcher = servletHttpExchange.getServletContext().getRequestDispatcher(httpServletRequest.getRequestURI());
                if (dispatcher == null) {
                    httpServletResponse.sendError(HttpServletResponse.SC_NOT_FOUND);
                    return;
                }
                dispatcher.dispatch(httpServletRequest, httpServletResponse);
            }catch (ServletException se){
                realThrowable = se.getRootCause();
            }catch (Throwable throwable){
                realThrowable = throwable;
            }finally {
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
            }
        }

        @Override
        public void recycle() {
            servletHttpExchange = null;
            RECYCLER.recycleInstance(HttpRunnable.this);
        }
    }

}
