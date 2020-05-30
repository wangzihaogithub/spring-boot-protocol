package com.github.netty.protocol;

import com.github.netty.annotation.Protocol;
import com.github.netty.core.AbstractNettyServer;
import com.github.netty.core.AbstractProtocol;
import com.github.netty.core.util.*;
import com.github.netty.protocol.nrpc.*;
import com.github.netty.protocol.nrpc.service.RpcCommandServiceImpl;
import com.github.netty.protocol.nrpc.service.RpcDBServiceImpl;
import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import io.netty.channel.ChannelPipeline;

import java.lang.reflect.Method;
import java.util.*;
import java.util.function.Function;

import static com.github.netty.protocol.nrpc.RpcServerChannelHandler.getRequestMappingName;

/**
 * Internal RPC protocol registry
 *
 *  ACK flag : (0=Don't need, 1=Need)
 *   Request Packet (note:  1 = request type)
 *-+------8B--------+--1B--+--1B--+------4B------+-----4B-----+------1B--------+-----length-----+------1B-------+---length----+-----4B------+-------length-------------+
 * | header/version | type | ACK   | total length | Request ID | service length | service name   | method length | method name | data length |         data             |
 * |   NRPC/010     |  1   | 1    |     55       |     1      |       8        | "/sys/user"    |      7        |  getUser    |     24      | {"age":10,"name":"wang"} |
 *-+----------------+------+------+--------------+------------+----------------+----------------+---------------+-------------+-------------+--------------------------+
 *
 *
 *   Response Packet (note: 2 = response type)
 *-+------8B--------+--1B--+--1B--+------4B------+-----4B-----+---2B---+--------1B------+--length--+---1B---+-----4B------+----------length----------+
 * | header/version | type | ACK   | total length | Request ID | status | message length | message  | encode | data length |         data             |
 * |   NRPC/010     |  2   | 0    |     35       |     1      |  200   |       2        |  ok      | 1      |     24      | {"age":10,"name":"wang"} |
 *-+----------------+------+------+--------------+------------+--------+----------------+----------+--------+-------------+--------------------------+
 *
 *
 *-+------2B-------+--1B--+----1B----+-----8B-----+------1B-----+----------------dynamic---------------------+-------dynamic------------+
 * | packet length | type | ACK flag |   version  | Fields size |                Fields                      |          Body            |
 * |      76       |  1   |   1      |   NRPC/201 |     2       | 11requestMappingName6/hello10methodName8sayHello  | {"age":10,"name":"wang"} |
 *-+---------------+------+----------+------------+-------------+--------------------------------------------+--------------------------+
 *
 * @author wangzihao
 * 2018/11/25/025
 */
public class NRpcProtocol extends AbstractProtocol {
    private LoggerX logger = LoggerFactoryX.getLogger(getClass());
    private ApplicationX application;
    /**
     * Maximum message length per pass
     */
    private int messageMaxLength = 10 * 1024 * 1024;
    /**
     * Check the method of the same name (because the generalization parameter called allow inconsistent,
     * so the name of the method to ensure that each class is unique)
     */
    private boolean methodOverwriteCheck = true;
    private Map<Object,Instance> instanceMap = new HashMap<>();
    private String serverDefaultVersion;
    private final List<RpcServerAop> rpcServerAopList = new ArrayList<>();
    private final AnnotationMethodToMethodNameFunction annotationMethodToMethodNameFunction = new AnnotationMethodToMethodNameFunction(Protocol.RpcMethod.class);
    public NRpcProtocol(ApplicationX application) {
        this.application = application;
    }

    public AnnotationMethodToMethodNameFunction getAnnotationMethodToMethodNameFunction() {
        return annotationMethodToMethodNameFunction;
    }

    public boolean isMethodOverwriteCheck() {
        return methodOverwriteCheck;
    }

    public void setMethodOverwriteCheck(boolean methodOverwriteCheck) {
        this.methodOverwriteCheck = methodOverwriteCheck;
    }

    public void setServerDefaultVersion(String serverDefaultVersion) {
        this.serverDefaultVersion = serverDefaultVersion;
    }

    public String getServerDefaultVersion() {
        return serverDefaultVersion;
    }

    public void addInstance(Object instance){
        addInstance(instance,getRequestMappingName(instance.getClass()),new ClassFileMethodToParameterNamesFunction(),annotationMethodToMethodNameFunction);
    }

    public void addInstance(Object instance,String requestMappingName,Function<Method,String[]> methodToParameterNamesFunction){
        addInstance(instance,requestMappingName,methodToParameterNamesFunction,annotationMethodToMethodNameFunction);
    }

