package com.github.netty.protocol.servlet;

import com.github.netty.core.util.ExpiryLRUMap;
import com.github.netty.core.util.LoggerFactoryX;
import com.github.netty.core.util.LoggerX;
import com.github.netty.core.util.Recyclable;

import javax.servlet.*;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Context for asynchronous processing
 *
 * @author wangzihao
 * 2018/7/15/015
 */
public class ServletAsyncContext implements AsyncContext, Recyclable {
    private static final LoggerX logger = LoggerFactoryX.getLogger(ServletAsyncContext.class);
    private static final int STATUS_INIT = 0;
    private static final int STATUS_START = 1;
    private static final int STATUS_DISPATCH = 2;
    private static final int STATUS_COMPLETE = 3;
    private static final AtomicInteger TASK_ID_INCR = new AtomicInteger();
    private static final ExpiryLRUMap<Integer, ServletAsyncContext> TIMEOUT_TASK_MAP = new ExpiryLRUMap<>(256, Long.MAX_VALUE, Long.MAX_VALUE, null);

    static {
        TIMEOUT_TASK_MAP.setOnExpiryConsumer(e -> {
            ServletAsyncContext asyncContext = e.getData();
            if (asyncContext.status.get() >= STATUS_DISPATCH) {
                return;
            }
            asyncContext.executor.execute(asyncContext.timeoutTask);
        });
    }

    private final Executor executor;
    private final AtomicBoolean timeoutFlag = new AtomicBoolean();
    /**
     * Has it been recycled
     */
    private AtomicBoolean recycleFlag = new AtomicBoolean(false);
    ;
    /**
     * Whether the IO thread has finished executing
     */
    private AtomicBoolean ioThreadExecuteOverFlag = new AtomicBoolean(false);
    /**
     * 0=init, 1=start, 2=complete
     */
    private AtomicInteger status = new AtomicInteger(STATUS_INIT);
    /**
     * Timeout time -> ms
     */
    private long timeout;
    private List<ServletAsyncListenerWrapper> asyncListenerWrapperList;
    private final Runnable timeoutTask = () -> {
        //Notice the timeout
        if (timeoutFlag.compareAndSet(false, true)) {
            if (asyncListenerWrapperList == null) {
                return;
            }
            Throwable throwable = null;
            boolean eventNotify = false;
            for (ServletAsyncListenerWrapper listenerWrapper : new ArrayList<>(asyncListenerWrapperList)) {
                eventNotify = throwable != null;
                AsyncEvent event = new AsyncEvent(this, listenerWrapper.servletRequest, listenerWrapper.servletResponse, throwable);
                try {
                    listenerWrapper.asyncListener.onTimeout(event);
                } catch (Throwable e) {
                    if (throwable != null) {
                        e.addSuppressed(throwable);
                    }
                    throwable = e;
                }
            }
            if (throwable != null && !eventNotify) {
                logger.error("asyncContext notifyEvent.onTimeout() error={}", throwable.toString(), throwable);
            }
        }
    };
    private ServletContext servletContext;
    private ServletHttpExchange servletHttpExchange;
    private ServletRequest servletRequest;
    private ServletResponse servletResponse;
    private volatile Integer timeoutTaskId;
    private volatile long startTimestamp;

    public ServletAsyncContext(ServletHttpExchange servletHttpExchange, ServletContext servletContext, Executor executor) {
        this.servletHttpExchange = Objects.requireNonNull(servletHttpExchange);
        this.servletContext = Objects.requireNonNull(servletContext);
        this.executor = Objects.requireNonNull(executor);
    }

    public ServletContext getServletContext() {
        return servletContext;
    }

    @Override
    public ServletRequest getRequest() {
        return servletRequest;
    }

    @Override
    public ServletResponse getResponse() {
        return servletResponse;
    }

    @Override
    public boolean hasOriginalRequestAndResponse() {
        return servletHttpExchange.getRequest() == servletRequest && servletHttpExchange.getResponse() == servletResponse;
    }

    public void setServletResponse(ServletResponse servletResponse) {
        this.servletResponse = servletResponse;
    }

    public void setServletRequest(ServletRequest servletRequest) {
        this.servletRequest = servletRequest;
    }

