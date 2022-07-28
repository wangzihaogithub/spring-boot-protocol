package com.github.netty.protocol.nrpc.service;

import com.github.netty.annotation.NRpcParam;
import com.github.netty.annotation.NRpcService;

import java.util.List;

/**
 * RpcDBService
 *
 * @author wangzihao
 * 2018/8/20/020
 */
@NRpcService(value = "/_nrpc/db", timeout = 1500)
public interface RpcDBService {

    /**
     * exist key
     *
     * @param key   key
     * @param group group
     * @return boolean
     */
    boolean exist2(@NRpcParam("key") String key, @NRpcParam("group") String group);

    boolean exist(@NRpcParam("key") String key);

    /**
     * put
     *
     * @param key          key
     * @param data         data
     * @param expireSecond expireSecond
     * @param group        group
     */
    void put4(@NRpcParam("group") String key, @NRpcParam("data") byte[] data, @NRpcParam("expireSecond") int expireSecond, @NRpcParam("group") String group);

    void put3(@NRpcParam("group") String key, @NRpcParam("data") byte[] data, @NRpcParam("expireSecond") int expireSecond);

    void put(@NRpcParam("group") String key, @NRpcParam("data") byte[] data);

    /**
     * Gets the number of groups
     *
     * @param group group
     * @return count
     */
    int count(@NRpcParam("group") String group);

    /**
     * To get the data
     *
     * @param key   key
     * @param group group
     * @return byte[] data
     */
    byte[] get2(@NRpcParam("key") String key, @NRpcParam("group") String group);

    byte[] get(@NRpcParam("key") String key);

    /**
     * changeKey
     *
     * @param oldKey oldKey
     * @param newKey newKey
     * @param group  group
     */
    void changeKey3(@NRpcParam("oldKey") String oldKey, @NRpcParam("newKey") String newKey, @NRpcParam("group") String group);

    void changeKey(@NRpcParam("oldKey") String oldKey, @NRpcParam("newKey") String newKey);

    /**
     * remove data
     *
     * @param key   key
     * @param group group
     */
    void remove2(@NRpcParam("key") String key, @NRpcParam("group") String group);

    void remove(@NRpcParam("key") String key);

    /**
     * remove data Batch
     *
     * @param keys  keys
     * @param group group
     */
    void removeBatch2(@NRpcParam("keys") List<String> keys, @NRpcParam("group") String group);

    void removeBatch(@NRpcParam("keys") List<String> keys);

    /**
     * Set the max number for this group
     *
     * @param maxSize the group maxSize
     * @param group   group
     */
    void setMaxSize2(@NRpcParam("maxSize") Integer maxSize, @NRpcParam("group") String group);

    void setMaxSize(@NRpcParam("maxSize") Integer maxSize);


}
