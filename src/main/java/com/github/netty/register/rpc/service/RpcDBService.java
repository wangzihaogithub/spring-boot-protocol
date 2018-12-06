package com.github.netty.register.rpc.service;

import com.github.netty.annotation.RegisterFor;

import java.util.List;

/**
 * 数据存储服务
 *
 * @author acer01
 * 2018/8/20/020
 */
@RegisterFor.RpcService(value = "/hrpc/db",timeout = 1000 * 10)
public interface RpcDBService {

    /**
     * 存在key
     * @param key
     * @param group 分组
     * @return
     */
    boolean exist2(@RegisterFor.RpcParam("key") String key, @RegisterFor.RpcParam("group") String group);
    boolean exist(@RegisterFor.RpcParam("key") String key);

    /**
     * 存入数据
     * @param key
     * @param data
     * @param expireSecond 过期时间(秒)
     * @param group 分组
     */
    void put4(@RegisterFor.RpcParam("group") String key, @RegisterFor.RpcParam("data") byte[] data, @RegisterFor.RpcParam("expireSecond") int expireSecond, @RegisterFor.RpcParam("group") String group);
    void put3(@RegisterFor.RpcParam("group") String key, @RegisterFor.RpcParam("data") byte[] data, @RegisterFor.RpcParam("expireSecond") int expireSecond);
    void put(@RegisterFor.RpcParam("group") String key, @RegisterFor.RpcParam("data") byte[] data);

    /**
     * 获取某个组的数量
     * @param group 分组
     */
    int count(@RegisterFor.RpcParam("group") String group);

    /**
     * 获取数据
     * @param key
     * @param group 分组
     * @return
     */
    byte[] get2(@RegisterFor.RpcParam("key") String key, @RegisterFor.RpcParam("group") String group);
    byte[] get(@RegisterFor.RpcParam("key") String key);

    /**
     * 改变key
     * @param oldKey
     * @param newKey
     * @param group 分组
     */
    void changeKey3(@RegisterFor.RpcParam("oldKey") String oldKey, @RegisterFor.RpcParam("newKey") String newKey, @RegisterFor.RpcParam("group") String group);
    void changeKey(@RegisterFor.RpcParam("oldKey") String oldKey, @RegisterFor.RpcParam("newKey") String newKey);

    /**
     * 删除数据
     * @param key

     */
    void remove2(@RegisterFor.RpcParam("key") String key, @RegisterFor.RpcParam("group") String group);
    void remove(@RegisterFor.RpcParam("key") String key);

    /**
     * 删除多条数据
     * @param keys
     * @param group 分组
     */
    void removeBatch2(@RegisterFor.RpcParam("keys") List<String> keys, @RegisterFor.RpcParam("group") String group);
    void removeBatch(@RegisterFor.RpcParam("keys") List<String> keys);

}
