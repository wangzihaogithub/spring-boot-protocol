package com.github.netty.javadubbo;

import com.github.netty.StartupServer;
import com.github.netty.core.AbstractChannelHandler;
import com.github.netty.core.AbstractNettyClient;
import com.github.netty.core.AbstractProtocol;
import com.github.netty.protocol.dubbo.*;
import com.github.netty.protocol.dubbo.packet.BodyRequest;
import com.github.netty.protocol.dubbo.packet.BodyResponse;
import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.socket.SocketChannel;

import java.net.InetSocketAddress;
import java.util.Map;
import java.util.function.Supplier;

/**
 * dubbo proxy server
 * 访问 http://localhost:8080/index.html 可以看效果
 * <p>
 * byte 16
 * 0-1 magic code
 * 2 flag
 * 8 - 1-request/0-response
 * 7 - two way
 * 6 - heartbeat
 * 1-5 serialization id
 * 3 status
 * 20 ok
 * 90 error?
 * 4-11 id (long)
 * 12 -15 datalength
 *
 * @author wangzihao
 */
public class DubboProxyBootstrap {

    public static void main(String[] args) {
        StartupServer server = new StartupServer(20880);
        server.addProtocol(new MyProtocol());
        server.start();
    }

    public static class MyProtocol extends AbstractProtocol {
        @Override
        public boolean canSupport(ByteBuf buffer) {
            return DubboDecoder.isDubboProtocol(buffer);
        }

        @Override
        public void addPipeline(Channel channel, ByteBuf clientFirstMsg) throws Exception {
            channel.pipeline().addLast(new DubboDecoder());
            channel.pipeline().addLast(new AbstractChannelHandler<DubboPacket, ByteBuf>() {

                @Override
                protected void onMessageReceived(ChannelHandlerContext ctx, DubboPacket packet) throws Exception {
                    Header header = packet.getHeader();
                    Body body = packet.getBody();
                    if (body instanceof BodyRequest) {
                        Map<String, Object> attachments = ((BodyRequest) body).getAttachments();
                        Object app = attachments.get("");

                    } else if (body instanceof BodyResponse) {
                        Map<String, Object> attachments = ((BodyResponse) body).getAttachments();
                    }

                    DubboClient client = new DubboClient();
                    client.handler(new ChannelInitializer() {
                        @Override
                        protected void initChannel(Channel channel) throws Exception {

                        }
                    });
                    client.connect(new InetSocketAddress("127.0.0.1", 20880));
                    // 透传
                    SocketChannel clientChannel = client.getChannel();
                    clientChannel.write(header.bytes());
                    clientChannel.write(body.bytes());
                }
            });
        }
    }
}