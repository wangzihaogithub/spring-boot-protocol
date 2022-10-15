package com.github.netty.protocol.servlet;

import com.github.netty.core.util.LoggerFactoryX;
import com.github.netty.core.util.LoggerX;
import com.github.netty.core.util.Wrapper;

import javax.servlet.http.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The servlet session
 *
 * @author wangzihao
 * 2018/7/15/015
 */
public class ServletHttpSession implements HttpSession, Wrapper<Session> {
    private static final LoggerX logger = LoggerFactoryX.getLogger(ServletHttpSession.class);
    private final List<HttpSessionBindingListener> httpSessionBindingListenerList = new ArrayList<>(2);
    private final ServletContext servletContext;
    private String id;
    private Map<String, Object> attributeMap;
    private long creationTime;
    private long currAccessedTime;
    private long lastAccessedTime;
    //Unit seconds
    private int maxInactiveInterval;
    private int accessCount;
    private Session source;

    ServletHttpSession(Session session, ServletContext servletContext) {
        this.servletContext = servletContext;
        this.wrap(session);
    }

    private Map<String, Object> getAttributeMap() {
        if (attributeMap == null) {
            attributeMap = new ConcurrentHashMap<>(6);
        }
        return attributeMap;
    }

    public void save() {
        getServletContext().getSessionService().saveSession(unwrap());
    }

    @Override
    public long getCreationTime() {
        return creationTime;
    }

    @Override
    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
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
    public int getMaxInactiveInterval() {
        return maxInactiveInterval;
    }

    @Override
    public void setMaxInactiveInterval(int interval) {
        maxInactiveInterval = interval;
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

        if (value == null) {
            removeValue(name);
            return;
        }

        Object oldValue = getAttributeMap().put(name, value);

        if (value instanceof HttpSessionBindingListener) {
            httpSessionBindingListenerList.add((HttpSessionBindingListener) value);
        }

        ServletEventListenerManager listenerManager = servletContext.getServletEventListenerManager();
        if (listenerManager.hasHttpSessionAttributeListener()) {
            listenerManager.onHttpSessionAttributeAdded(new HttpSessionBindingEvent(this, name, value));
            if (oldValue != null) {
                listenerManager.onHttpSessionAttributeReplaced(new HttpSessionBindingEvent(this, name, oldValue));
            }
        }

        HttpSessionBindingEvent valueBoundEvent = new HttpSessionBindingEvent(this, name, value);
        for (HttpSessionBindingListener listener : httpSessionBindingListenerList) {
            try {
                listener.valueBound(valueBoundEvent);
            } catch (Throwable throwable) {
                logger.warn("listener.valueBound error ={},listener={},valueBoundEvent={}", throwable.toString(), listener, valueBoundEvent);
            }
        }

        if (oldValue != null) {
            HttpSessionBindingEvent valueUnboundEvent = new HttpSessionBindingEvent(this, name, oldValue);
            for (HttpSessionBindingListener listener : httpSessionBindingListenerList) {
                try {
                    listener.valueUnbound(valueUnboundEvent);
                } catch (Throwable throwable) {
                    logger.warn("listener.valueBound error ={},listener={},valueBoundEvent={}", throwable.toString(), listener, valueBoundEvent);
                }
            }
        }
    }

    @Override
    public void putValue(String name, Object value) {
        setAttribute(name, value);
    }

    @Override
    public void removeAttribute(String name) {
        Object oldValue = getAttributeMap().remove(name);

        if (oldValue instanceof HttpSessionBindingListener) {
            httpSessionBindingListenerList.remove(oldValue);
        }
        onRemoveAttribute(name, oldValue);
    }

    private void onRemoveAttribute(String name, Object oldValue) {
        ServletEventListenerManager listenerManager = servletContext.getServletEventListenerManager();
        if (listenerManager.hasHttpSessionAttributeListener()) {
            listenerManager.onHttpSessionAttributeRemoved(new HttpSessionBindingEvent(this, name, oldValue));
        }

        if (!httpSessionBindingListenerList.isEmpty()) {
            HttpSessionBindingEvent valueUnboundEvent = new HttpSessionBindingEvent(this, name, oldValue);
            for (HttpSessionBindingListener listener : httpSessionBindingListenerList) {
                listener.valueUnbound(valueUnboundEvent);
            }
        }
    }

    @Override
    public void removeValue(String name) {
        removeAttribute(name);
    }

    @Override
    public void invalidate() {
        invalidate0();
        servletContext.getSessionService().removeSession(id);
    }

    public void invalidate0() {
        ServletEventListenerManager listenerManager = servletContext.getServletEventListenerManager();
        if (listenerManager.hasHttpSessionListener()) {
            listenerManager.onHttpSessionDestroyed(new HttpSessionEvent(this));
        }

        if (attributeMap != null) {
            for (String key : attributeMap.keySet()) {
                Object oldValue = attributeMap.remove(key);
                if (!(oldValue instanceof HttpSessionBindingListener)) {
                    onRemoveAttribute(key, oldValue);
                }
            }
            httpSessionBindingListenerList.clear();
            attributeMap.clear();
            attributeMap = null;
        }
        maxInactiveInterval = -1;
    }

    public boolean hasListener() {
        if (!httpSessionBindingListenerList.isEmpty()) {
            return true;
        }
        ServletEventListenerManager listenerManager = servletContext.getServletEventListenerManager();
        if (listenerManager.hasHttpSessionListener()) {
            return true;
        }
        return listenerManager.hasHttpSessionAttributeListener();
    }

    @Override
    public boolean isNew() {
        return accessCount == 1;
    }

    /**
     * The validity of
     *
     * @return True is valid, false is not
     */
    public boolean isValid() {
        return id != null && System.currentTimeMillis() < (creationTime + (maxInactiveInterval * 1000));
    }

    public void access() {
        accessCount++;

        if (isNew()) {
            currAccessedTime = System.currentTimeMillis();
            lastAccessedTime = currAccessedTime;
            ServletEventListenerManager listenerManager = servletContext.getServletEventListenerManager();
            if (listenerManager.hasHttpSessionListener()) {
                listenerManager.onHttpSessionCreated(new HttpSessionEvent(this));
            }
        } else {
            lastAccessedTime = currAccessedTime;
            currAccessedTime = System.currentTimeMillis();
        }
        save();
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
        this.accessCount = source.getAccessCount();

        if (attributeMap != null) {
            httpSessionBindingListenerList.clear();
            for (Object value : attributeMap.values()) {
                if (!(value instanceof HttpSessionBindingListener)) {
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

        source.setAccessCount(accessCount);
        source.setMaxInactiveInterval(maxInactiveInterval);
        source.setAttributeMap(attributeMap);
        return source;
    }

    @Override
    public String toString() {
        return "ServletHttpSession[" + id + ']';
    }

}
