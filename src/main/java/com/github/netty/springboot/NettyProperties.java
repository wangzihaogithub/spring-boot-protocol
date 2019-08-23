package com.github.netty.springboot;

import com.github.netty.core.util.ApplicationX;
import io.netty.handler.logging.LogLevel;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.io.File;
import java.io.Serializable;
import java.util.concurrent.Executor;

/**
 * You can configure it here
 * @author wangzihao
 * 2018/8/25/025
 */
@ConfigurationProperties(prefix = "server.netty", ignoreUnknownFields = true)
public class NettyProperties implements Serializable{
    private static final long serialVersionUID = 1L;

    /**
     * 服务端 - TCP级别最大同时在线的连接数
     */
    private int maxConnections = 10000;
    /**
     * 服务端 - 是否tcp数据包日志
     */
    private boolean enableTcpPackageLog = false;

    /**
     * 服务端 - tcp数据包日志等级(需要先开启tcp数据包日志)
     */
    private io.netty.handler.logging.LogLevel tcpPackageLogLevel = io.netty.handler.logging.LogLevel.DEBUG;

    /**
     * 服务端-IO线程数  注: (0 = cpu核数 * 2 )
     */
    private int serverIoThreads = 0;
    /**
     * 服务端-io线程执行调度与执行io事件的百分比. 注:(100=每次只执行一次调度工作, 其他都执行io事件), 并发高的时候可以设置最大
     */
    private int serverIoRatio = 100;

    /**
     * HTTP协议(Servlet实现)
     */
    private final HttpServlet httpServlet = new HttpServlet();
    /**
     * NRPC协议
     */
    private final Nrpc nrpc = new Nrpc();
    /**
     * MQTT协议
     */
    private final Mqtt mqtt = new Mqtt();
    /**
     * RTSP协议
     */
    private final Rtsp rtsp = new Rtsp();

    /**
     * 全局对象
     */
    private transient final ApplicationX application = new ApplicationX();

    public NettyProperties() {}

    public ApplicationX getApplication() {
        return application;
    }

    public LogLevel getTcpPackageLogLevel() {
        return tcpPackageLogLevel;
    }

    public void setTcpPackageLogLevel(LogLevel tcpPackageLogLevel) {
        this.tcpPackageLogLevel = tcpPackageLogLevel;
    }

    public int getServerIoThreads() {
        return serverIoThreads;
    }

    public void setServerIoThreads(int serverIoThreads) {
        this.serverIoThreads = serverIoThreads;
    }

    public int getServerIoRatio() {
        return serverIoRatio;
    }

    public void setServerIoRatio(int serverIoRatio) {
        this.serverIoRatio = serverIoRatio;
    }

    public boolean isEnableTcpPackageLog() {
        return enableTcpPackageLog;
    }

    public void setEnableTcpPackageLog(boolean enableTcpPackageLog) {
        this.enableTcpPackageLog = enableTcpPackageLog;
    }

    public int getMaxConnections() {
        return maxConnections;
    }

    public void setMaxConnections(int maxConnections) {
        this.maxConnections = maxConnections;
    }

    public Nrpc getNrpc() {
        return nrpc;
    }

    public Mqtt getMqtt() {
        return mqtt;
    }

    public Rtsp getRtsp() {
        return rtsp;
    }

    public HttpServlet getHttpServlet() {
        return httpServlet;
    }

    public static class HttpServlet{
        /**
         * 请求体最大字节
         */
        private int maxContentSize = 5 * 1024 * 1024;
        /**
         * 请求头每行最大字节
         */
        private int maxHeaderLineSize = 4096;
        /**
         * 请求头最大字节
         */
        private int maxHeaderSize = 8192;
        /**
         * 大于这个字节则进行分段传输
         */
        private int maxChunkSize = 5 * 1024 * 1024;
        /**
         * 服务端 - servlet线程执行器
         */
        private Class<?extends Executor> serverHandlerExecutor = null;

        /**
         * session存储 - 是否开启本地文件存储
         */
        private boolean enablesLocalFileSession = false;

        /**
         * session存储 - session远程存储的url地址, 注: 如果不设置就不会开启
         */
        private String sessionRemoteServerAddress;

        /**
         * 每次调用servlet的 OutputStream.Writer()方法写入的最大堆字节,超出后用堆外内存
         */
        private int responseWriterChunkMaxHeapByteLength = 4096;

        /**
         * 文件基础目录。如果未指定，则使用临时目录。
         */
        private File basedir;

        public int getMaxContentSize() {
            return maxContentSize;
        }

        public void setMaxContentSize(int maxContentSize) {
            this.maxContentSize = maxContentSize;
        }

        public int getMaxHeaderLineSize() {
            return maxHeaderLineSize;
        }

        public void setMaxHeaderLineSize(int maxHeaderLineSize) {
            this.maxHeaderLineSize = maxHeaderLineSize;
        }

        public int getMaxHeaderSize() {
            return maxHeaderSize;
        }

        public void setMaxHeaderSize(int maxHeaderSize) {
            this.maxHeaderSize = maxHeaderSize;
        }

