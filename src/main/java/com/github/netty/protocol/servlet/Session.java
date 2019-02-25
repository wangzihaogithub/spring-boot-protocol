package com.github.netty.protocol.servlet;

import java.io.Serializable;
import java.util.Map;

/**
 * Session entity class
 * @author wangzihao
 *  2018/8/18/018
 */
public class Session implements Serializable{
    private static final long serialVersionUID = 1L;

    private String id;
    private Map<String,Object> attributeMap;
    private long creationTime;
    private long lastAccessedTime;
    /**
     * Unit seconds
     */
    private int maxInactiveInterval;
    private int accessCount;

    public Session() {
    }

    public Session(String id) {
        this.id = id;
    }

    /**
     * The validity of
     * @return True is valid, false is not
     */
    public boolean isValid() {
        return System.currentTimeMillis() < (creationTime + (maxInactiveInterval * 1000));
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public Map<String, Object> getAttributeMap() {
        return attributeMap;
    }

    public void setAttributeMap(Map<String, Object> attributeMap) {
        this.attributeMap = attributeMap;
    }

    public long getCreationTime() {
        return creationTime;
    }

    public void setCreationTime(long creationTime) {
        this.creationTime = creationTime;
    }

    public long getLastAccessedTime() {
        return lastAccessedTime;
    }

    public void setLastAccessedTime(long lastAccessedTime) {
        this.lastAccessedTime = lastAccessedTime;
    }

    public int getMaxInactiveInterval() {
        return maxInactiveInterval;
    }

    public void setMaxInactiveInterval(int maxInactiveInterval) {
        this.maxInactiveInterval = maxInactiveInterval;
    }

    public int getAccessCount() {
        return accessCount;
    }

    public void setAccessCount(int accessCount) {
        this.accessCount = accessCount;
    }

    @Override
    public String toString() {
        return "Session{" +
                "id='" + id + '\'' +
                ", accessCount=" + accessCount +
                '}';
    }
}
