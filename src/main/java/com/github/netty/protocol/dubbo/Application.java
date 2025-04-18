package com.github.netty.protocol.dubbo;

import java.net.InetSocketAddress;

/**
 * dubbo应用路由配置
 */
public class Application {
    /**
     * dubbo应用名
     */
    private String name;
    /**
     * dubbo应用地址
     */
    private InetSocketAddress address;
    /**
     * 从dubbo哪个attachment字段取服务名称
     */
    private String attachmentApplicationName;
    /**
     * dubbo路径映射服务
     */
    private String[] pathPatterns;
    /**
     * 是否是默认应用
     */
    private boolean defaultApplication;
    /**
     * 后端心跳间隔毫秒
     */
    private int heartbeatIntervalMs = Constant.DEFAULT_HEARTBEAT;

    public Application() {
    }

    public Application(String name, InetSocketAddress address) {
        this(name, address, "remote.application", null, false);
    }

    public Application(String name, InetSocketAddress address, String attachmentApplicationName) {
        this(name, address, attachmentApplicationName, null, false);
    }

    public Application(String name, InetSocketAddress address, String attachmentApplicationName, String[] pathPatterns) {
        this(name, address, attachmentApplicationName, pathPatterns, false);
    }

    public Application(String name, InetSocketAddress address, String attachmentApplicationName, String[] pathPatterns, boolean defaultApplication) {
        this.name = name;
        this.attachmentApplicationName = attachmentApplicationName;
        this.address = address;
        this.pathPatterns = pathPatterns;
        this.defaultApplication = defaultApplication;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public int getHeartbeatIntervalMs() {
        return heartbeatIntervalMs;
    }

    public void setHeartbeatIntervalMs(int heartbeatIntervalMs) {
        this.heartbeatIntervalMs = heartbeatIntervalMs;
    }

    public String getAttachmentApplicationName() {
        return attachmentApplicationName;
    }

    public void setAttachmentApplicationName(String attachmentApplicationName) {
        this.attachmentApplicationName = attachmentApplicationName;
    }

    public InetSocketAddress getAddress() {
        return address;
    }

    public void setAddress(InetSocketAddress address) {
        this.address = address;
    }

    public String[] getPathPatterns() {
        return pathPatterns;
    }

    public void setPathPatterns(String[] pathPatterns) {
        this.pathPatterns = pathPatterns;
    }

    public boolean isDefaultApplication() {
        return defaultApplication;
    }

    public void setDefaultApplication(boolean defaultApplication) {
        this.defaultApplication = defaultApplication;
    }

    public String getDisplayName() {
        String string;
        if (name != null && !name.isEmpty()) {
            string = name;
        } else if (pathPatterns != null) {
            string = String.join(",", pathPatterns);
        } else if (address != null) {
            string = address.toString();
        } else {
            string = "null";
        }
        return string;
    }

    @Override
    public String toString() {
        String string = getDisplayName();
        return defaultApplication ? "(default)" + string : string;
    }
}