package com.github.netty.javadubbo;

import com.github.netty.StartupServer;
import com.github.netty.core.AbstractChannelHandler;
import com.github.netty.core.AbstractProtocol;
import com.github.netty.javadubbo.example.Serializer;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ByteToMessageDecoder;
import org.apache.dubbo.remoting.exchange.Response;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Function;

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
        private static final Charset UTF8 = Charset.forName("utf-8");
        // magic header.
        protected static final short MAGIC = (short) 0xdabb;
        protected static final byte MAGIC_0 = (byte) (MAGIC >>> 8);
        protected static final byte MAGIC_1 = (byte) MAGIC;
        // message flag.
        protected static final byte FLAG_REQUEST = (byte) 0x80;
        protected static final byte FLAG_TWOWAY = (byte) 0x40;
        protected static final byte FLAG_EVENT = (byte) 0x20;
        protected static final int SERIALIZATION_MASK = 0x1f;
        // header length.
        protected static final int HEADER_LENGTH = 16;

        public static final byte RESPONSE_WITH_EXCEPTION = 0;
        public static final byte RESPONSE_VALUE = 1;
        public static final byte RESPONSE_NULL_VALUE = 2;
        public static final byte RESPONSE_WITH_EXCEPTION_WITH_ATTACHMENTS = 3;
        public static final byte RESPONSE_VALUE_WITH_ATTACHMENTS = 4;
        public static final byte RESPONSE_NULL_VALUE_WITH_ATTACHMENTS = 5;

        /**
         * ok.
         */
        public static final byte OK = 20;

        /**
         * client side timeout.
         */
        public static final byte CLIENT_TIMEOUT = 30;

        /**
         * server side timeout.
         */
        public static final byte SERVER_TIMEOUT = 31;

        /**
         * channel inactive, directly return the unfinished requests.
         */
        public static final byte CHANNEL_INACTIVE = 35;

        /**
         * request format error.
         */
        public static final byte BAD_REQUEST = 40;

        /**
         * response format error.
         */
        public static final byte BAD_RESPONSE = 50;

        /**
         * service not found.
         */
        public static final byte SERVICE_NOT_FOUND = 60;

        /**
         * service error.
         */
        public static final byte SERVICE_ERROR = 70;

        /**
         * internal server error.
         */
        public static final byte SERVER_ERROR = 80;

        /**
         * internal server error.
         */
        public static final byte CLIENT_ERROR = 90;

        /**
         * server side threadpool exhausted and quick return.
         */
        public static final byte SERVER_THREADPOOL_EXHAUSTED_ERROR = 100;

        @Override
        public boolean canSupport(ByteBuf buffer) {
            return buffer.readableBytes() >= 2
                    && buffer.getByte(0) == MAGIC_0
                    && buffer.getByte(1) == MAGIC_1;
        }

        @Override
        public void addPipeline(Channel channel, ByteBuf clientFirstMsg) throws Exception {
            channel.pipeline().addLast(new DubboDecoder());
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

        static class DubboDecoder extends ByteToMessageDecoder {
            enum State {
                READ_INITIAL,
                READ_HEADER
            }

            // request and serialization flag.
            private byte flag;
            private byte status;
            private long requestId;
            // 8 - 1-request/0-response
            private byte type;
            private int bodyLength;
            private State state = State.READ_INITIAL;

            private void end() {
                this.flag = 0;
                this.status = 0;
                this.requestId = 0;
                this.type = 0;
                this.bodyLength = 0;
                this.state = State.READ_INITIAL;
            }

            private void header(ByteBuf buffer) {
                // request and serialization flag.
                this.flag = buffer.getByte(2);
                this.status = buffer.getByte(3);
                this.requestId = buffer.getLong(4);
                // 8 - 1-request/0-response
                this.type = buffer.getByte(8);
                this.bodyLength = buffer.getInt(12);
                buffer.skipBytes(HEADER_LENGTH);
                this.state = State.READ_HEADER;
            }

            private Object body(ByteBuf buffer) throws IOException, ClassNotFoundException {
                try {
                    boolean flagResponse = (flag & FLAG_REQUEST) == 0;
                    boolean flagRequest = !flagResponse;
                    boolean flagEvent = flagResponse && (flag & FLAG_EVENT) != 0;
                    boolean flagTwoway = (flag & FLAG_TWOWAY) == 1;
                    boolean statusOk = flagRequest || status == Response.OK;
                    byte serializationProtoId = (byte) (flag & SERIALIZATION_MASK);
                    Object data;
                    if (flagResponse) {
                        // decode response.
                        if (status == OK) {
                            if (flagEvent) {
                                byte[] payload = Serializer.getPayload(buffer, bodyLength);
                                if (Serializer.isHeartBeat(payload, serializationProtoId)) {
                                    data = null;
                                } else {
                                    Serializer.ObjectInput input = Serializer.codeOfDeserialize(serializationProtoId, new ByteArrayInputStream(payload));
                                    data = input.readEvent();
                                }
                            } else {
                                Serializer.ObjectInput in = Serializer.codeOfDeserialize(serializationProtoId, buffer, bodyLength);

                                String dubboVersion = in.readUTF();

                                String path = in.readUTF();
                                String version = in.readUTF();
                                String methodName = in.readUTF();
                                String parameterTypesDesc = in.readUTF();
                                String app = in.readUTF();
                                Map<String, Object> attachments = in.readAttachments();

                                System.out.println("attachments = " + attachments);
                                // todo
//                                byte flag = buffer.readByte();
//                                switch (flag) {
//                                    case DubboCodec.RESPONSE_NULL_VALUE:
//                                        break;
//                                    case DubboCodec.RESPONSE_VALUE:
////                            handleValue(in);
//                                        break;
//                                    case DubboCodec.RESPONSE_WITH_EXCEPTION:
////                            handleException(in);
//                                        break;
//                                    case DubboCodec.RESPONSE_NULL_VALUE_WITH_ATTACHMENTS:
////                            handleAttachment(in);
//                                        break;
//                                    case DubboCodec.RESPONSE_VALUE_WITH_ATTACHMENTS:
////                            handleValue(in);
////                            handleAttachment(in);
//                                        break;
//                                    case DubboCodec.RESPONSE_WITH_EXCEPTION_WITH_ATTACHMENTS:
////                            handleException(in);
////                            handleAttachment(in);
//                                        break;
//                                    default:
//                                        throw new IOException("Unknown result flag, expect '0' '1' '2' '3' '4' '5', but received: " + flag);
//                                }

                                data = null;
                            }
                        } else {
                            Serializer.ObjectInput in = Serializer.codeOfDeserialize(serializationProtoId, buffer, bodyLength);
                            String app = in.readUTF();

                        }
                    } else {
                        // decode request.
                        Serializer.ObjectInput in = Serializer.codeOfDeserialize(serializationProtoId, buffer, bodyLength);

                        String dubboVersion = in.readUTF();

                        String path = in.readUTF();
                        String version = in.readUTF();

                        // Do provider-level payload checks.
                        String methodName = in.readUTF();
                        String parameterTypesDesc = in.readUTF();
                        String app = in.readUTF();
                        Map<String, Object> attachments = in.readAttachments();

                        // todo
//                        byte flag = buffer.readByte();
//                        switch (flag) {
//                            case DubboCodec.RESPONSE_NULL_VALUE:
//                                break;
//                            case DubboCodec.RESPONSE_VALUE:
////                            handleValue(in);
//                                break;
//                            case DubboCodec.RESPONSE_WITH_EXCEPTION:
////                            handleException(in);
//                                break;
//                            case DubboCodec.RESPONSE_NULL_VALUE_WITH_ATTACHMENTS:
////                            handleAttachment(in);
//                                break;
//                            case DubboCodec.RESPONSE_VALUE_WITH_ATTACHMENTS:
////                            handleValue(in);
////                            handleAttachment(in);
//                                break;
//                            case DubboCodec.RESPONSE_WITH_EXCEPTION_WITH_ATTACHMENTS:
////                            handleException(in);
////                            handleAttachment(in);
//                                break;
//                            default:
//                                throw new IOException("Unknown result flag, expect '0' '1' '2' '3' '4' '5', but received: " + flag);
//                        }
                        data = null;
                        System.out.println("attachments = " + attachments);
                    }

                    return null;
                } finally {
                    end();
                }
            }


//            boolean flagResponse = (flag & FLAG_REQUEST) == 0;
//            boolean flagRequest = !flagResponse;
//            boolean flagEvent = flagResponse && (flag & FLAG_EVENT) != 0;
//            boolean flagTwoway = (flag & FLAG_TWOWAY) == 1;

            @Override
            protected void decode(ChannelHandlerContext ctx, ByteBuf buffer, List<Object> out) throws Exception {
                boolean hasNext;
                do {
                    switch (state) {
                        case READ_INITIAL: {
                            if (buffer.readableBytes() >= HEADER_LENGTH) {
                                header(buffer);
                                hasNext = buffer.isReadable();
                            } else {
                                hasNext = false;
                            }
                            break;
                        }
                        case READ_HEADER: {
                            if (buffer.readableBytes() >= bodyLength) {
                                Object body = body(buffer);
                                if (body != null) {
                                    out.add(body);
                                }
                                hasNext = buffer.isReadable();
                            } else {
                                hasNext = false;
                            }
                            break;
                        }
                        default: {
                            hasNext = false;
                            break;
                        }
                    }
                } while (hasNext);
            }
        }
    }
}