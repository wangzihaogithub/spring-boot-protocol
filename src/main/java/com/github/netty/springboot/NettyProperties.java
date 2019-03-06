package com.github.netty.springboot;

import com.github.netty.core.util.ApplicationX;
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
     * 服务端 - 是否tcp数据包日志
     */
    private boolean enableTcpPackageLog = false;
    /**
     * 服务端-IO线程数  注: (0 = cpu核数 * 2 )
     */
    private int serverIoThreads = 0;

    /**
     * 服务端 - servlet线程执行器
     */
    private Executor serverHandlerExecutor = null;

    /**
     * 服务端-io线程执行调度与执行io事件的百分比. 注:(100=每次只执行一次调度工作, 其他都执行io事件), 并发高的时候可以设置最大
     */
    private int serverIoRatio = 100;

    /**
     * RPC客户端-工作线程数   注: (0 = cpu核数 * 2 )
     */
    private int rpcClientIoThreads = 1;

    /**
     * RPC客户端-IO线程执行调度与执行io事件的百分比. 注:(100=每次只执行一次调度工作, 其他都执行io事件), 并发高的时候可以设置最大
     */
    private int rpcClientIoRatio = 100;

    /**
     * RPC客户端-RPC同步调用超时时间
     */
    private int rpcTimeout = 1000;

    /**
     * RPC客户端 - 保持的连接数
     */
    private int rpcClientChannels = 1;

    /**
     * RPC客户端-是否RPC开启心跳日志
     */
    private boolean enableRpcHeartLog = false;

    /**
     * RPC客户端 - 自动重连
     */
    private boolean enablesRpcClientAutoReconnect = true;

    /**
     * RPC客户端 - 心跳间隔时间(秒)
     */
    private int rpcClientHeartIntervalSecond = 20;
    /**
     * RPC服务端 - 每次消息最大长度 (默认10M)
     */
    private int rpcServerMessageMaxLength = 10 * 1024 * 1024;
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

    /**
     * 全局对象
     */
    private transient ApplicationX application = new ApplicationX();

    public NettyProperties() {}

    public ApplicationX getApplication() {
        return application;
    }

    public void setApplication(ApplicationX application) {
        this.application = application;
    }

    public File getBasedir() {
        return basedir;
    }

    public void setBasedir(File basedir) {
        this.basedir = basedir;
    }

    public int getRpcClientHeartIntervalSecond() {
        return rpcClientHeartIntervalSecond;
    }

    public void setRpcClientHeartIntervalSecond(int rpcClientHeartIntervalSecond) {
        this.rpcClientHeartIntervalSecond = rpcClientHeartIntervalSecond;
    }

    public boolean isEnablesLocalFileSession() {
        return enablesLocalFileSession;
    }

    public void setEnablesLocalFileSession(boolean enablesLocalFileSession) {
        this.enablesLocalFileSession = enablesLocalFileSession;
    }

    public int getRpcTimeout() {
        return rpcTimeout;
    }

    public void setRpcTimeout(int rpcTimeout) {
        this.rpcTimeout = rpcTimeout;
    }

    public int getResponseWriterChunkMaxHeapByteLength() {
        return responseWriterChunkMaxHeapByteLength;
    }

    public void setResponseWriterChunkMaxHeapByteLength(int responseWriterChunkMaxHeapByteLength) {
        this.responseWriterChunkMaxHeapByteLength = responseWriterChunkMaxHeapByteLength;
    }

    public int getRpcClientIoThreads() {
        return rpcClientIoThreads;
    }

    public void setRpcClientIoThreads(int rpcClientIoThreads) {
        this.rpcClientIoThreads = rpcClientIoThreads;
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

    public int getRpcClientIoRatio() {
        return rpcClientIoRatio;
    }

    public void setRpcClientIoRatio(int rpcClientIoRatio) {
        this.rpcClientIoRatio = rpcClientIoRatio;
    }

    public int getRpcClientChannels() {
        return rpcClientChannels;
    }

    public void setRpcClientChannels(int rpcClientChannels) {
        this.rpcClientChannels = rpcClientChannels;
    }

    public boolean isEnablesRpcClientAutoReconnect() {
        return enablesRpcClientAutoReconnect;
    }

    public void setEnablesRpcClientAutoReconnect(boolean enablesRpcClientAutoReconnect) {
        this.enablesRpcClientAutoReconnect = enablesRpcClientAutoReconnect;
    }

    public Executor getServerHandlerExecutor() {
        return serverHandlerExecutor;
    }

    public void setServerHandlerExecutor(Executor serverHandlerExecutor) {
        this.serverHandlerExecutor = serverHandlerExecutor;
    }

    public String getSessionRemoteServerAddress() {
        return sessionRemoteServerAddress;
    }

    public void setSessionRemoteServerAddress(String sessionRemoteServerAddress) {
        this.sessionRemoteServerAddress = sessionRemoteServerAddress;
    }

    public boolean isEnableRpcHeartLog() {
        return enableRpcHeartLog;
    }

    public void setEnableRpcHeartLog(boolean enableRpcHeartLog) {
        this.enableRpcHeartLog = enableRpcHeartLog;
    }

    public int getRpcServerMessageMaxLength() {
        return rpcServerMessageMaxLength;
    }

    public void setRpcServerMessageMaxLength(int rpcServerMessageMaxLength) {
        this.rpcServerMessageMaxLength = rpcServerMessageMaxLength;
    }

    public boolean isEnableTcpPackageLog() {
        return enableTcpPackageLog;
    }

    public void setEnableTcpPackageLog(boolean enableTcpPackageLog) {
        this.enableTcpPackageLog = enableTcpPackageLog;
    }

    @Override
    public String toString() {
        return "NettyProperties{" +
                "serverWorkerCount=" + serverIoThreads +
                ", serverIoRatio=" + serverIoRatio +
                ", rpcClientWorkerCount=" + rpcClientIoThreads +
                ", rpcClientIoRatio=" + rpcClientIoRatio +
                ", rpcTimeout=" + rpcTimeout +
                ", rpcClientChannelCount=" + rpcClientChannels +
                ", enablesRpcClientAutoReconnect=" + enablesRpcClientAutoReconnect +
                ", rpcClientHeartIntervalSecond=" + rpcClientHeartIntervalSecond +
                ", sessionRemoteServerAddress='" + sessionRemoteServerAddress + '\'' +
                ", responseWriterChunkMaxHeapByteLength=" + responseWriterChunkMaxHeapByteLength +
                ", rpcServerMessageMaxLength=" + rpcServerMessageMaxLength +
                ", enableTcpPackageLog=" + enableTcpPackageLog +
                ", basedir=" + basedir +
                '}';
    }
}
