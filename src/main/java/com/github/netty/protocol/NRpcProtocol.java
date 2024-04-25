package com.github.netty.protocol;

import com.github.netty.annotation.NRpcMethod;
import com.github.netty.annotation.NRpcService;
import com.github.netty.core.AbstractNettyServer;
import com.github.netty.core.AbstractProtocol;
import com.github.netty.core.util.*;
import com.github.netty.protocol.nrpc.*;
import com.github.netty.protocol.nrpc.codec.DataCodecUtil;
import com.github.netty.protocol.nrpc.codec.RpcDecoder;
import com.github.netty.protocol.nrpc.codec.RpcEncoder;
import com.github.netty.protocol.nrpc.service.RpcCommandServiceImpl;
import com.github.netty.protocol.nrpc.service.RpcDBServiceImpl;
import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import io.netty.channel.ChannelPipeline;

import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.Executor;
import java.util.function.Function;
import java.util.function.Supplier;

import static com.github.netty.protocol.nrpc.RpcServerChannelHandler.getRequestMappingName;

/**
 * Internal RPC protocol registry
 * <p>
 * ACK flag : (0=Don't need, 1=Need)
 * Request Packet (note:  1 = request type)
 * -+------8B--------+--1B--+--1B--+------4B------+-----4B-----+-----4B-----+------1B--------+-----length-----+------1B-------+---length----+-----4B------+-------length-------------+
 * | header/version | type | ACK   | total length | Request ID | timeout/ms | service length | service name   | method length | method name | data length |         data             |
 * |   NRPC/010     |  1   | 1    |     55       |     1       |     1000   |       8        | "/sys/user"    |      7        |  getUser    |     24      | {"age":10,"name":"wang"} |
 * -+----------------+------+------+--------------+------------+------------+----------------+----------------+---------------+-------------+-------------+--------------------------+
 * <p>
 * <p>
 * Response Packet (note: 2 = response type)
 * -+------8B--------+--1B--+--1B--+------4B------+-----4B-----+---2B---+--------1B------+--length--+---1B---+-----4B------+----------length----------+
 * | header/version | type | ACK   | total length | Request ID | status | message length | message  | encode | data length |         data             |
 * |   NRPC/010     |  2   | 0    |     35       |     1      |  200   |       2        |  ok      | 1      |     24      | {"age":10,"name":"wang"} |
 * -+----------------+------+------+--------------+------------+--------+----------------+----------+--------+-------------+--------------------------+
 * <p>
 * <p>
 * -+------2B-------+--1B--+----1B----+-----8B-----+------1B-----+----------------dynamic---------------------+-------dynamic------------+
 * | packet length | type | ACK flag |   version  | Fields size |                Fields                      |          Body            |
 * |      76       |  1   |   1      |   NRPC/201 |     2       | 11requestMappingName6/hello10methodName8sayHello  | {"age":10,"name":"wang"} |
 * -+---------------+------+----------+------------+-------------+--------------------------------------------+--------------------------+
 *
 * @author wangzihao
 * 2018/11/25/025
 */
public class NRpcProtocol extends AbstractProtocol {
    private final List<RpcServerAop> rpcServerAopList = new ArrayList<>();
    private final AnnotationMethodToMethodNameFunction annotationMethodToMethodNameFunction = new AnnotationMethodToMethodNameFunction(NRpcMethod.class);
    private final LoggerX logger = LoggerFactoryX.getLogger(getClass());
    private final ApplicationX application;
    private Supplier<Executor> executorSupplier;
    /**
     * Maximum message length per pass
     */
    private int messageMaxLength = 10 * 1024 * 1024;
    /**
     * Check the method of the same name (because the generalization parameter called allow inconsistent,
     * so the name of the method to ensure that each class is unique)
     */
    private boolean methodOverwriteCheck = true;
    private final Map<Object, Instance> instanceMap = new LinkedHashMap<>();
    private String serverDefaultVersion;

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

    public String getServerDefaultVersion() {
        return serverDefaultVersion;
    }

    public void setServerDefaultVersion(String serverDefaultVersion) {
        this.serverDefaultVersion = serverDefaultVersion;
    }

    public void addInstance(Object instance) {
        addInstance(instance, getRequestMappingName(instance.getClass()), new ClassFileMethodToParameterNamesFunction(), annotationMethodToMethodNameFunction);
    }

    public void addInstance(Object instance, String requestMappingName, Function<Method, String[]> methodToParameterNamesFunction) {
        addInstance(instance, requestMappingName, methodToParameterNamesFunction, annotationMethodToMethodNameFunction);
    }

