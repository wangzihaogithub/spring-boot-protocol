package com.github.netty.protocol.servlet;

import com.github.netty.core.MessageToRunnable;
import com.github.netty.core.util.*;
import com.github.netty.protocol.servlet.util.HttpHeaderUtil;
import com.github.netty.protocol.servlet.util.Protocol;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufHolder;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.*;
import io.netty.util.concurrent.FastThreadLocal;

import javax.servlet.RequestDispatcher;
import javax.servlet.Servlet;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletResponse;
import java.nio.channels.ClosedChannelException;
import java.nio.charset.Charset;
import java.util.*;

import static com.github.netty.protocol.servlet.ServletHttpExchange.CLOSE_NO;
import static com.github.netty.protocol.servlet.util.HttpHeaderConstants.*;

/**
 * Life cycle connection
 * NettyMessageToServletRunnable
 *
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
    private static final Set<HttpMethod> HTTP_METHOD_SET = new HashSet<>(9);

    static {
        EXPECTATION_FAILED.headers().set(CONTENT_LENGTH, 0);
        TOO_LARGE.headers().set(CONTENT_LENGTH, 0);

        TOO_LARGE_CLOSE.headers().set(CONTENT_LENGTH, 0);
        TOO_LARGE_CLOSE.headers().set(CONNECTION, CLOSE);

        NOT_ACCEPTABLE_CLOSE.headers().set(CONTENT_LENGTH, 0);
        NOT_ACCEPTABLE_CLOSE.headers().set(CONNECTION, CLOSE);

        HTTP_METHOD_SET.addAll(Arrays.asList(
                HttpMethod.OPTIONS, HttpMethod.GET, HttpMethod.HEAD,
                HttpMethod.POST, HttpMethod.PUT, HttpMethod.PATCH,
                HttpMethod.DELETE, HttpMethod.TRACE, HttpMethod.CONNECT));
    }

    private final ServletContext servletContext;
    private final long maxContentLength;
    private final Protocol protocol;
    private final boolean ssl;
    private ServletHttpExchange exchange;
    private /*volatile */ HttpRunnable httpRunnable;

    private static final FastThreadLocal<List<Runnable>> ASYNC_CONTEXT_DISPATCH_THREAD_LOCAL = new FastThreadLocal<>();

    static boolean isCurrentRunAtRequesting() {
        return ASYNC_CONTEXT_DISPATCH_THREAD_LOCAL.get() != null;
    }

    static void addAsyncContextDispatch(Runnable runnable) {
        List<Runnable> list = ASYNC_CONTEXT_DISPATCH_THREAD_LOCAL.get();
        if (list != null) {
            list.add(runnable);
        }
    }

    public NettyMessageToServletRunnable(ServletContext servletContext, long maxContentLength, Protocol protocol, boolean ssl) {
        this.servletContext = servletContext;
        this.maxContentLength = maxContentLength;
        this.protocol = protocol;
        this.ssl = ssl;
    }

    @Override
    public Runnable onMessage(ChannelHandlerContext context, Object msg) {
        ServletHttpExchange exchange = this.exchange;
        boolean needDiscard = true;
        Runnable result = null;

        // header
        if (msg instanceof HttpRequest) {
            HttpRequest request = (HttpRequest) msg;
            long contentLength = HttpHeaderUtil.getContentLength(request, -1L);
            if (continueResponse(context, request, contentLength)) {
                needDiscard = false;
                HttpRunnable httpRunnable = RECYCLER.getInstance();
                httpRunnable.exchange = exchange = this.exchange = ServletHttpExchange.newInstance(
                        servletContext,
                        context,
                        request,
                        protocol,
                        ssl);
                exchange.getRequest().getInputStream0().setContentLength(contentLength);
                this.httpRunnable = httpRunnable;
            }
        }

        // body
        if (msg instanceof HttpContent && exchange.closeStatus() == CLOSE_NO) {
            needDiscard = false;
            exchange.getRequest().getInputStream0().onMessage((HttpContent) msg);
            if (exchange.getRequest().isMultipart() || msg instanceof LastHttpContent) {
                result = this.httpRunnable;
                this.httpRunnable = null;
            }
        }

        // abort discard
        if (needDiscard) {
            discard(msg);
        }
        return result;
    }

    @Override
    public Runnable onClose(ChannelHandlerContext context) {
        ServletHttpExchange exchange = this.exchange;
        if (exchange != null) {
            if (exchange.isAsyncStartIng()) {
                exchange.abort();
                ServletAsyncContext asyncContext = exchange.getAsyncContext();
                if (asyncContext != null && !asyncContext.isComplete()) {
                    asyncContext.complete(new ClosedChannelException());
                }
            } else if (exchange.closeStatus() == CLOSE_NO) {
                exchange.abort();
            }
            exchange.close();
        }
        return null;
    }

    @Override
    public Runnable onError(ChannelHandlerContext context, Throwable throwable) {
        ServletHttpExchange exchange = this.exchange;
        if (exchange != null && exchange.isAsyncStartIng()) {
            ServletAsyncContext asyncContext = exchange.getAsyncContext();
            if (asyncContext != null) {
                asyncContext.onError(throwable);
            }
        }
        return null;
    }

    protected void discard(Object msg) {
        try {
            ByteBuf byteBuf;
            if (msg instanceof ByteBufHolder) {
                byteBuf = ((ByteBufHolder) msg).content();
            } else if (msg instanceof ByteBuf) {
                byteBuf = (ByteBuf) msg;
            } else {
                byteBuf = null;
            }
            ServletHttpExchange exchange = this.exchange;
            if (byteBuf != null && byteBuf.isReadable() && LOGGER.isDebugEnabled()) {
                LOGGER.debug("http packet discard {} = '{}', exchange.closeStatus = {}, httpRunnable = {}",
                        msg.getClass().getName(),
                        byteBuf.toString(byteBuf.readerIndex(), Math.min(byteBuf.readableBytes(), 100), Charset.forName("UTF-8")),
                        exchange != null ? exchange.closeStatus() : "null",
                        httpRunnable);
            }
        } finally {
            RecyclableUtil.release(msg);
        }
    }

    protected boolean continueResponse(ChannelHandlerContext context, HttpRequest httpRequest, long contentLength) {
        if (httpRequest.method() == HttpMethod.OPTIONS) {
            return true;
        }
        if (!HTTP_METHOD_SET.contains(httpRequest.method())) {
            return true;
        }

        boolean success;
        Object continueResponse;
        if (HttpHeaderUtil.isUnsupportedExpectation(httpRequest)) {
            // if the request contains an unsupported expectation, we return 417
            context.pipeline().fireUserEventTriggered(HttpExpectationFailedEvent.INSTANCE);
            continueResponse = EXPECTATION_FAILED.retainedDuplicate();
            success = false;
        } else if (HttpUtil.is100ContinueExpected(httpRequest)) {
            // if the request contains 100-continue but the content-length is too large, we return 413
            if (contentLength <= maxContentLength) {
                continueResponse = CONTINUE.retainedDuplicate();
                success = true;
            } else {
                context.pipeline().fireUserEventTriggered(HttpExpectationFailedEvent.INSTANCE);
                continueResponse = TOO_LARGE.retainedDuplicate();
                success = false;
            }
        } else {
            continueResponse = null;
            success = true;
        }
        // we're going to respond based on the request expectation so there's no
        // need to propagate the expectation further.
        if (continueResponse != null) {
            httpRequest.headers().remove(EXPECT);
            context.writeAndFlush(continueResponse);
        }
        return success;
    }

    /**
     * http task
     */
    public static class HttpRunnable implements Runnable, Recyclable {
        public static final LoggerX logger = LoggerFactoryX.getLogger(HttpRunnable.class);
        private ServletHttpExchange exchange;

        public ServletHttpExchange getExchange() {
            return exchange;
        }

        public void setExchange(ServletHttpExchange exchange) {
            this.exchange = exchange;
        }

        @Override
        public void run() {
            ServletHttpServletRequest request = exchange.getRequest();
            ServletHttpServletResponse response = exchange.getResponse();
            ServletErrorPageManager errorPageManager = exchange.getServletContext().getErrorPageManager();
            Throwable realThrowable = null;

            // upload cannot block event loop
            if (request.isMultipart()
                    && exchange.getChannelHandlerContext().executor().inEventLoop()) {
                exchange.getServletContext().getDefaultExecutorSupplier().get().execute(this);
                return;
            }

            LinkedList<Runnable> asyncContextDispatchOperList = new LinkedList<>();
            ASYNC_CONTEXT_DISPATCH_THREAD_LOCAL.set(asyncContextDispatchOperList);
            ServletRequestDispatcher dispatcher = null;
            try {
                dispatcher = request.getRequestDispatcher(request.getRequestURI());
                if (dispatcher == null) {
                    Servlet defaultServlet = exchange.getServletContext().getDefaultServlet();
                    if (defaultServlet != null) {
                        defaultServlet.service(request, response);
                    } else {
                        response.sendError(HttpServletResponse.SC_NOT_FOUND);
                    }
                } else {
                    request.setAsyncSupportedFlag(dispatcher.getFilterChain().getServletRegistration().isAsyncSupported());
                    request.setDispatcher(dispatcher);
                    dispatcher.dispatch(request, response);
                }
            } catch (ServletException se) {
                realThrowable = se.getRootCause();
            } catch (Throwable throwable) {
                realThrowable = throwable;
            } finally {
                try {
                    handleErrorPage(errorPageManager, realThrowable, dispatcher, request, response);
                } catch (Throwable e) {
                    logger.warn("handleErrorPage error = {}", e.toString(), e);
                } finally {
                    if (dispatcher != null) {
                        dispatcher.recycle();
                    }
                    /*
                     * If not asynchronous, or asynchronous has ended
                     * each response object is valid only if it is within the scope of the servlet's service method or the filter's doFilter method, unless the
                     * the request object associated with the component has started asynchronous processing. If the relevant request has already started asynchronous processing, then up to the AsyncContext
                     * complete method is called, and the request object remains valid. To avoid the performance overhead of creating response objects, the container typically recycles the response object.
                     * before the startAsync of the relevant request is invoked, the developer must be aware that the response object reference remains outside the scope described above
                     * circumference may lead to uncertain behavior
                     */
                    if (request.isAsync()) {
                        ServletAsyncContext asyncContext = request.getAsyncContext();
                        //If the asynchronous execution completes, recycle
                        if (asyncContext.isComplete()) {
                            asyncContext.recycle();
                        } else {
                            //Marks the end of execution for the main thread
                            request.getAsyncContext().markIoThreadOverFlag();
                            if (asyncContext.isComplete()) {
                                asyncContext.recycle();
                            }
                        }
                    } else {
                        //Not asynchronous direct collection
                        exchange.close();
                    }
                    recycle();
                }

                try {
                    while (!asyncContextDispatchOperList.isEmpty()) {
                        List<Runnable> asyncContextDispatchOperListCopy = new ArrayList<>(asyncContextDispatchOperList);
                        asyncContextDispatchOperList.clear();
                        for (Runnable runnable : asyncContextDispatchOperListCopy) {
                            runnable.run();
                        }
                    }
                } finally {
                    ASYNC_CONTEXT_DISPATCH_THREAD_LOCAL.remove();
                }
            }
        }

        protected void handleErrorPage(ServletErrorPageManager errorPageManager, Throwable realThrowable, ServletRequestDispatcher requestDispatcher, ServletHttpServletRequest request, ServletHttpServletResponse response) {
            /*
             * Error pages are obtained according to two types: 1. By exception type; 2. By status code
             */
            if (realThrowable == null) {
                realThrowable = (Throwable) request.getAttribute(RequestDispatcher.ERROR_EXCEPTION);
            }
            ServletErrorPage errorPage = null;
            if (realThrowable != null) {
                errorPage = errorPageManager.find(realThrowable);
                if (errorPage == null) {
                    response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
                    errorPage = errorPageManager.find(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
                }
                if (errorPage == null) {
                    errorPage = errorPageManager.find(0);
                }
            } else if (response.isError()) {
                errorPage = errorPageManager.find(response.getStatus());
                if (errorPage == null) {
                    errorPage = errorPageManager.find(0);
                }
            }
            //Error page
            if (realThrowable != null || errorPage != null) {
                errorPageManager.handleErrorPage(errorPage, realThrowable, requestDispatcher, request, response);
            }
        }

        @Override
        public void recycle() {
            exchange = null;
            RECYCLER.recycleInstance(HttpRunnable.this);
        }

        @Override
        public String toString() {
            return Optional.ofNullable(exchange)
                    .map(ServletHttpExchange::getRequest)
                    .map(ServletHttpServletRequest::getNettyRequest)
                    .map(String::valueOf)
                    .orElse("recycle");
        }
    }

}