    @Override
    public void dispatch() {
        String path;
        String contextPath;
        ServletRequest servletRequest = this.servletRequest;
        if (servletRequest instanceof HttpServletRequest) {
            path = ((HttpServletRequest) servletRequest).getRequestURI();
            contextPath = ((HttpServletRequest) servletRequest).getContextPath();
        } else {
            path = servletHttpExchange.getRequest().getRequestURI();
            contextPath = servletHttpExchange.getRequest().getContextPath();
        }
        if (contextPath.length() > 1) {
            path = path.substring(contextPath.length());
        }
        dispatch(servletContext, path);
    }

    @Override
    public void dispatch(String path) {
        dispatch(servletContext, path);
    }

    @Override
    public void dispatch(javax.servlet.ServletContext context, String path) {
        if(isComplete()){
            return;
        }
        status.set(STATUS_DISPATCH);
        String contextPath = context.getContextPath();
        String dispatcherPath = contextPath == null || contextPath.isEmpty() ? path : contextPath + path;
        HttpServletRequest httpServletRequest;
        HttpServletResponse httpServletResponse;
        if (servletRequest instanceof HttpServletRequest) {
            httpServletRequest = (HttpServletRequest) servletRequest;
        } else {
            httpServletRequest = servletHttpExchange.getRequest();
        }
        if (servletResponse instanceof HttpServletResponse) {
            httpServletResponse = (HttpServletResponse) servletResponse;
        } else {
            httpServletResponse = servletHttpExchange.getResponse();
        }

        ServletRequestDispatcher dispatcher = servletContext.getRequestDispatcher(dispatcherPath, DispatcherType.ASYNC);
        try {
            if (dispatcher == null) {
                logger.warn("not found dispatcher. contextPath={}, path={}", context.getContextPath(), path);
                httpServletResponse.setStatus(HttpServletResponse.SC_NOT_FOUND);
            } else {
                try {
                    dispatcher.dispatchAsync(httpServletRequest, httpServletResponse, this);
                } catch (Throwable e) {
                    onError(e);
                }
            }
        } finally {
            complete();
        }
    }

    public void onError(Throwable throwable) {
        //Notify the complete
        if (asyncListenerWrapperList != null) {
            boolean eventNotify = false;
            for (ServletAsyncListenerWrapper listenerWrapper : new ArrayList<>(asyncListenerWrapperList)) {
                eventNotify = throwable != null;
                AsyncEvent event = new AsyncEvent(this, listenerWrapper.servletRequest, listenerWrapper.servletResponse, throwable);
                try {
                    listenerWrapper.asyncListener.onError(event);
                } catch (Throwable e) {
                    if (throwable != null) {
                        e.addSuppressed(throwable);
                    }
                    throwable = e;
                }
            }
            if (throwable != null && !eventNotify) {
                logger.error("asyncContext notifyEvent.onError() error={}", throwable.toString(), throwable);
            }
        }
    }

    @Override
    public void complete() {
        complete(null);
    }

    public void complete(Throwable rootThrowable){
        if (isComplete()) {
            return;
        }
        Integer timeoutTaskId = this.timeoutTaskId;
        if (timeoutTaskId != null) {
            TIMEOUT_TASK_MAP.remove(timeoutTaskId);
        }
        try {
            //Notify the complete
            if (asyncListenerWrapperList != null) {
                Throwable throwable = rootThrowable;
                boolean eventNotify = false;
                for (ServletAsyncListenerWrapper listenerWrapper : new ArrayList<>(asyncListenerWrapperList)) {
                    eventNotify = throwable != null;
                    AsyncEvent event = new AsyncEvent(this, listenerWrapper.servletRequest, listenerWrapper.servletResponse, throwable);
                    try {
                        listenerWrapper.asyncListener.onComplete(event);
                    } catch (Throwable e) {
                        if (throwable != null) {
                            e.addSuppressed(throwable);
                        }
                        throwable = e;
                    }
                }
                if (throwable != null && !eventNotify) {
                    logger.error("asyncContext notifyEvent.onComplete() error={}", throwable.toString(), throwable);
                }
            }
            //If the handler has finished, recycle it yourself
            if (ioThreadExecuteOverFlag.get()) {
                recycle();
            }
        } finally {
            status.set(STATUS_COMPLETE);
        }
    }

    /**
     * Marks the end of the main thread
     */
    public void markIoThreadOverFlag() {
        this.ioThreadExecuteOverFlag.compareAndSet(false, true);
    }

