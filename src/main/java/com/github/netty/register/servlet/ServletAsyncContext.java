package com.github.netty.register.servlet;

import com.github.netty.core.util.Recyclable;
import com.github.netty.core.util.RecyclableUtil;

import javax.servlet.*;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
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
 * 异步处理的上下文
 * @author acer01
 *  2018/7/15/015
 */
public class ServletAsyncContext implements AsyncContext,Recyclable {

    private HttpServletRequest httpServletRequest;
    private HttpServletResponse httpServletResponse;
    private ExecutorService executorService;

    /**
     * 是否已经回收
     */
    private AtomicBoolean recycleFlag = new AtomicBoolean(false);
    /**
     * IO线程是否执行结束
     */
    private AtomicBoolean ioThreadExecuteOverFlag = new AtomicBoolean(false);

    /**
     * 0=初始, 1=开始, 2=完成
      */
    private AtomicInteger status;
    private static final int STATUS_INIT = 0;
    private static final int STATUS_START = 1;
    private static final int STATUS_COMPLETE = 2;

    /**
     * 超时时间 -> 毫秒
     */
    private long timeout;

    private List<ServletAsyncListenerWrapper> asyncListenerWrapperList;

    private ServletContext servletContext;
    private ServletHttpObject httpServletObject;
    private Throwable throwable;
    private Runnable task;

    public ServletAsyncContext(ServletHttpObject httpServletObject, ServletContext servletContext, ExecutorService executorService, ServletRequest httpServletRequest, ServletResponse httpServletResponse) {
        this.httpServletObject = Objects.requireNonNull(httpServletObject);
        this.servletContext = Objects.requireNonNull(servletContext);
        this.executorService = Objects.requireNonNull(executorService);
        this.httpServletRequest = (HttpServletRequest)Objects.requireNonNull(httpServletRequest);
        this.httpServletResponse = (HttpServletResponse)Objects.requireNonNull(httpServletResponse);
        this.status = new AtomicInteger(STATUS_INIT);
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
            //通知结束
            notifyEvent(listenerWrapper -> {
                try {
                    AsyncEvent event = new AsyncEvent(ServletAsyncContext.this,listenerWrapper.servletRequest,listenerWrapper.servletResponse, getThrowable());
                    listenerWrapper.asyncListener.onComplete(event);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            });

            //如果handler已经结束, 则自己进行回收
            if(ioThreadExecuteOverFlag.get()) {
                recycle();
            }
        }
    }

    /**
     * 标记主线程结束
     */
    public void markIoThreadOverFlag() {
        this.ioThreadExecuteOverFlag.compareAndSet(false,true);
    }

    @Override
    public void recycle(){
        //如果未回收, 则进行回收
        if(recycleFlag.compareAndSet(false,true)){
            httpServletObject.recycle();
        }
    }

    @Override
    public void start(Runnable runnable) {
        if(status.compareAndSet(STATUS_INIT,STATUS_START)){
            task = newTaskWrapper(runnable);
            executorService.execute(task);
        }
    }

    private Runnable newTaskWrapper(Runnable run){
        return () -> {
            Future future = executorService.submit(run);
            try {
                //通知开始
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
                //通知超时
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

                //通知异常
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
            asyncListenerWrapperList = RecyclableUtil.newRecyclableList(6);
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
            throw new IllegalStateException("请求不能为空");
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

        private ServletAsyncListenerWrapper(AsyncListener asyncListener, ServletRequest servletRequest, ServletResponse servletResponse) {
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
