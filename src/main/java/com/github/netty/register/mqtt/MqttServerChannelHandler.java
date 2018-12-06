package com.github.netty.register.mqtt;

import com.github.netty.core.AbstractChannelHandler;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.mqtt.MqttMessage;
import io.netty.handler.codec.mqtt.MqttMessageType;

/**
 *  开发一个MQTT库需要提供如下命令：
     Connect ：当一个TCP/IP套接字在服务器端和客户端连接建立时需要使用的命令。
     publish  ： 是由客户端向服务端发送，告诉服务器端自己感兴趣的Topic。每一个publishMessage 都会与一个Topic的名字联系在一起。
     pubRec:   是publish命令的响应，只不过使用了2级QoS协议。它是2级QoS协议的第二条消息
     pubRel:    是2级QoS协议的第三条消息
     publComp: 是2级QoS协议的第四条消息
     subscribe： 允许一个客户端注册自已感兴趣的Topic 名字，发布到这些Topic的消息会以publish Message的形式由服务器端发送给客户端。
     unsubscribe:  从客户端到服务器端，退订一个Topic。
     Ping： 有客户端向服务器端发送的“are you alive”的消息。
     disconnect：断开这个TCP/IP协议。
 * Created by acer01 on 2018/12/5/005.
 */
@ChannelHandler.Sharable
public class MqttServerChannelHandler extends AbstractChannelHandler<MqttMessage> {
    @Override
    protected void onMessageReceived(ChannelHandlerContext ctx, MqttMessage msg) throws Exception {
        MqttMessageType messageType = msg.fixedHeader().messageType();
        switch (messageType) {
            //客户端到服务端的连接请求
            case CONNECT:{
                break;
            }
            //服务端对连接请求的响应
            case CONNACK:{
                break;
            }
            //发布消息
            case PUBLISH:{
                break;
            }
            //对发布消息的回应
            case PUBACK:{
                break;
            }
            //收到发布消息（保证传输part1）
            case PUBREC:{
                break;
            }
            //释放发布消息（保证传输part2）
            case PUBREL:{
                break;
            }
            //完成发布消息（保证传输part3）
            case PUBCOMP:{
                break;
            }
            //客户端订阅请求
            case SUBSCRIBE:{
                break;
            }
            //订阅请求的回应
            case SUBACK:{
                break;
            }
            //停止订阅请求
            case UNSUBSCRIBE:{
                break;
            }
            //停止订阅请求响应
            case UNSUBACK:{
                break;
            }
            //Ping请求（保持连接）
            case PINGREQ:{
                break;
            }
            //Ping响应
            case PINGRESP:{
                break;
            }
            //客户端正在断开
            case DISCONNECT:{
                break;
            }
            default: {
                //保留字段 0或15 reserved
                break;
            }
        }
    }

}
