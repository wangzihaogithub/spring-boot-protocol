package com.github.netty.protocol.servlet;

import com.github.netty.core.util.ExpiryLRUMap;
import com.github.netty.core.util.LoggerFactoryX;
import com.github.netty.core.util.LoggerX;
import com.github.netty.core.util.Recyclable;

import javax.servlet.*;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Context for asynchronous processing
 * @author wangzihao
 *  2018/7/15/015
 */
public class ServletAsyncContext implements AsyncContext, Recyclable {
    private static final LoggerX logger = LoggerFactoryX.getLogger(ServletAsyncContext.class);
    private static final int STATUS_INIT = 0;
    private static final int STATUS_START = 1;
    private static final int STATUS_RUNNING = 2;
    private static final int STATUS_COMPLETE = 3;

    /**
     * Has it been recycled
     */
    private AtomicBoolean recycleFlag = new AtomicBoolean(false);
    /**
     * Whether the IO thread has finished executing
     */
    private AtomicBoolean ioThreadExecuteOverFlag = new AtomicBoolean(false);
    /**
     * 0=init, 1=start, 2=complete
      */
    private AtomicInteger status = new AtomicInteger(STATUS_INIT);;
    /**
     * Timeout time -> ms
     */
    private long timeout;
    private List<ServletAsyncListenerWrapper> asyncListenerWrapperList;
    private ServletContext servletContext;
    private ServletHttpExchange servletHttpExchange;
    private Throwable throwable;
    private HttpServletRequest httpServletRequest;
    private HttpServletResponse httpServletResponse;
    private ExecutorService executorService;

    public ServletAsyncContext(ServletHttpExchange servletHttpExchange, ServletContext servletContext, ExecutorService executorService, ServletRequest httpServletRequest, ServletResponse httpServletResponse) {
        this.servletHttpExchange = Objects.requireNonNull(servletHttpExchange);
        this.servletContext = Objects.requireNonNull(servletContext);
        this.executorService = Objects.requireNonNull(executorService);
        this.httpServletRequest = (HttpServletRequest)Objects.requireNonNull(httpServletRequest);
        this.httpServletResponse = (HttpServletResponse)Objects.requireNonNull(httpServletResponse);
    }

    public Throwable getThrowable() {
        return throwable;
    }

    public ServletContext getServletContext() {
        return servletContext;
    }

    public void setThrowable(Throwable throwable) {
        this.throwable = throwable;
    }

    @Override
    public ServletRequest getRequest() {
        return httpServletRequest;
    }

    @Override
    public ServletResponse getResponse() {
        return httpServletResponse;
    }

    @Override
    public boolean hasOriginalRequestAndResponse() {
        return true;
    }

    @Override
    public void dispatch() {
        if(httpServletRequest == null){
            return;
        }

        String path = httpServletRequest.getRequestURI();
        String contextPath = httpServletRequest.getContextPath();
        if (contextPath.length() > 1) {
            path = path.substring(contextPath.length());
        }
        dispatch(path);
    }

    @Override
    public void dispatch(String path) {
        dispatch(servletContext, path);
    }

    @Override
    public void dispatch(javax.servlet.ServletContext context, String path) {
        check();
        this.servletContext = (ServletContext) context;
        ServletRequestDispatcher dispatcher = servletContext.getRequestDispatcher(path);

        Runnable runnable = dispatcher.dispatchAsync(httpServletRequest,httpServletResponse,this);
        start(runnable);
    }

    @Override
    public void complete() {
        status.set(STATUS_COMPLETE);
        //Notify the complete
        if(asyncListenerWrapperList != null) {
            for (ServletAsyncListenerWrapper listenerWrapper : asyncListenerWrapperList) {
                AsyncEvent event = new AsyncEvent(this,listenerWrapper.servletRequest,listenerWrapper.servletResponse,null);
                try {
                    listenerWrapper.asyncListener.onComplete(event);
                } catch (IOException e) {
                    logger.error("asyncContext notifyEvent.onComplete() error={}",e.toString(),e);
                }
            }
        }
        //If the handler has finished, recycle it yourself
        if(ioThreadExecuteOverFlag.get()) {
            recycle();
        }
    }

    /**
     * Marks the end of the main thread
     */
    public void markIoThreadOverFlag() {
        this.ioThreadExecuteOverFlag.compareAndSet(false,true);
    }

    @Override
    public void recycle(){
        //If not, recycle
        if(recycleFlag.compareAndSet(false,true)){
            servletHttpExchange.recycle();
        }
    }

    @Override
    public void start(Runnable runnable) {
        start();
        if(status.compareAndSet(STATUS_START,STATUS_RUNNING)){
            TaskWrapper wrapper = new TaskWrapper(runnable,this);
            if(servletContext.isAsyncSwitchThread()){
                executorService.execute(wrapper);
            }else {
                wrapper.run();
            }
        }
    }

    public void start(){
        status.compareAndSet(STATUS_INIT,STATUS_START);
    }