    @Override
    public void recycle() {
        //If not, recycle
        if (recycleFlag.compareAndSet(false, true)) {
            servletHttpExchange.recycle();
        }
    }

    @Override
    public void start(Runnable runnable) {
        executor.execute(runnable);
    }

    public void setStart() {
        if (status.get() >= STATUS_DISPATCH || timeoutFlag.get()) {
            throw new IllegalStateException("The request associated with the AsyncContext has already completed processing.");
        }
        startTimestamp = System.currentTimeMillis();
        if (timeoutTaskId == null) {
            timeoutTaskId = TASK_ID_INCR.getAndIncrement();
        } else {
            TIMEOUT_TASK_MAP.remove(timeoutTaskId);
        }
        status.set(STATUS_START);
        TIMEOUT_TASK_MAP.put(timeoutTaskId, this, timeout);

        //Notify the start
        if (asyncListenerWrapperList != null) {
            List<ServletAsyncListenerWrapper> list = new ArrayList<>(asyncListenerWrapperList);
            asyncListenerWrapperList.clear();
            Throwable throwable = null;
            boolean eventNotify = false;
            for (ServletAsyncListenerWrapper listenerWrapper : list) {
                eventNotify = throwable != null;
                AsyncEvent event = new AsyncEvent(this, listenerWrapper.servletRequest, listenerWrapper.servletResponse, throwable);
                try {
                    listenerWrapper.asyncListener.onStartAsync(event);
                } catch (Throwable e) {
                    if (throwable != null) {
                        e.addSuppressed(throwable);
                    }
                    throwable = e;
                }
            }
            if (throwable != null && !eventNotify) {
                logger.error("asyncContext notifyEvent.onTimeout() error={}", throwable.toString(), throwable);
            }
        }
    }

    @Override
    public void addListener(AsyncListener listener) {
        addListener(listener, servletRequest, servletResponse);
    }

    @Override
    public void addListener(AsyncListener listener, ServletRequest servletRequest, ServletResponse servletResponse) {
        if (asyncListenerWrapperList == null) {
            asyncListenerWrapperList = new ArrayList<>(3);
        }
        asyncListenerWrapperList.add(new ServletAsyncListenerWrapper(listener, servletRequest, servletResponse));
    }

    @Override
    public <T extends AsyncListener> T createListener(Class<T> clazz) throws ServletException {
        try {
            return clazz.newInstance();
        } catch (InstantiationException | IllegalAccessException e) {
            throw new ServletException("asyncContext createListener error=" + e, e);
        }
    }

    @Override
    public long getTimeout() {
        return timeout;
    }

    @Override
    public void setTimeout(long timeout) {
        if (isComplete()) {
            return;
        }
        this.timeout = timeout;
        if (timeout <= 0 && timeoutTaskId != null) {
            TIMEOUT_TASK_MAP.remove(timeoutTaskId);
            return;
        }

        if (startTimestamp != 0) {
            if (timeoutTaskId == null) {
                timeoutTaskId = TASK_ID_INCR.getAndIncrement();
            } else {
                TIMEOUT_TASK_MAP.remove(timeoutTaskId);
            }
            long startDiff = System.currentTimeMillis() - startTimestamp;
            long expiryTime = timeout - startDiff;
            if (expiryTime > 0) {
                TIMEOUT_TASK_MAP.put(timeoutTaskId, this, expiryTime);
            } else {
                timeoutTask.run();
            }
        }
    }

    public boolean isStarted() {
        return status.get() >= STATUS_START;
    }

    public boolean isComplete() {
        return status.get() == STATUS_COMPLETE;
    }

    public ServletHttpExchange getExchange() {
        return servletHttpExchange;
    }

    public boolean isChannelActive() {
        return servletHttpExchange != null && servletHttpExchange.isChannelActive();
    }

    private static class ServletAsyncListenerWrapper {
        AsyncListener asyncListener;
        ServletRequest servletRequest;
        ServletResponse servletResponse;

        ServletAsyncListenerWrapper(AsyncListener asyncListener, ServletRequest servletRequest, ServletResponse servletResponse) {
            this.asyncListener = asyncListener;
            this.servletRequest = servletRequest;
            this.servletResponse = servletResponse;
        }
    }

}