    public void addInstance(Object instance,String requestMappingName,Function<Method,String[]> methodToParameterNamesFunction,Function<Method,String> methodToNameFunction){
        if(instance instanceof RpcClient.Proxy){
            return;
        }
        String version = RpcServerInstance.getVersion(instance.getClass(), serverDefaultVersion);
        instanceMap.put(instance, new Instance(instance,requestMappingName,version,methodToParameterNamesFunction,methodToNameFunction,methodOverwriteCheck));
        logger.info("addInstance({}, {}, {})",
                RpcServerInstance.getServerInstanceKey(requestMappingName,version),
                instance.getClass().getSimpleName(),
                methodToParameterNamesFunction.getClass().getSimpleName());
    }

    public boolean existInstance(Object instance){
        return instanceMap.containsKey(instance);
    }

    @Override
    public String getProtocolName() {
        return RpcVersion.CURRENT_VERSION.getText();
    }

    @Override
    public boolean canSupport(ByteBuf msg) {
        return RpcVersion.CURRENT_VERSION.isSupport(msg);
    }

    @Override
    public void addPipeline(Channel channel) throws Exception {
        RpcServerChannelHandler rpcServerHandler = new RpcServerChannelHandler();
        rpcServerHandler.getAopList().addAll(rpcServerAopList);
        for (Instance instance : instanceMap.values()) {
            rpcServerHandler.addRpcServerInstance(instance.requestMappingName,instance.version,
                    instance.checkGetRpcServerInstance());
        }
        ChannelPipeline pipeline = channel.pipeline();
        pipeline.addLast(new RpcDecoder(messageMaxLength));
        pipeline.addLast(new RpcEncoder());
        pipeline.addLast(rpcServerHandler);
    }

    @Override
    public int getOrder() {
        return 200;
    }

    @Override
    public <T extends AbstractNettyServer> void onServerStart(T server) throws Exception {
        Collection list = application.getBeanForAnnotation(Protocol.RpcService.class);
        rpcServerAopList.clear();
        rpcServerAopList.addAll(application.getBeanForType(RpcServerAop.class));

        for(Object serviceImpl : list){
            if(existInstance(serviceImpl)){
                continue;
            }
            addInstance(serviceImpl);
        }
        addInstancePlugins();
        for (RpcServerAop rpcServerAop : rpcServerAopList) {
            rpcServerAop.onInitAfter(this);
        }
        if(methodOverwriteCheck){
            List<Exception> exceptionList = new ArrayList<>();
            for (Instance instance : instanceMap.values()) {
                try {
                    instance.checkGetRpcServerInstance();
                }catch (Exception e){
                    exceptionList.add(e);
                }
            }
            if(!exceptionList.isEmpty()){
                StringJoiner joiner = new StringJoiner("\n\n");
                int i = 1;
                for (Exception exception : exceptionList) {
                    joiner.add("["+i+"] "+exception.getLocalizedMessage());
                    i++;
                }
                throw new UnsupportedOperationException("serverMethodOverwriteCheckList: \n" + joiner);
            }
        }
    }

    @Override
    public <T extends AbstractNettyServer> void onServerStop(T server) throws Exception {

    }

    /**
     * Add an instance of the extension
     */
    protected void addInstancePlugins(){
        //The RPC basic command service is enabled by default
        addInstance(new RpcCommandServiceImpl());
        //Open DB service by default
        addInstance(new RpcDBServiceImpl());
    }

    protected ApplicationX getApplication() {
        return application;
    }

    public int getMessageMaxLength() {
        return messageMaxLength;
    }

    public void setMessageMaxLength(int messageMaxLength) {
        this.messageMaxLength = messageMaxLength;
    }

    static class Instance{
        private String requestMappingName;
        private String version;
        private RpcServerInstance rpcServerInstance;
        private Exception rpcServerInstanceException;
        Instance(Object instance, String requestMappingName, String version,Function<Method, String[]> methodToParameterNamesFunction,Function<Method,String> methodToNameFunction,boolean methodOverwriteCheck) {
            this.requestMappingName = requestMappingName;
            this.version = version;
            try {
                this.rpcServerInstance = new RpcServerInstance(instance, null, methodToParameterNamesFunction, methodToNameFunction,methodOverwriteCheck);
            }catch (Exception e){
                rpcServerInstanceException = e;
            }
        }

        public RpcServerInstance checkGetRpcServerInstance() throws Exception{
            if(rpcServerInstanceException != null){
                throw rpcServerInstanceException;
            }
            return rpcServerInstance;
        }

    }
}
