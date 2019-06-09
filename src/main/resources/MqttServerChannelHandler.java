//package com.github.netty.supportPipeline.mqtt;
//
//import com.github.netty.core.AbstractChannelHandler;
//import com.github.netty.core.util.NamespaceUtil;
//import io.netty.buffer.ByteBuf;
//import io.netty.channel.ChannelHandlerContext;
//import io.netty.handler.codec.mqtt.*;
//
//import java.util.List;
//
///**
// *  开发一个MQTT库需要提供如下命令：
//     Connect ：当一个TCP/IP套接字在服务器端和客户端连接建立时需要使用的命令。
//     publish  ： 是由客户端向服务端发送，告诉服务器端自己感兴趣的Topic。每一个publishMessage 都会与一个Topic的名字联系在一起。
//     pubRec:   是publish命令的响应，只不过使用了2级QoS协议。它是2级QoS协议的第二条消息
//     pubRel:    是2级QoS协议的第三条消息
//     publComp: 是2级QoS协议的第四条消息
//     subscribe： 允许一个客户端注册自已感兴趣的Topic 名字，发布到这些Topic的消息会以publish Message的形式由服务器端发送给客户端。
//     unsubscribe:  从客户端到服务器端，退订一个Topic。
//     Ping： 有客户端向服务器端发送的“are you alive”的消息。
//     disconnect：断开这个TCP/IP协议。
// * Created by acer01 on 2018/12/5/005.
// */
//public class MqttServerChannelHandler extends AbstractChannelHandler<MqttMessage> {
//    private ChannelHandlerContext context;
//    private MqttExchanger exchanger;
//
//    public MqttServerChannelHandler(MqttExchanger exchanger) {
//        super(true);
//        this.exchanger = exchanger;
//        logger.info(NamespaceUtil.newIdName(getClass())+"====");
//    }
//
//    /**
//     * 服务端事件 - 有链接接入
//     * @param request
//     * @return
//     */
//    private MqttConnAckMessage onConnect(MqttConnectMessage request){
//        MqttQoS qoS = request.fixedHeader().qosLevel();
//        MqttConnectPayload payload = request.payload();
//        MqttConnectVariableHeader variableHeader = request.variableHeader();
//
//        MqttFixedHeader responseFixedHeader = new MqttFixedHeader(MqttMessageType.CONNACK, false, qoS, false, 0);
//        MqttConnAckVariableHeader responseVariableHeader = new MqttConnAckVariableHeader(MqttConnectReturnCode.CONNECTION_ACCEPTED,true);
//        MqttConnAckMessage response = new MqttConnAckMessage(responseFixedHeader,responseVariableHeader);
//
//        exchanger.addSession(payload.clientIdentifier(),payload.userName(),
//                payload.passwordInBytes(),payload.willMessageInBytes(),
//                payload.willTopic(),MqttQoS.valueOf(variableHeader.willQos()),
//                context.channel());
//        return response;
//    }
//
//    /**
//     * 客户端事件 - 链接应答
//     * @param request
//     * @return
//     */
//    private MqttMessage onConnectAck(MqttConnAckMessage request){
//        return null;
//    }
//
//    /**
//     * 服务端事件 - 有新消息发布
//     * @param request
//     * @return
//     */
//    private MqttPubAckMessage onPublish(MqttPublishMessage request){
//        MqttQoS qoS = request.fixedHeader().qosLevel();
//        if(qoS != MqttQoS.AT_LEAST_ONCE && qoS != MqttQoS.EXACTLY_ONCE){
//            return null;
//        }
//
//        MqttFixedHeader responseFixedHeader = new MqttFixedHeader(MqttMessageType.PUBACK, false, qoS, false, 0);
//        MqttMessageIdVariableHeader responseVariableHeader = MqttMessageIdVariableHeader.from(request.variableHeader().packetId());
//        MqttPubAckMessage response = new MqttPubAckMessage(responseFixedHeader, responseVariableHeader);
//
//        ByteBuf payload = request.payload();
//        byte[] data = new byte[payload.readableBytes()];
//        payload.readBytes(data);
//        exchanger.addPublish(context.channel(),request.variableHeader().packetId(),request.variableHeader().topicName(),data);
//        return response;
//    }
//
//    /**
//     * 客户端事件 - 接到服务端的发布结果
//     * @param request
//     * @return
//     */
//    private MqttMessage onPubAck(MqttPubAckMessage request){
//
//        return null;
//    }
//
//    /**
//     * 服务端事件 - 2级QOS 接到客户端对发布结果的回应
//     * @param request
//     * @return
//     */
//    private MqttMessage onPubrec(MqttMessage request){
//        MqttFixedHeader mqttFixedHeader = new MqttFixedHeader(MqttMessageType.PUBREL,false,request.fixedHeader().qosLevel(),false,0);
//        MqttMessageIdVariableHeader responseVariableHeader = null;
//        if(request.variableHeader() instanceof MqttMessageIdVariableHeader){
//            responseVariableHeader = MqttMessageIdVariableHeader.from(((MqttMessageIdVariableHeader) request.variableHeader()).messageId());
//        }
//        MqttPubAckMessage mqttPubAckMessage = new MqttPubAckMessage(mqttFixedHeader,responseVariableHeader);
//        return mqttPubAckMessage;
//    }
//
//    /**
//     * 客户端事件 - 2级QOS 释放发布,将不再次重复进行发布
//     * @param request
//     * @return
//     */
//    private MqttMessage onPubrel(MqttMessage request){
//
//        return null;
//    }
//
//    /**
//     * 服务端事件 - 2级QOS 接到确认客户端释放完毕 ()
//     * @param request
//     * @return
//     */
//    private MqttMessage onPubcomp(MqttMessage request){
//        return null;
//    }
//
//    /**
//     * 服务端事件 - 有客户端订阅
//     * @param request
//     * @return
//     */
//    private MqttSubAckMessage onSubscribe(MqttSubscribeMessage request){
//        MqttMessageIdVariableHeader requestHeader = request.variableHeader();
//        List<MqttTopicSubscription> subscriptions = request.payload().topicSubscriptions();
//        int[] grantedQoSLevels = new int[subscriptions.size()];
//        int i = 0;
//        for(MqttTopicSubscription topicSubscription : subscriptions){
//            grantedQoSLevels[i] = topicSubscription.qualityOfService().value();
//            i++;
//        }
//
//        MqttSubAckPayload responsePayload = new MqttSubAckPayload(grantedQoSLevels);
//        MqttFixedHeader responseFixedHeader = new MqttFixedHeader(MqttMessageType.SUBACK,false, request.fixedHeader().qosLevel(), false, 0);
//        MqttMessageIdVariableHeader responseVariableHeader = MqttMessageIdVariableHeader.from(requestHeader.messageId());
//        MqttSubAckMessage response = new MqttSubAckMessage(responseFixedHeader,responseVariableHeader,responsePayload);
//
//        exchanger.addSubscribe(context.channel(),subscriptions);
//        return response;
//    }
//
//    /**
//     * 客户端事件 - 接到服务端的订阅成功应答
//     * @param request
//     * @return
//     */
//    private MqttMessage onSubAck(MqttSubAckMessage request){
//
//        return null;
//    }
//
//    /**
//     * 服务端事件 - 接到停止订阅
//     * @param request
//     * @return
//     */
//    private MqttUnsubAckMessage onUnsubscribe(MqttUnsubscribeMessage request){
//        exchanger.removeSubscribe(context.channel(),request.payload().topics());
//
//        MqttFixedHeader responseFixedHeader = new MqttFixedHeader(MqttMessageType.UNSUBACK, false, request.fixedHeader().qosLevel(), false, 0);
//        MqttMessageIdVariableHeader responseVariableHeader = MqttMessageIdVariableHeader.from(request.variableHeader().messageId());
//        MqttUnsubAckMessage response = new MqttUnsubAckMessage(responseFixedHeader,responseVariableHeader);
//        return response;
//    }
//
//    /**
//     * 客户端事件- 停止订阅的应答
//     * @param request
//     * @return
//     */
//    private MqttMessage onUnsubAck(MqttUnsubAckMessage request){
//        return null;
//    }
//
//    /**
//     * 服务端事件- 客户端的ping
//     * @param request
//     * @return
//     */
//    private MqttMessage onPingReq(MqttMessage request){
//        MqttMessageIdVariableHeader responseVariableHeader = null;
//        if(request.variableHeader() instanceof MqttMessageIdVariableHeader){
//            responseVariableHeader = MqttMessageIdVariableHeader.from(((MqttMessageIdVariableHeader) request.variableHeader()).messageId());
//        }
//        MqttFixedHeader responseFixedHeader = new MqttFixedHeader(MqttMessageType.PINGRESP, false,MqttQoS.AT_MOST_ONCE, false, 0);
//        MqttMessage response = new MqttMessage(responseFixedHeader,responseVariableHeader);
//        return response;
//    }
//
//    /**
//     * 客户端事件- ping响应
//     * @param request
//     * @return
//     */
//    private MqttMessage onPingResp(MqttMessage request){
//        return null;
//    }
//
//    /**
//     * 服务端事件- 客户端请求断开
//     * @param request
//     * @return
//     */
//    private MqttMessage onDisconnect(MqttMessage request){
//        return null;
//    }
//
//    @Override
//    protected void onMessageReceived(ChannelHandlerContext ctx, MqttMessage request) throws Exception {
//        this.context = ctx;
//        MqttMessage responseMessage = null;
//        switch (request.fixedHeader().messageType()) {
//            //客户端到服务端的连接请求
//            case CONNECT:{
//                MqttConnAckMessage response = onConnect((MqttConnectMessage) request);
//                responseMessage = response;
//                break;
//            }
//            //服务端对连接请求的响应
//            case CONNACK:{
//                MqttMessage response = onConnectAck((MqttConnAckMessage) request);
//                responseMessage = response;
//                break;
//            }
//            //发布消息
//            case PUBLISH:{
//                MqttPubAckMessage response = onPublish((MqttPublishMessage) request);
//                responseMessage = response;
//                break;
//            }
//            //对发布消息的回应
//            case PUBACK:{
//                MqttMessage response = onPubAck((MqttPubAckMessage) request);
//                responseMessage = response;
//                break;
//            }
//            //收到发布消息（保证传输part1）
//            case PUBREC:{
//                MqttMessage response = onPubrec(request);
//                responseMessage = response;
//                break;
//            }
//            //释放发布消息（保证传输part2）
//            case PUBREL:{
//                MqttMessage response = onPubrel(request);
//                responseMessage = response;
//                break;
//            }
//            //完成发布消息（保证传输part3）
//            case PUBCOMP:{
//                MqttMessage response = onPubcomp(request);
//                responseMessage = response;
//                break;
//            }
//            //客户端订阅请求
//            case SUBSCRIBE:{
//                MqttSubAckMessage response = onSubscribe((MqttSubscribeMessage) request);
//                responseMessage = response;
//                break;
//            }
//            //订阅请求的回应
//            case SUBACK:{
//                MqttMessage response = onSubAck((MqttSubAckMessage) request);
//                responseMessage = response;
//                break;
//            }
//            //停止订阅请求
//            case UNSUBSCRIBE:{
//                MqttUnsubAckMessage response = onUnsubscribe((MqttUnsubscribeMessage) request);
//                responseMessage = response;
//                break;
//            }
//            //停止订阅请求响应
//            case UNSUBACK:{
//                MqttMessage response = onUnsubAck((MqttUnsubAckMessage) request);
//                responseMessage = response;
//                break;
//            }
//            //Ping请求（保持连接）
//            case PINGREQ:{
//                MqttMessage response = onPingReq(request);
//                responseMessage = response;
//                break;
//            }
//            //Ping响应
//            case PINGRESP:{
//                MqttMessage response = onPingResp(request);
//                responseMessage = response;
//                break;
//            }
//            //客户端正在断开
//            case DISCONNECT:{
//                MqttMessage response = onDisconnect(request);
//                responseMessage = response;
//                break;
//            }
//            default: {
//                //保留字段 0或15 reserved
//                break;
//            }
//        }
//
//        if(responseMessage != null){
//            ctx.writeAndFlush(responseMessage);
//        }
//    }
//
//    @Override
//    public void channelRegistered(ChannelHandlerContext ctx) throws Exception {
//        this.context = ctx;
//        logger.info("channelRegistered");
//    }
//
//    @Override
//    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
//        logger.info("channelInactive");
//    }
//
//    @Override
//    public void channelWritabilityChanged(ChannelHandlerContext ctx) throws Exception {
//        logger.info("channelWritabilityChanged");
//    }
//
//    @Override
//    public void channelActive(ChannelHandlerContext ctx) throws Exception {
//        logger.info("channelActive");
//    }
//
//    @Override
//    public void channelReadComplete(ChannelHandlerContext ctx) throws Exception {
//        logger.info("channelReadComplete");
//    }
//}
