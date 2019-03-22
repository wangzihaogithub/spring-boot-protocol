package com.github.netty.protocol.nrpc;

import com.github.netty.annotation.Protocol;
import com.github.netty.core.util.ReflectUtil;
import com.github.netty.core.util.StringUtil;
import io.netty.buffer.ByteBuf;

import static com.github.netty.protocol.nrpc.RpcEncoder.PROTOCOL_HEADER;

/**
 *
 * @author wangzihao
 *  2018/11/25/025
 */
public class RpcUtil {

    /**
     * Get the service name
     * @param instanceClass instanceClass
     * @return serviceName
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

}
