package com.github.netty.register.servlet;

import javax.servlet.*;
import javax.servlet.http.*;
import java.util.LinkedList;
import java.util.List;
import java.util.function.Function;

/**
 * servlet全局事件监听
 *
 * @author acer01
 *  2018/7/29/029
 */
public class ServletEventListenerManager {

    private final Object lock = new Object();

    private List<ServletContextAttributeListener> servletContextAttributeListenerList;
    private List<ServletRequestListener> servletRequestListenerList;
    private List<ServletRequestAttributeListener> servletRequestAttributeListenerList;
    private List<HttpSessionIdListener> httpSessionIdListenerList;
    private List<HttpSessionAttributeListener> httpSessionAttributeListenerList;

    private List<HttpSessionListener> httpSessionListenerList;
    private List<ServletContextListener> servletContextListenerList;
    private Function<Servlet,Servlet> servletAddedListener;

    //=============event=================

    public Servlet onServletAdded(Servlet servlet){
        if(servletAddedListener == null){
            return servlet;
        }
        try {
            return servletAddedListener.apply(servlet);
        }catch (Throwable throwable){
            throwable.printStackTrace();
            return servlet;
        }
    }

    public void onServletContextAttributeAdded(ServletContextAttributeEvent event){
        if(servletContextAttributeListenerList == null){
            return;
        }
        for(ServletContextAttributeListener listener : servletContextAttributeListenerList){
            try {
                listener.attributeAdded(event);
            }catch (Throwable throwable){
                throwable.printStackTrace();
            }
        }
    }

    public void onServletContextAttributeRemoved(ServletContextAttributeEvent event){
        if(servletContextAttributeListenerList == null){
            return;
        }
        for(ServletContextAttributeListener listener : servletContextAttributeListenerList){
            try {
                listener.attributeRemoved(event);
            }catch (Throwable throwable){
                throwable.printStackTrace();
            }
        }
    }

    public void onServletContextAttributeReplaced(ServletContextAttributeEvent event){
        if(servletContextAttributeListenerList == null){
            return;
        }
        for(ServletContextAttributeListener listener : servletContextAttributeListenerList){
            try {
                listener.attributeReplaced(event);
            }catch (Throwable throwable){
                throwable.printStackTrace();
            }
        }
    }

    public void onServletRequestInitialized(ServletRequestEvent event){
        if(servletRequestListenerList == null){
            return;
        }
        for(ServletRequestListener listener : servletRequestListenerList){
            try {
                listener.requestInitialized(event);
            }catch (Throwable throwable){
                throwable.printStackTrace();
            }
        }
    }

    public void onServletRequestDestroyed(ServletRequestEvent event){
        if(servletRequestListenerList == null){
            return;
        }
        for(ServletRequestListener listener : servletRequestListenerList){
            try {
                listener.requestDestroyed(event);
            }catch (Throwable throwable){
                throwable.printStackTrace();
            }
        }
    }

    public void onServletRequestAttributeAdded(ServletRequestAttributeEvent event){
        if(servletRequestAttributeListenerList == null){
            return;
        }
        for(ServletRequestAttributeListener listener : servletRequestAttributeListenerList){
            try {
                listener.attributeAdded(event);
            }catch (Throwable throwable){
                throwable.printStackTrace();
            }
        }
    }

    public void onServletRequestAttributeRemoved(ServletRequestAttributeEvent event){
        if(servletRequestAttributeListenerList == null){
            return;
        }
        for(ServletRequestAttributeListener listener : servletRequestAttributeListenerList){
            try {
                listener.attributeRemoved(event);
            }catch (Throwable throwable){
                throwable.printStackTrace();
            }
        }
    }

    public void onServletRequestAttributeReplaced(ServletRequestAttributeEvent event){
        if(servletRequestAttributeListenerList == null){
            return;
        }
        for(ServletRequestAttributeListener listener : servletRequestAttributeListenerList){
            try {
                listener.attributeReplaced(event);
            }catch (Throwable throwable){
                throwable.printStackTrace();
            }
        }
    }

    public void onHttpSessionIdChanged(HttpSessionEvent event, String oldSessionId){
        if(httpSessionIdListenerList == null){
            return;
        }
        for(HttpSessionIdListener listener : httpSessionIdListenerList){
            try {
                listener.sessionIdChanged(event,oldSessionId);
            }catch (Throwable throwable){
                throwable.printStackTrace();
            }
        }
    }

    public void onHttpSessionAttributeAdded(HttpSessionBindingEvent event){
        if(httpSessionAttributeListenerList == null){
            return;
        }
        for(HttpSessionAttributeListener listener : httpSessionAttributeListenerList){
            try {
                listener.attributeAdded(event);
            }catch (Throwable throwable){
                throwable.printStackTrace();
            }
        }
    }

    public void onHttpSessionAttributeRemoved(HttpSessionBindingEvent event){
        if(httpSessionAttributeListenerList == null){
            return;
        }
        for(HttpSessionAttributeListener listener : httpSessionAttributeListenerList){
            try {
                listener.attributeRemoved(event);
            }catch (Throwable throwable){
                throwable.printStackTrace();
            }
        }
    }

    public void onHttpSessionAttributeReplaced(HttpSessionBindingEvent event){
        if(httpSessionAttributeListenerList == null){
            return;
        }
        for(HttpSessionAttributeListener listener : httpSessionAttributeListenerList){
            try {
                listener.attributeReplaced(event);
            }catch (Throwable throwable){
                throwable.printStackTrace();
            }
        }
    }

