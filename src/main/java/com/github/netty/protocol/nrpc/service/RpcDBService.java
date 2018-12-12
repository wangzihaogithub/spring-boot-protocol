package com.github.netty.protocol.nrpc.service;

import com.github.netty.annotation.Protocol;

import java.util.List;

/**
 * 数据存储服务
 *
 * @author acer01
 * 2018/8/20/020
 */
@Protocol.RpcService(value = "/hrpc/db",timeout = 1000 * 10)
public interface RpcDBService {

    /**
     * 存在key
     * @param key
     * @param group 分组
     * @return
     */
    boolean exist2(@Protocol.RpcParam("key") String key, @Protocol.RpcParam("group") String group);
    boolean exist(@Protocol.RpcParam("key") String key);

    /**
     * 存入数据
     * @param key
     * @param data
     * @param expireSecond 过期时间(秒)
     * @param group 分组
     */
    void put4(@Protocol.RpcParam("group") String key, @Protocol.RpcParam("data") byte[] data, @Protocol.RpcParam("expireSecond") int expireSecond, @Protocol.RpcParam("group") String group);
    void put3(@Protocol.RpcParam("group") String key, @Protocol.RpcParam("data") byte[] data, @Protocol.RpcParam("expireSecond") int expireSecond);
    void put(@Protocol.RpcParam("group") String key, @Protocol.RpcParam("data") byte[] data);

    /**
     * 获取某个组的数量
     * @param group 分组
     */
    int count(@Protocol.RpcParam("group") String group);

    /**
     * 获取数据
     * @param key
     * @param group 分组
     * @return
     */
    byte[] get2(@Protocol.RpcParam("key") String key, @Protocol.RpcParam("group") String group);
    byte[] get(@Protocol.RpcParam("key") String key);

    /**
     * 改变key
     * @param oldKey
     * @param newKey
     * @param group 分组
     */
    void changeKey3(@Protocol.RpcParam("oldKey") String oldKey, @Protocol.RpcParam("newKey") String newKey, @Protocol.RpcParam("group") String group);
    void changeKey(@Protocol.RpcParam("oldKey") String oldKey, @Protocol.RpcParam("newKey") String newKey);

    /**
     * 删除数据
     * @param key

     */
    void remove2(@Protocol.RpcParam("key") String key, @Protocol.RpcParam("group") String group);
    void remove(@Protocol.RpcParam("key") String key);

    /**
     * 删除多条数据
     * @param keys
     * @param group 分组
     */
    void removeBatch2(@Protocol.RpcParam("keys") List<String> keys, @Protocol.RpcParam("group") String group);
    void removeBatch(@Protocol.RpcParam("keys") List<String> keys);

}