    private static class TaskWrapper implements Runnable{
        private static final AtomicInteger TASK_ID_INCR = new AtomicInteger();
        private static final ExpiryLRUMap<Integer,TaskWrapper> TIMEOUT_TASK_MAP = new ExpiryLRUMap<>(256,Long.MAX_VALUE,Long.MAX_VALUE,null);
        static {
            TIMEOUT_TASK_MAP.setOnExpiryConsumer(node -> {
                //Notice the timeout
                TaskWrapper taskWrapper = node.getData();
                ServletAsyncContext asyncContext = taskWrapper.asyncContext;
                if(taskWrapper.eventFlag.compareAndSet(false,true)){
                    if(asyncContext.asyncListenerWrapperList == null) {
                        return;
                    }
                    asyncContext.executorService.execute(()->{
                        for (ServletAsyncListenerWrapper listenerWrapper : asyncContext.asyncListenerWrapperList) {
                            try {
                                AsyncEvent event = new AsyncEvent(asyncContext, listenerWrapper.servletRequest, listenerWrapper.servletResponse, null);
                                listenerWrapper.asyncListener.onTimeout(event);
                            } catch (Exception ex) {
                                logger.error("asyncContext notifyEvent.onTimeout() error={}", ex.toString(), ex);
                            }
                        }
                    });
                }
            });
        }
        private final Runnable runnable;
        private final ServletAsyncContext asyncContext;
        private final AtomicBoolean eventFlag = new AtomicBoolean(false);
        public TaskWrapper(Runnable runnable, ServletAsyncContext asyncContext) {
            this.runnable = runnable;
            this.asyncContext = asyncContext;
        }
        @Override
        public void run() {
            int taskId = TASK_ID_INCR.getAndIncrement();
            TIMEOUT_TASK_MAP.put(taskId,this,asyncContext.getTimeout());
            try {
                //Notify the start
                if(asyncContext.asyncListenerWrapperList != null) {
                    for (ServletAsyncListenerWrapper listenerWrapper : asyncContext.asyncListenerWrapperList) {
                        AsyncEvent event = new AsyncEvent(asyncContext,listenerWrapper.servletRequest,listenerWrapper.servletResponse,null);
                        try {
                            listenerWrapper.asyncListener.onStartAsync(event);
                        } catch (IOException e) {
                            logger.error("asyncContext notifyEvent.onStartAsync() error={}",e.toString(),e);
                        }
                    }
                }
                //running
                runnable.run();
                if(eventFlag.compareAndSet(false,true)){
                    TIMEOUT_TASK_MAP.remove(taskId);
                    //Notify the complete
                    if(asyncContext.asyncListenerWrapperList != null) {
                        for (ServletAsyncListenerWrapper listenerWrapper : asyncContext.asyncListenerWrapperList) {
                            AsyncEvent event = new AsyncEvent(asyncContext,listenerWrapper.servletRequest,listenerWrapper.servletResponse, null);
                            try {
                                listenerWrapper.asyncListener.onComplete(event);
                            } catch (Exception e) {
                                logger.error("asyncContext notifyEvent.onComplete() error={}",e.toString(),e);
                            }
                        }
                    }
                }
            }catch (Throwable throwable){
                if(throwable instanceof AsyncRuntimeException){
                    throwable = throwable.getCause();
                }
                asyncContext.setThrowable(throwable);
                //Notify the throwable
                if(asyncContext.asyncListenerWrapperList != null) {
                    for (ServletAsyncListenerWrapper listenerWrapper : asyncContext.asyncListenerWrapperList) {
                        AsyncEvent event = new AsyncEvent(asyncContext,listenerWrapper.servletRequest,listenerWrapper.servletResponse, throwable);
                        try {
                            listenerWrapper.asyncListener.onError(event);
                        } catch (Exception e) {
                            logger.error("asyncContext notifyEvent.onError() error={}",e.toString(),e);
                        }
                    }
                }
            }finally {
                asyncContext.recycle();
            }
        }
    }

    @Override
    public void addListener(AsyncListener listener) {
        addListener(listener, httpServletRequest, httpServletResponse);
    }

    @Override
    public void addListener(AsyncListener listener, ServletRequest servletRequest, ServletResponse servletResponse) {
        if(asyncListenerWrapperList == null){
            asyncListenerWrapperList = new ArrayList<>(6);
        }
        asyncListenerWrapperList.add(new ServletAsyncListenerWrapper(listener,servletRequest,servletResponse));
    }

    @Override
    public <T extends AsyncListener> T createListener(Class<T> clazz) throws ServletException {
        try {
            return clazz.newInstance();
        } catch (InstantiationException | IllegalAccessException e) {
            throw new ServletException("asyncContext createListener error="+e,e);
        }
    }

    @Override
    public void setTimeout(long timeout) {
        this.timeout = timeout;
    }

    @Override
    public long getTimeout() {
        return timeout;
    }

    private void check() {
        if (httpServletRequest == null) {
            // AsyncContext has been recycled and should not be being used
            throw new IllegalStateException("The request cannot be null");
        }
    }

    public boolean isStarted(){
        return status.get() > STATUS_INIT;
    }

    public boolean isComplete(){
        return status.get() == STATUS_COMPLETE;
    }


    private class ServletAsyncListenerWrapper{
        AsyncListener asyncListener;
        ServletRequest servletRequest;
        ServletResponse servletResponse;
        ServletAsyncListenerWrapper(AsyncListener asyncListener, ServletRequest servletRequest, ServletResponse servletResponse) {
            this.asyncListener = asyncListener;
            this.servletRequest = servletRequest;
            this.servletResponse = servletResponse;
        }
    }

    public static class AsyncRuntimeException extends RuntimeException{
        private Throwable cause;
        AsyncRuntimeException(Throwable cause) {
            super(cause.getMessage(),cause,true,false);
            this.cause = cause;
        }
        @Override
        public Throwable getCause() {
            return cause;
        }
    }
}
