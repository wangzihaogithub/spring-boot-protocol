package com.github.netty.springmyprotocol;

import com.github.netty.core.AbstractChannelHandler;
import com.github.netty.core.AbstractProtocol;
import com.github.netty.springboot.EnableNettyEmbedded;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.stereotype.Component;

import java.net.URL;
import java.nio.charset.Charset;
import java.util.Objects;

@EnableNettyEmbedded
@SpringBootApplication
public class SpringMyProtocolBootstrap {
    private static final URL CONFIG_URL = SpringMyProtocolBootstrap.class.getResource(
            "/springmyprotocol/application.yaml");

    public static void main(String[] args) {
        System.getProperties().put("spring.config.location", CONFIG_URL.toString());
        SpringApplication.run(SpringMyProtocolBootstrap.class, args);
    }

    @Component
    public static class MyProtocol extends AbstractProtocol {
        public static final String HANDSHAKE_KEY = "开启吧!我的自定义协议";
        private static final Charset UTF8 = Charset.forName("utf-8");

        @Override
        public boolean canSupport(ByteBuf msg) {
            String reqString = msg.toString(UTF8);
            return Objects.equals(HANDSHAKE_KEY, reqString);
        }

        @Override
        public void addPipeline(Channel channel, ByteBuf clientFirstMsg) throws Exception {
            channel.pipeline().addLast(new AbstractChannelHandler<ByteBuf, ByteBuf>() {
                private boolean connection;

                @Override
                protected void onMessageReceived(ChannelHandlerContext ctx, ByteBuf msg) throws Exception {
                    if (connection) {
                        System.out.println("收到! = " + msg.toString(UTF8));
                    } else {
                        ctx.writeAndFlush(Unpooled.copiedBuffer("握手完毕! 请开始你的表演~", UTF8));
                        connection = true;
                    }
                }
            });
        }
    }
}
