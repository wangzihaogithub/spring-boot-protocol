package com.github.netty.protocol;

import com.github.netty.core.ProtocolsRegister;
import com.github.netty.protocol.mqtt.*;
import com.github.netty.protocol.mqtt.config.FileResourceLoader;
import com.github.netty.protocol.mqtt.config.IResourceLoader;
import com.github.netty.protocol.mqtt.metrics.*;
import com.github.netty.protocol.mqtt.persistence.MemorySubscriptionsRepository;
import com.github.netty.protocol.mqtt.security.*;
import com.github.netty.protocol.mqtt.subscriptions.CTrieSubscriptionDirectory;
import com.github.netty.protocol.mqtt.subscriptions.ISubscriptionsDirectory;
import com.github.netty.protocol.mqtt.interception.BrokerInterceptor;
import com.github.netty.protocol.mqtt.interception.InterceptHandler;
import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import io.netty.channel.ChannelPipeline;
import io.netty.handler.codec.mqtt.MqttDecoder;
import io.netty.handler.codec.mqtt.MqttEncoder;
import io.netty.handler.codec.mqtt.MqttPublishMessage;
import io.netty.handler.timeout.IdleStateHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.text.ParseException;
import java.util.concurrent.TimeUnit;

/**
 * 物联网传输协议
 * @author acer01
 *  2018/12/5/005
 */
public class MqttProtocolsRegister implements ProtocolsRegister {
    private Logger logger = LoggerFactory.getLogger(MqttProtocolsRegister.class);
    public static final int ORDER = NRpcProtocolsRegister.ORDER + 100;

    private int messageMaxLength;
    private int nettyChannelTimeoutSeconds;
    private boolean enableMetrics = false;
    private String metricsLibratoEmail;
    private String metricsLibratoToken;
    private String metricsLibratoSource;

    private BytesMetricsCollector bytesMetricsCollector = new BytesMetricsCollector();
    private MessageMetricsCollector metricsCollector = new MessageMetricsCollector();

    private BrokerInterceptor interceptor = new BrokerInterceptor(1);
    private MoquetteIdleTimeoutHandler timeoutHandler = new MoquetteIdleTimeoutHandler();
    private MqttServerChannelHandler mqttHandler;
    private DropWizardMetricsHandler metricsHandler;
    private PostOffice dispatcher;

    public MqttProtocolsRegister() {
        this(8092,10);
    }

    public MqttProtocolsRegister(int messageMaxLength,int nettyChannelTimeoutSeconds) {
        this.messageMaxLength = messageMaxLength;
        this.nettyChannelTimeoutSeconds = nettyChannelTimeoutSeconds;
    }

    private IAuthorizatorPolicy initializeAuthorizatorPolicy(String aclFilePath) {
        IAuthorizatorPolicy authorizatorPolicy;
        if (aclFilePath != null && !aclFilePath.isEmpty()) {
            authorizatorPolicy = new DenyAllAuthorizatorPolicy();
            try {
                IResourceLoader resourceLoader = new FileResourceLoader();
                authorizatorPolicy = ACLFileParser.parse(resourceLoader.loadResource(aclFilePath));
            } catch (ParseException pex) {
                logger.error("Unable to parse ACL file. path=" + aclFilePath, pex);
            }
        } else {
            authorizatorPolicy = new PermitAllAuthorizatorPolicy();
        }
        return authorizatorPolicy;
    }

    public void internalPublish(MqttPublishMessage msg, final String clientId) {
        final int messageID = msg.variableHeader().packetId();
        logger.trace("Internal publishing message CId: {}, messageId: {}", clientId, messageID);
        dispatcher.internalPublish(msg);
    }

    public void addInterceptHandler(InterceptHandler interceptHandler) {
        logger.info("Adding MQTT message interceptor. InterceptorId={}", interceptHandler.getID());
        interceptor.addInterceptHandler(interceptHandler);
    }

    public void removeInterceptHandler(InterceptHandler interceptHandler) {
        logger.info("Removing MQTT message interceptor. InterceptorId={}", interceptHandler.getID());
        interceptor.removeInterceptHandler(interceptHandler);
    }

