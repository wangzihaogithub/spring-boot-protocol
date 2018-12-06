package com.github.netty.core;

/**
 * 服务器监听
 * Created by acer01 on 2018/11/12/012.
 */
public interface ServerListener {

    void onServerStart() throws Exception;
    void onServerStop() throws Exception;
}
