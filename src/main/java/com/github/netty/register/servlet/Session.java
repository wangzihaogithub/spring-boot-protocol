package com.github.netty.register.servlet;

import java.io.Serializable;
import java.util.Map;

/**
 * 会话实体类
 * @author acer01
 *  2018/8/18/018
 */
public class Session implements Serializable{
    private static final long serialVersionUID = 1L;

    private String id;

    private Map<String,Object> attributeMap;
    private long creationTime;
    private long lastAccessedTime;
    /**
     * 单位 秒
     */
    private int maxInactiveInterval;
    private int accessCount;

    public Session() {
    }

    public Session(String id) {
        this.id = id;
    }

    /**
     * 是否有效
     * @return true 有效, false无效
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
