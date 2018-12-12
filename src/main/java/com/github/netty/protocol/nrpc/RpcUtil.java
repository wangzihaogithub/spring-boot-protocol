package com.github.netty.protocol.nrpc;

import com.github.netty.annotation.Protocol;
import com.github.netty.core.util.ReflectUtil;
import com.github.netty.core.util.StringUtil;
import io.netty.buffer.ByteBuf;

import static com.github.netty.protocol.nrpc.RpcEncoder.PROTOCOL_HEADER;

/**
 *
 * @author acer01
 *  2018/11/25/025
 */
public class RpcUtil {

    /**
     * 获取服务名称
     * @param instanceClass
     */
    public static String getServiceName(Class instanceClass){
        String serviceName = "";
        Protocol.RpcService rpcInterfaceAnn = ReflectUtil.findAnnotation(instanceClass, Protocol.RpcService.class);
        if (rpcInterfaceAnn != null) {
            serviceName = rpcInterfaceAnn.value();
        }

        if(serviceName.isEmpty()){
            Class[] classes = ReflectUtil.getInterfaces(instanceClass);
            if(classes.length > 0){
                serviceName = '/'+ StringUtil.firstLowerCase(classes[0].getSimpleName());
            }else {
                serviceName =  '/'+ StringUtil.firstLowerCase(instanceClass.getSimpleName());
            }
        }
        return serviceName;
    }

    /**
     * 该消息是否是RPC协议
     * @param msg 消息
     * @return true=是, false=否
     */
    public static boolean isRpcProtocols(ByteBuf msg){
        int protocolLength = msg.getByte(0);
        if(PROTOCOL_HEADER.length != protocolLength){
            return false;
        }

        for(int i=0; i< protocolLength; i++) {
            if(msg.getByte(i + 1) != PROTOCOL_HEADER[i]){
                return false;
            }
        }
        return true;
    }
}
