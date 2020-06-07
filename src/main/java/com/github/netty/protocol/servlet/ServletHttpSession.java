package com.github.netty.protocol.servlet;

import com.github.netty.core.util.LoggerFactoryX;
import com.github.netty.core.util.LoggerX;
import com.github.netty.core.util.Wrapper;

import javax.servlet.http.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * The servlet session
 * @author wangzihao
 *  2018/7/15/015
 */
public class ServletHttpSession implements HttpSession, Wrapper<Session> {
    private static final LoggerX logger = LoggerFactoryX.getLogger(ServletHttpSession.class);
    private ServletContext servletContext;
    private String id;

    private Map<String,Object> attributeMap;
    private long creationTime;
    private long currAccessedTime;
    private long lastAccessedTime;
    //Unit seconds
    private int maxInactiveInterval;
    private boolean newSessionFlag;
    private AtomicInteger accessCount;

    private final List<HttpSessionBindingListener> httpSessionBindingListenerList = new ArrayList<>();

    private Session source;
    /**
     * The servlet principal
     */
    private ServletPrincipal principal;

    ServletHttpSession() {
    }

    ServletHttpSession(ServletContext servletContext) {
        this.servletContext = servletContext;
    }

    public ServletPrincipal getPrincipal() {
        return principal;
    }

    public void setPrincipal(ServletPrincipal principal) {
        this.principal = principal;
    }

    public void setServletContext(ServletContext servletContext) {
        this.servletContext = servletContext;
    }

    public void setId(String id) {
        this.id = id;
    }

    private Map<String, Object> getAttributeMap() {
        if(attributeMap == null){
            attributeMap = new ConcurrentHashMap<>(16);
        }
        return attributeMap;
    }

    public void save(){
	    if(id == null){
		    return;
	    }
        getServletContext().getSessionService().saveSession(unwrap());
	    getServletContext().log("saveHttpSession : sessionId="+id);
    }

    public void remove(){
    	if(id == null){
    		return;
	    }
        getServletContext().getSessionService().removeSession(getId());
	    getServletContext().log("removeHttpSession : sessionId="+id);
    }

    @Override
    public long getCreationTime() {
        return creationTime;
    }

    @Override
    public String getId() {
        return id;
    }

    @Override
    public long getLastAccessedTime() {
        return lastAccessedTime;
    }

    @Override
    public ServletContext getServletContext() {
        return servletContext;
    }

    @Override
    public void setMaxInactiveInterval(int interval) {
        maxInactiveInterval = interval;
    }

    @Override
    public int getMaxInactiveInterval() {
        return maxInactiveInterval;
    }

    @Override
    public HttpSessionContext getSessionContext() {
        return null;
    }

    @Override
    public Object getAttribute(String name) {
        Object value = getAttributeMap().get(name);
        return value;
    }

    @Override
    public Object getValue(String name) {
        return getAttribute(name);
    }

    @Override
    public Enumeration<String> getAttributeNames() {
        return Collections.enumeration(getAttributeMap().keySet());
    }

    @Override
    public String[] getValueNames() {
        return getAttributeMap().keySet().toArray(new String[getAttributeMap().size()]);
    }

    @Override
    public void setAttribute(String name, Object value) {
        Objects.requireNonNull(name);

        if(value == null){
            removeValue(name);
            return;
        }

        Object oldValue = getAttributeMap().put(name,value);

        if(value instanceof HttpSessionBindingListener){
            httpSessionBindingListenerList.add((HttpSessionBindingListener) value);
        }

        ServletEventListenerManager listenerManager = servletContext.getServletEventListenerManager();
        if(listenerManager.hasHttpSessionAttributeListener()){
            listenerManager.onHttpSessionAttributeAdded(new HttpSessionBindingEvent(this,name,value));
            if(oldValue != null){
                listenerManager.onHttpSessionAttributeReplaced(new HttpSessionBindingEvent(this,name,oldValue));
            }
        }

        HttpSessionBindingEvent valueBoundEvent = new HttpSessionBindingEvent(this,name,value);
        for(HttpSessionBindingListener listener : httpSessionBindingListenerList){
            try {
                listener.valueBound(valueBoundEvent);
            }catch (Throwable throwable){
                logger.warn("listener.valueBound error ={},listener={},valueBoundEvent={}",throwable.toString(),listener,valueBoundEvent);
            }
        }

        if(oldValue != null){
            HttpSessionBindingEvent valueUnboundEvent = new HttpSessionBindingEvent(this,name,oldValue);
            for(HttpSessionBindingListener listener : httpSessionBindingListenerList){
                try {
                    listener.valueUnbound(valueUnboundEvent);
                }catch (Throwable throwable){
                    logger.warn("listener.valueBound error ={},listener={},valueBoundEvent={}",throwable.toString(),listener,valueBoundEvent);
                }
            }
        }
    }

