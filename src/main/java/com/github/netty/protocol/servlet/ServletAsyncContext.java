package com.github.netty.protocol.servlet;

import com.github.netty.core.util.Recyclable;

import javax.servlet.*;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

/**
 * Context for asynchronous processing
 * @author wangzihao
 *  2018/7/15/015
 */
public class ServletAsyncContext implements AsyncContext,Recyclable {
    private static final int STATUS_INIT = 0;
    private static final int STATUS_START = 1;
    private static final int STATUS_COMPLETE = 2;

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
    private ServletHttpObject httpServletObject;
    private Throwable throwable;
    private HttpServletRequest httpServletRequest;
    private HttpServletResponse httpServletResponse;
    private ExecutorService executorService;

    public ServletAsyncContext(ServletHttpObject httpServletObject, ServletContext servletContext, ExecutorService executorService, ServletRequest httpServletRequest, ServletResponse httpServletResponse) {
        this.httpServletObject = Objects.requireNonNull(httpServletObject);
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
        if(status.compareAndSet(STATUS_START,STATUS_COMPLETE)){
            //Notify the end
            notifyEvent(listenerWrapper -> {
                try {
                    AsyncEvent event = new AsyncEvent(ServletAsyncContext.this,listenerWrapper.servletRequest,listenerWrapper.servletResponse, getThrowable());
                    listenerWrapper.asyncListener.onComplete(event);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            });

            //If the handler has finished, recycle it yourself
            if(ioThreadExecuteOverFlag.get()) {
                recycle();
            }
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
            httpServletObject.recycle();
        }
    }

    @Override
    public void start(Runnable runnable) {
        if(status.compareAndSet(STATUS_INIT,STATUS_START)){
            Runnable task = newTaskWrapper(runnable);
            executorService.execute(task);
        }
    }

    private Runnable newTaskWrapper(Runnable run){
        return () -> {
            Future future = executorService.submit(run);
            try {
                //Notify the start
                notifyEvent(listenerWrapper -> {
                    AsyncEvent event = new AsyncEvent(ServletAsyncContext.this,listenerWrapper.servletRequest,listenerWrapper.servletResponse,null);
                    try {
                        listenerWrapper.asyncListener.onStartAsync(event);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                });

                future.get(getTimeout(), TimeUnit.MILLISECONDS);

            } catch (TimeoutException e) {
                //Notice the timeout
                notifyEvent(listenerWrapper -> {
                    try {
                        AsyncEvent event = new AsyncEvent(ServletAsyncContext.this,listenerWrapper.servletRequest,listenerWrapper.servletResponse,null);
                        listenerWrapper.asyncListener.onTimeout(event);
                    } catch (Exception ex) {
                        ex.printStackTrace();
                    }
                });
            }catch (Throwable throwable){
                if(throwable instanceof AsyncRuntimeException){
                    throwable = throwable.getCause();
                }
                setThrowable(throwable);

                //Notify the throwable
                notifyEvent(listenerWrapper -> {
                    AsyncEvent event = new AsyncEvent(ServletAsyncContext.this,listenerWrapper.servletRequest,listenerWrapper.servletResponse, getThrowable());
                    try {
                        listenerWrapper.asyncListener.onError(event);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                });
            }
        };
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
            e.printStackTrace();
        }
        return null;
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

    private void notifyEvent(Consumer<ServletAsyncListenerWrapper> consumer){
        if(asyncListenerWrapperList != null) {
            for (ServletAsyncListenerWrapper listenerWrapper : asyncListenerWrapperList){
                consumer.accept(listenerWrapper);
            }
        }
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