        public int getMaxChunkSize() {
            return maxChunkSize;
        }

        public void setMaxChunkSize(int maxChunkSize) {
            this.maxChunkSize = maxChunkSize;
        }

        public Class<?extends Executor> getServerHandlerExecutor() {
            return serverHandlerExecutor;
        }

        public void setServerHandlerExecutor(Class<?extends Executor> serverHandlerExecutor) {
            this.serverHandlerExecutor = serverHandlerExecutor;
        }

        public boolean isEnablesLocalFileSession() {
            return enablesLocalFileSession;
        }

        public void setEnablesLocalFileSession(boolean enablesLocalFileSession) {
            this.enablesLocalFileSession = enablesLocalFileSession;
        }

        public String getSessionRemoteServerAddress() {
            return sessionRemoteServerAddress;
        }

        public void setSessionRemoteServerAddress(String sessionRemoteServerAddress) {
            this.sessionRemoteServerAddress = sessionRemoteServerAddress;
        }

        public int getResponseWriterChunkMaxHeapByteLength() {
            return responseWriterChunkMaxHeapByteLength;
        }

        public void setResponseWriterChunkMaxHeapByteLength(int responseWriterChunkMaxHeapByteLength) {
            this.responseWriterChunkMaxHeapByteLength = responseWriterChunkMaxHeapByteLength;
        }

        public File getBasedir() {
            return basedir;
        }

        public void setBasedir(File basedir) {
            this.basedir = basedir;
        }
    }

    public static class Nrpc {

        /**
         * RPC客户端-工作线程数   注: (0 = cpu核数 * 2 )
         */
        private int clientIoThreads = 0;

        /**
         * RPC客户端-IO线程执行调度与执行io事件的百分比. 注:(100=每次只执行一次调度工作, 其他都执行io事件), 并发高的时候可以设置最大
         */
        private int clientIoRatio = 100;

        /**
         * RPC客户端-是否RPC开启心跳日志
         */
        private boolean clientEnableHeartLog = false;

        /**
         * RPC客户端 - 自动重连
         */
        private boolean clientAutoReconnect = true;

        /**
         * RPC客户端 - 心跳间隔时间(秒)
         */
        private int clientHeartInterval = 20;
        /**
         * RPC服务端 - 每次消息最大长度 (默认10M)
         */
        private int serverMessageMaxLength = 10 * 1024 * 1024;

        public int getClientIoThreads() {
            return clientIoThreads;
        }

        public void setClientIoThreads(int clientIoThreads) {
            this.clientIoThreads = clientIoThreads;
        }

        public int getClientIoRatio() {
            return clientIoRatio;
        }

        public void setClientIoRatio(int clientIoRatio) {
            this.clientIoRatio = clientIoRatio;
        }

        public boolean isClientEnableHeartLog() {
            return clientEnableHeartLog;
        }

        public void setClientEnableHeartLog(boolean clientEnableHeartLog) {
            this.clientEnableHeartLog = clientEnableHeartLog;
        }

        public boolean isClientAutoReconnect() {
            return clientAutoReconnect;
        }

        public void setClientAutoReconnect(boolean clientAutoReconnect) {
            this.clientAutoReconnect = clientAutoReconnect;
        }

        public int getClientHeartInterval() {
            return clientHeartInterval;
        }

        public void setClientHeartInterval(int clientHeartInterval) {
            this.clientHeartInterval = clientHeartInterval;
        }

        public int getServerMessageMaxLength() {
            return serverMessageMaxLength;
        }

        public void setServerMessageMaxLength(int serverMessageMaxLength) {
            this.serverMessageMaxLength = serverMessageMaxLength;
        }
    }

    public static class Mqtt{
        /**
         * 是否开启MQTT协议
         */
        private boolean enabled = false;
        /**
         * 消息最大长度(字节)
         */
        private int messageMaxLength = 8192;
        /**
         * netty读事件空闲时间(秒)
         */
        private int nettyReaderIdleTimeSeconds = 10;
        /**
         * 刷新缓冲区数据间隔(秒) (如果大于0秒则定时发送缓冲区数据, 小于等于0秒则实时发送数据)
         */
        private int autoFlushIdleTime = 0;

        public int getMessageMaxLength() {
            return messageMaxLength;
        }

        public void setMessageMaxLength(int messageMaxLength) {
            this.messageMaxLength = messageMaxLength;
        }

        public int getNettyReaderIdleTimeSeconds() {
            return nettyReaderIdleTimeSeconds;
        }

        public void setNettyReaderIdleTimeSeconds(int nettyReaderIdleTimeSeconds) {
            this.nettyReaderIdleTimeSeconds = nettyReaderIdleTimeSeconds;
        }

        public int getAutoFlushIdleTime() {
            return autoFlushIdleTime;
        }

        public void setAutoFlushIdleTime(int autoFlushIdleTime) {
            this.autoFlushIdleTime = autoFlushIdleTime;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public boolean isEnabled() {
            return enabled;
        }
    }

    public static class Rtsp{

    }
}