    @Override
    public void putValue(String name, Object value) {
        setAttribute(name,value);
    }

    @Override
    public void removeAttribute(String name) {
        Object oldValue = getAttributeMap().remove(name);

        if(oldValue instanceof HttpSessionBindingListener){
            httpSessionBindingListenerList.remove(oldValue);
        }
        onRemoveAttribute(name,oldValue);
    }

    private void onRemoveAttribute(String name,Object oldValue){
        ServletEventListenerManager listenerManager = servletContext.getServletEventListenerManager();
        if(listenerManager.hasHttpSessionAttributeListener()){
            listenerManager.onHttpSessionAttributeRemoved(new HttpSessionBindingEvent(this,name,oldValue));
        }

        HttpSessionBindingEvent valueUnboundEvent = new HttpSessionBindingEvent(this,name,oldValue);
        for(HttpSessionBindingListener listener : httpSessionBindingListenerList){
            listener.valueUnbound(valueUnboundEvent);
        }
    }

    @Override
    public void removeValue(String name) {
        removeAttribute(name);
    }

    @Override
    public void invalidate() {
        if(servletContext != null) {
            ServletEventListenerManager listenerManager = servletContext.getServletEventListenerManager();
            if (listenerManager.hasHttpSessionListener()) {
                listenerManager.onHttpSessionDestroyed(new HttpSessionEvent(this));
            }
            servletContext.getSessionService().removeSession(id);
        }

        if(attributeMap != null) {
            for (String key : attributeMap.keySet()) {
                Object oldValue = attributeMap.remove(key);
                if(!(oldValue instanceof HttpSessionBindingListener)){
                    onRemoveAttribute(key,oldValue);
                }
            }
            httpSessionBindingListenerList.clear();
            attributeMap.clear();
            attributeMap = null;
        }
        servletContext = null;
        maxInactiveInterval = -1;
    }

    @Override
    public boolean isNew() {
        return newSessionFlag;
    }

    /**
     * The validity of
     * @return True is valid, false is not
     */
    public boolean isValid() {
        return id != null && System.currentTimeMillis() < (creationTime + (maxInactiveInterval * 1000));
    }

    public void setNewSessionFlag(boolean newSessionFlag) {
        this.newSessionFlag = newSessionFlag;

        if(newSessionFlag){
            ServletEventListenerManager listenerManager = servletContext.getServletEventListenerManager();
            if(listenerManager.hasHttpSessionListener()){
                listenerManager.onHttpSessionCreated(new HttpSessionEvent(this));
            }
        }
    }

    public ServletHttpSession access(){
        currAccessedTime = System.currentTimeMillis();
        lastAccessedTime = currAccessedTime;
        accessCount.incrementAndGet();
        return this;
    }

    @Override
    public void wrap(Session source) {
        this.source = source;

        this.id = source.getId();
        this.attributeMap = source.getAttributeMap();
        this.creationTime = source.getCreationTime();
        this.lastAccessedTime = source.getLastAccessedTime();
        //Unit seconds
        this.maxInactiveInterval = source.getMaxInactiveInterval();
        this.accessCount = new AtomicInteger(source.getAccessCount());

        if(attributeMap != null) {
            for (Object value : attributeMap.values()) {
                if(!(value instanceof HttpSessionBindingListener)){
                    continue;
                }
                httpSessionBindingListenerList.add((HttpSessionBindingListener) value);
            }
        }
    }

    @Override
    public Session unwrap() {
        source.setId(id);
        source.setCreationTime(creationTime);
        source.setLastAccessedTime(lastAccessedTime);

        source.setAccessCount(accessCount.get());
        source.setMaxInactiveInterval(maxInactiveInterval);
        source.setAttributeMap(attributeMap);
        return source;
    }

    public void clear() {
        this.id = null;
        this.attributeMap = null;
        this.creationTime = 0;
        this.lastAccessedTime = 0;
        this.maxInactiveInterval = 0;
        this.accessCount = null;
        this.source = null;
        this.principal = null;
    }

    @Override
    public String toString() {
        return "ServletHttpSession[" + id + ']';
    }

}