    public void onHttpSessionCreated(HttpSessionEvent event){
        if(httpSessionListenerList == null){
            return;
        }
        for(HttpSessionListener listener : httpSessionListenerList){
            try {
                listener.sessionCreated(event);
            }catch (Throwable throwable){
                throwable.printStackTrace();
            }
        }
    }

    public void onHttpSessionDestroyed(HttpSessionEvent event){
        if(httpSessionListenerList == null){
            return;
        }
        for(HttpSessionListener listener : httpSessionListenerList){
            try {
                listener.sessionDestroyed(event);
            }catch (Throwable throwable){
                throwable.printStackTrace();
            }
        }
    }

    public void onServletContextInitialized(ServletContextEvent event){
        if(servletContextListenerList == null){
            return;
        }
        for(ServletContextListener listener : servletContextListenerList){
            try {
                listener.contextInitialized(event);
            }catch (Throwable throwable){
                throwable.printStackTrace();
            }
        }
    }

    public void onServletContextDestroyed(ServletContextEvent event){
        if(servletContextListenerList == null){
            return;
        }
        for(ServletContextListener listener : servletContextListenerList){
            try {
                listener.contextDestroyed(event);
            }catch (Throwable throwable){
                throwable.printStackTrace();
            }
        }
    }


    //==============has===========

    public boolean hasServletContextAttributeListener(){
        return servletContextAttributeListenerList != null;
    }
    public boolean hasServletRequestListener(){
        return servletRequestListenerList != null;
    }
    public boolean hasServletRequestAttributeListener(){
        return servletRequestAttributeListenerList != null;
    }
    public boolean hasHttpSessionIdListener(){
        return httpSessionIdListenerList != null;
    }
    public boolean hasHttpSessionAttributeListener(){
        return httpSessionAttributeListenerList != null;
    }
    public boolean hasHttpSessionListener(){
        return httpSessionListenerList != null;
    }
    public boolean hasServletContextListener(){
        return servletContextListenerList != null;
    }
    public boolean hasServletAddedListener(){
        return servletAddedListener != null;
    }


    //==============add===========

    public void setServletAddedListener(Function<Servlet, Servlet> servletAddedListener) {
        this.servletAddedListener = servletAddedListener;
    }

    public void addServletContextAttributeListener(ServletContextAttributeListener listener){
        getServletContextAttributeListenerList().add(listener);
    }

    public void addServletRequestListener(ServletRequestListener listener){
        getServletRequestListenerList().add(listener);
    }

    public void addServletRequestAttributeListener(ServletRequestAttributeListener listener){
        getServletRequestAttributeListenerList().add(listener);
    }

    public void addHttpSessionIdListenerListener(HttpSessionIdListener listener){
        getHttpSessionIdListenerList().add(listener);
    }

    public void addHttpSessionAttributeListener(HttpSessionAttributeListener listener){
        getHttpSessionAttributeListenerList().add(listener);
    }

    public void addHttpSessionListener(HttpSessionListener listener){
        getHttpSessionListenerList().add(listener);
    }

    public void addServletContextListener(ServletContextListener listener){
        getServletContextListenerList().add(listener);
    }


    //==============get===========

    private List<ServletContextAttributeListener> getServletContextAttributeListenerList() {
        if(servletContextAttributeListenerList == null){
            synchronized (lock) {
                if(servletContextAttributeListenerList == null) {
                    servletContextAttributeListenerList = newListenerList();
                }
            }
        }
        return servletContextAttributeListenerList;
    }


    private List<ServletRequestListener> getServletRequestListenerList() {
        if(servletRequestListenerList == null){
            synchronized (lock) {
                if(servletRequestListenerList == null) {
                    servletRequestListenerList = newListenerList();
                }
            }
        }
        return servletRequestListenerList;
    }

    private List<ServletRequestAttributeListener> getServletRequestAttributeListenerList() {
        if(servletRequestAttributeListenerList == null){
            synchronized (lock) {
                if(servletRequestAttributeListenerList == null) {
                    servletRequestAttributeListenerList = newListenerList();
                }
            }
        }
        return servletRequestAttributeListenerList;
    }

    private List<HttpSessionIdListener> getHttpSessionIdListenerList() {
        if(httpSessionIdListenerList == null){
            synchronized (lock) {
                if(httpSessionIdListenerList == null) {
                    httpSessionIdListenerList = newListenerList();
                }
            }
        }
        return httpSessionIdListenerList;
    }

    private List<HttpSessionAttributeListener> getHttpSessionAttributeListenerList() {
        if(httpSessionAttributeListenerList == null){
            synchronized (lock) {
                if(httpSessionAttributeListenerList == null) {
                    httpSessionAttributeListenerList = newListenerList();
                }
            }
        }
        return httpSessionAttributeListenerList;
    }

    private List<HttpSessionListener> getHttpSessionListenerList() {
        if(httpSessionListenerList == null){
            synchronized (lock) {
                if(httpSessionListenerList == null) {
                    httpSessionListenerList = newListenerList();
                }
            }
        }
        return httpSessionListenerList;
    }

    private List<ServletContextListener> getServletContextListenerList() {
        if(servletContextListenerList == null){
            synchronized (lock) {
                if(servletContextListenerList == null) {
                    servletContextListenerList = newListenerList();
                }
            }
        }
        return servletContextListenerList;
    }

    private <T>List<T> newListenerList(){
        return new LinkedList<T>();
    }
}