    @Override
    public String getProtocolName() {
        return "mqtt";
    }

    @Override
    public boolean canSupport(ByteBuf msg) {
        if(msg.readableBytes() < 9){
            return false;
        }

        if( msg.getByte(4) == 'M'
                &&  msg.getByte(5) == 'Q'
                &&  msg.getByte(6) == 'T'
                &&   msg.getByte(7) == 'T'){
            return true;
        }
        return false;
    }

    @Override
    public void register(Channel channel) throws Exception {
        ChannelPipeline pipeline = channel.pipeline();

        pipeline.addFirst("idleStateHandler", new IdleStateHandler(nettyChannelTimeoutSeconds, 0, 0));
        pipeline.addAfter("idleStateHandler", "idleEventHandler", timeoutHandler);
        // pipeline.addLast("logger", new LoggingHandler("Netty", LogLevel.ERROR));

        pipeline.addFirst("bytemetrics", new BytesMetricsHandler(bytesMetricsCollector));
        pipeline.addLast("autoflush", new AutoFlushHandler(1, TimeUnit.SECONDS));
        pipeline.addLast("decoder", new MqttDecoder(messageMaxLength));
        pipeline.addLast("encoder", MqttEncoder.INSTANCE);
        pipeline.addLast("metrics", new MessageMetricsHandler(metricsCollector));
        pipeline.addLast("messageLogger", new MQTTMessageLogger());

        //--可选
        if(isEnableMetrics()) {
            pipeline.addLast("wizardMetrics", metricsHandler);
        }
        //--可选

        pipeline.addLast("handler", mqttHandler);
    }

    @Override
    public int order() {
        return ORDER;
    }

    @Override
    public void onServerStart() throws Exception {
        IAuthorizatorPolicy authorizatorPolicy = initializeAuthorizatorPolicy(null);

        ISubscriptionsDirectory subscriptions = new CTrieSubscriptionDirectory(new MemorySubscriptionsRepository());
        SessionRegistry sessions = new SessionRegistry(subscriptions, new MemoryQueueRepository());
        dispatcher = new PostOffice(subscriptions, authorizatorPolicy, new MemoryRetainedRepository(), sessions,interceptor);
        mqttHandler = new MqttServerChannelHandler(new MQTTConnectionFactory(new BrokerConfiguration(), new AcceptAllAuthenticator(), sessions, dispatcher));

        if(isEnableMetrics()) {
            metricsHandler = new DropWizardMetricsHandler();
            metricsHandler.init(metricsLibratoEmail, metricsLibratoToken, metricsLibratoSource);
        }
    }

    public boolean isEnableMetrics() {
        return enableMetrics;
    }

    public void setEnableMetrics(boolean enableMetrics) {
        this.enableMetrics = enableMetrics;
    }

    public String getMetricsLibratoEmail() {
        return metricsLibratoEmail;
    }

    public void setMetricsLibratoEmail(String metricsLibratoEmail) {
        this.metricsLibratoEmail = metricsLibratoEmail;
    }

    public String getMetricsLibratoToken() {
        return metricsLibratoToken;
    }

    public void setMetricsLibratoToken(String metricsLibratoToken) {
        this.metricsLibratoToken = metricsLibratoToken;
    }

    public String getMetricsLibratoSource() {
        return metricsLibratoSource;
    }

    public void setMetricsLibratoSource(String metricsLibratoSource) {
        this.metricsLibratoSource = metricsLibratoSource;
    }

    @Override
    public void onServerStop() throws Exception {
        if(metricsCollector != null) {
            MessageMetrics metrics = metricsCollector.computeMetrics();
            logger.info("Metrics messages[read={}, write={}]", metrics.messagesRead(),metrics.messagesWrote());
        }
        if(bytesMetricsCollector != null){
            BytesMetrics bytesMetrics = bytesMetricsCollector.computeMetrics();
            logger.info("Metrics bytes[read={}, write={}]",  bytesMetrics.readBytes(), bytesMetrics.wroteBytes());
        }
        if(interceptor != null) {
            interceptor.stop();
        }
    }
}
