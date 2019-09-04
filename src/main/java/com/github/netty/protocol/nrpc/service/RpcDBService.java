package com.github.netty.protocol.nrpc.service;

import com.github.netty.annotation.Protocol;

import java.util.List;

/**
 * RpcDBService
 *
 * @author wangzihao
 * 2018/8/20/020
 */
@Protocol.RpcService(value = "/hrpc/db",timeout = 1000 * 10)
public interface RpcDBService {

    /**
     * 存在key
     * @param key key
     * @param group 分组
     * @return boolean
     */
    boolean exist2(@Protocol.RpcParam("key") String key, @Protocol.RpcParam("group") String group);
    boolean exist(@Protocol.RpcParam("key") String key);

    /**
     * put
     * @param key key
     * @param data data
     * @param expireSecond expireSecond
     * @param group group
     */
    void put4(@Protocol.RpcParam("group") String key, @Protocol.RpcParam("data") byte[] data, @Protocol.RpcParam("expireSecond") int expireSecond, @Protocol.RpcParam("group") String group);
    void put3(@Protocol.RpcParam("group") String key, @Protocol.RpcParam("data") byte[] data, @Protocol.RpcParam("expireSecond") int expireSecond);
    void put(@Protocol.RpcParam("group") String key, @Protocol.RpcParam("data") byte[] data);

    /**
     * Gets the number of groups
     * @param group group
     * @return count
     */
    int count(@Protocol.RpcParam("group") String group);

    /**
     * To get the data
     * @param key key
     * @param group group
     * @return byte[] data
     */
    byte[] get2(@Protocol.RpcParam("key") String key, @Protocol.RpcParam("group") String group);
    byte[] get(@Protocol.RpcParam("key") String key);

    /**
     * changeKey
     * @param oldKey oldKey
     * @param newKey newKey
     * @param group group
     */
    void changeKey3(@Protocol.RpcParam("oldKey") String oldKey, @Protocol.RpcParam("newKey") String newKey, @Protocol.RpcParam("group") String group);
    void changeKey(@Protocol.RpcParam("oldKey") String oldKey, @Protocol.RpcParam("newKey") String newKey);

    /**
     * remove data
     * @param key key
     * @param group group
     */
    void remove2(@Protocol.RpcParam("key") String key, @Protocol.RpcParam("group") String group);
    void remove(@Protocol.RpcParam("key") String key);

    /**
     * remove data Batch
     * @param keys keys
     * @param group group
     */
    void removeBatch2(@Protocol.RpcParam("keys") List<String> keys, @Protocol.RpcParam("group") String group);
    void removeBatch(@Protocol.RpcParam("keys") List<String> keys);

	/**
	 * Set the max number for this group
	 * @param maxSize the group maxSize
	 * @param group group
	 */
    void setMaxSize2(@Protocol.RpcParam("maxSize")Integer maxSize,@Protocol.RpcParam("group") String group);
	void setMaxSize(@Protocol.RpcParam("maxSize")Integer maxSize);


}