    public void addInstance(Object instance, String requestMappingName, Function<Method, String[]> methodToParameterNamesFunction, Function<Method, String> methodToNameFunction) {
        if (instance instanceof RpcClient.Proxy) {
            return;
        }
        String version = RpcServerInstance.getVersion(instance.getClass(), serverDefaultVersion);
        Integer timeout = RpcServerInstance.getTimeout(instance.getClass());
        instanceMap.put(instance, new Instance(instance, requestMappingName, version, timeout, methodToParameterNamesFunction, methodToNameFunction, methodOverwriteCheck));
        logger.info("addInstance({}, {}, {})",
                RpcServerInstance.getServerInstanceKey(requestMappingName, version),
                instance.getClass().getSimpleName(),
                methodToParameterNamesFunction.getClass().getSimpleName());
    }

    public boolean existInstance(Object instance) {
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
    public void addPipeline(Channel channel, ByteBuf clientFirstMsg) throws Exception {
        super.addPipeline(channel, clientFirstMsg);
        RpcServerChannelHandler rpcServerHandler = new RpcServerChannelHandler();
        rpcServerHandler.setExecutorSupplier(executorSupplier);
        rpcServerHandler.getAopList().addAll(rpcServerAopList);
        for (Instance instance : instanceMap.values()) {
            rpcServerHandler.addRpcServerInstance(instance.requestMappingName, instance.version,
                    instance.checkGetRpcServerInstance());
        }
        ChannelPipeline pipeline = channel.pipeline();
        pipeline.addLast(new RpcDecoder(messageMaxLength));
        pipeline.addLast(new RpcEncoder());
        pipeline.addLast(rpcServerHandler);
    }

    public Supplier<Executor> getExecutorSupplier() {
        return executorSupplier;
    }

    public void setExecutorSupplier(Supplier<Executor> executorSupplier) {
        this.executorSupplier = executorSupplier;
    }

    @Override
    public int getOrder() {
        return 200;
    }

    @Override
    public <T extends AbstractNettyServer> void onServerStart(T server) throws Exception {
        Collection list = application.getBeanForAnnotation(NRpcService.class);
        rpcServerAopList.clear();
        rpcServerAopList.addAll(application.getBeanForType(RpcServerAop.class));

        for (Object serviceImpl : list) {
            if (existInstance(serviceImpl)) {
                continue;
            }
            addInstance(serviceImpl);
        }
        addInstancePlugins();
        for (RpcServerAop rpcServerAop : rpcServerAopList) {
            rpcServerAop.onInitAfter(this);
        }
        if (methodOverwriteCheck) {
            List<Exception> exceptionList = new ArrayList<>();
            for (Instance instance : instanceMap.values()) {
                try {
                    instance.checkGetRpcServerInstance();
                } catch (Exception e) {
                    exceptionList.add(e);
                }
            }
            if (!exceptionList.isEmpty()) {
                StringJoiner joiner = new StringJoiner("\n\n");
                int i = 1;
                for (Exception exception : exceptionList) {
                    joiner.add("[" + i + "] " + exception.getLocalizedMessage());
                    i++;
                }
                throw new UnsupportedOperationException("serverMethodOverwriteCheckList: \n" + joiner);
            }
        }
        logger.info("used codec = {}", DataCodecUtil.getDataCodec());
    }

    @Override
    public <T extends AbstractNettyServer> void onServerStop(T server) throws Exception {

    }

    /**
     * Add an instance of the extension
     */
    protected void addInstancePlugins() {
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

    static class Instance {
        private String requestMappingName;
        private String version;
        private Integer timeout;
        private RpcServerInstance rpcServerInstance;
        private Exception rpcServerInstanceException;

        Instance(Object instance, String requestMappingName, String version, Integer timeout, Function<Method, String[]> methodToParameterNamesFunction, Function<Method, String> methodToNameFunction, boolean methodOverwriteCheck) {
            this.requestMappingName = requestMappingName;
            this.version = version;
            this.timeout = timeout;
            try {
                this.rpcServerInstance = new RpcServerInstance(instance, null, version, timeout, methodToParameterNamesFunction, methodToNameFunction, methodOverwriteCheck);
            } catch (Exception e) {
                rpcServerInstanceException = e;
            }
        }

        public RpcServerInstance checkGetRpcServerInstance() throws Exception {
            if (rpcServerInstanceException != null) {
                throw rpcServerInstanceException;
            }
            return rpcServerInstance;
        }

    }
}
