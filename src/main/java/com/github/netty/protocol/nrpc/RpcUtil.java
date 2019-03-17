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
    public static final int OK = 200;
    public static final int NO_SUCH_METHOD = 400;
    public static final int NO_SUCH_SERVICE = 401;
    public static final int SERVER_ERROR = 500;

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

    /**
     * Whether the message is an RPC protocol
     * @param msg message
     * @return true=yes, false=no
     */
    public static boolean isRpcProtocols(ByteBuf msg){
        if(msg == null || msg.readableBytes() < RpcDecoder.MIN_PACKET_LENGTH){
            return false;
        }
        for(int i=0; i< 4; i++) {
            //NPRC == NPRC
            if(msg.getByte(i) != PROTOCOL_HEADER[i]){
                return false;
            }
        }
        return true;
    }
}
