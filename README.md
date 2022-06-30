# Spring-boot-protocol (用Netty实现)

将springboot的WebServer改为了NettyTcpServer, 为使用者扩充了网络编程的能力.

![](https://user-images.githubusercontent.com/18204507/68989252-9d871a80-087e-11ea-96e1-20c12689c12a.png)

多协议服务器, Springboot协议扩展包, 允许单端口提供多协议服务.其中内置多种网络传输(标准与规范)的实现库, 轻松添加或扩展协议. 例: HttpServlet, RPC, MQTT（物联网通讯协议）, Websocket, RTSP(流媒体协议), DNS（域名解析协议）,MYSQL协议.

    1.可以替代tomcat或jetty. 导包后一个@EnableNettyEmbedded注解即用. 
    
    2.支持http请求聚合, 然后用 select * from id in (httpRequestList). 示例：com.github.netty.http.example.HttpGroupByApiController.java
    
    3.支持h2c, 让nginx h2接收浏览器请求，nginx或haproxy 卸载ssl加密后，h2c（明文h2）代理请求本服务器，不用配置，不用改Controller或servlet代码， 默认自动协商开启h2c。
    
    4.支持异步零拷贝。sendFile, mmap. 
        示例：com.github.netty.http.example.HttpZeroCopyController.java
        ((NettyOutputStream)servletResponse.getOutputStream()).write(new File("c://123.txt"));
        ((NettyOutputStream)servletResponse.getOutputStream()).write(MappedByteBuffer);
        com.github.netty.protocol.servlet.DefaultServlet#sendFile
        
    6.HttpServlet性能比tomcat的NIO2高出 25%/TPS。
        1. Netty的池化内存,减少了GC对CPU的消耗 
        2. Tomcat的NIO2, 注册OP_WRITE后,tomcat会阻塞用户线程等待, 并没有释放线程. 
        3. 与tomcat不同,支持两种IO模型,可供用户选择
    
    7.RPC性能略胜阿里巴巴的Dubbo(因为IO模型设计与dubbo不同，减少了线程切换), 使用习惯保持与springcloud相同
    
    8.Mysql,MQTT等协议可以在不依赖协议网关, 单机单端口同时支持N种协议 (例: HTTP,HTTP2,MQTT,Mysql,Websocket.)
    
    9.可以添加自定义传输协议. (例: 定长传输, 分隔符传输)

    10.开启Mysql协议,代理处理客户端与服务端的数据包, 记录mysql日志.
    /spring-boot-protocol/netty-mysql/zihaoapi.cn_3306-127.0.0.1_57998-packet.log
    
    {
        "timestamp":"2021-01-04 22:10:19",
        "sequenceId":0,
        "connectionId":8720,
        "handlerType":"backend",
        "clientCharset":"utf8_general_ci",
        "serverCharset":"latin1_swedish_ci",
        "packet":"ServerHandshakePacket,5.6.39-log,[AUTO_COMMIT]"
    },
    {
        "timestamp":"2021-01-04 22:10:19",
        "sequenceId":1,
        "connectionId":8720,
        "handlerType":"frontend",
        "clientCharset":"utf8_general_ci",
        "serverCharset":"latin1_swedish_ci",
        "packet":"ClientHandshakePacket,db1,root,{_runtime_version=12.0.2, _client_version=8.0.19, _client_license=GPL, _runtime_vendor=Oracle Corporation, _client_name=MySQL Connector/J}"
    },
    {
        "timestamp":"2021-01-04 22:10:19",
        "sequenceId":2,
        "connectionId":8720,
        "handlerType":"backend",
        "clientCharset":"utf8_general_ci",
        "serverCharset":"latin1_swedish_ci",
        "packet":"ServerOkPacket,[AUTO_COMMIT]"
    },
    {
        "timestamp":"2021-01-04 22:10:19",
        "sequenceId":0,
        "connectionId":8720,
        "handlerType":"frontend",
        "clientCharset":"utf8_general_ci",
        "serverCharset":"latin1_swedish_ci",
        "packet":"ClientQueryPacket,COM_QUERY,select * from order"
    },
    {
        "timestamp":"2021-01-04 22:10:19",
        "sequenceId":1,
        "connectionId":8720,
        "handlerType":"backend",
        "clientCharset":"utf8_general_ci",
        "serverCharset":"latin1_swedish_ci",
        "packet":"ServerColumnCountPacket,6"
    },
    {
        "timestamp":"2021-01-04 22:10:19",
        "sequenceId":2,
        "connectionId":8720,
        "handlerType":"backend",
        "clientCharset":"utf8_general_ci",
        "serverCharset":"latin1_swedish_ci",
        "packet":"ServerColumnDefinitionPacket,order_id"
    },
    

github地址 : https://github.com/wangzihaogithub


如果需要不依赖spring的servlet, 可以使用 https://github.com/wangzihaogithub/netty-servlet (支持文件零拷贝,可扩展底层通讯)


### 使用方法

#### 1.添加依赖, 在pom.xml中加入 [![Maven Central](https://maven-badges.herokuapp.com/maven-central/com.github.wangzihaogithub/spring-boot-protocol/badge.svg)](https://search.maven.org/search?q=g:com.github.wangzihaogithub%20AND%20a:spring-boot-protocol)

```xml
<!-- https://mvnrepository.com/artifact/com.github.wangzihaogithub/spring-boot-protocol -->
<dependency>
  <groupId>com.github.wangzihaogithub</groupId>
  <artifactId>spring-boot-protocol</artifactId>
  <version>2.2.11</version>
</dependency>
```
	
#### 2.开启netty容器

    @EnableNettyEmbedded//切换容器的注解
    @SpringBootApplication
    public class ExampleApplication {
    
        public static void main(String[] args) {
            SpringApplication.run(ExampleApplication.class, args);
        }
    }

#### 3.启动, 已经成功替换tomcat, 切换至 NettyTcpServer!
	2019-02-28 22:06:16.192  INFO 9096 --- [er-Boss-NIO-2-1] c.g.n.springboot.server.NettyTcpServer   : NettyTcpServer@1 start (port = 10004, pid = 9096, protocol = [my-protocol, http, nrpc, mqtt], os = windows 8.1) ...
	2019-02-28 22:06:16.193  INFO 9096 --- [           main] c.g.example.ProtocolApplication10004     : Started ProtocolApplication10004 in 2.508 seconds (JVM running for 3.247)    
---

#### 示例代码 -> [https://github.com/wangzihaogithub/netty-example](https://github.com/wangzihaogithub/netty-example "https://github.com/wangzihaogithub/netty-example")

#### 示例代码！ /src/test包下有使用示例代码 ->  [https://github.com/wangzihaogithub/spring-boot-protocol/tree/master/src/test](https://github.com/wangzihaogithub/spring-boot-protocol/tree/master/src/test "https://github.com/wangzihaogithub/spring-boot-protocol/tree/master/src/test")
 
##### 示例1. Springboot版,使用HTTP或websocket模块(使用springboot后,默认是开启http的)
        
        1. 引入http依赖
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-web</artifactId>
            <version>${spring-boot.version}</version>
        </dependency>
        
        2. 可选！如果需要websocket，可以引入这个包，否则可以不引入
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-websocket</artifactId>
            <version>${spring-boot.version}</version>
        </dependency>
        
        3.编写启动类
        
        // @EnableWebSocket // 如果引入了websocket，可以打这个注解开启
        @EnableNettyEmbedded//切换容器的注解
        @SpringBootApplication
        public class ExampleApplication {
          public static void main(String[] args) {
              SpringApplication.run(ExampleApplication.class, args);
          }
        }
        
        3. 启动后,控制台已经看到http协议出现了,开启成功! 可以用浏览器打开或websocket服务了.  protocol = [http, NRPC/218]
        2022-04-10 09:58:04.652  INFO 2716 --- [er-Boss-NIO-3-1] c.g.n.springboot.server.NettyTcpServer   : NettyTcpServer@1 start (version = 2.2.3, port = 8080, pid = 2716, protocol = [http, NRPC/218], os = windows 10) ...
        2022-04-10 09:58:04.673  INFO 2716 --- [           main] c.github.netty.ExampleApplication  : Started ExampleApplication in 2.235 seconds (JVM running for 3.807)
        
        4. 编写http代码
        @RestController
        @RequestMapping
        public class HttpController {
            private final Logger logger = LoggerFactory.getLogger(getClass());
        
            /**
             * 访问地址： http://localhost:8080/test/hello
             * @param name name
             * @return hi! 小明
             */
            @RequestMapping("/hello")
            public String hello(String name, @RequestParam Map query,
                                   @RequestBody(required = false) Map body,
                                HttpServletRequest request, HttpServletResponse response) {
                return "hi! " + name;
            }
        }
        
        5.如果引入了websocket，可以编写websocket服务端代码
        
        @Component
        public class WebsocketController extends AbstractWebSocketHandler implements WebSocketConfigurer, HandshakeInterceptor {
            public static final Map<String, NativeWebSocketSession> sessionMap = new ConcurrentHashMap<>();
            private static final Logger log = LoggerFactory.getLogger(WebsocketController.class);
        
            @Override
            public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
                log.info("应用启动时注册 websocket Controller {}", getClass());
                registry.addHandler(this, "/my-websocket")
                        .addInterceptors(this).setAllowedOrigins("*");
            }
        
            @Override
            public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response, WebSocketHandler wsHandler, Map<String, Object> attributes) throws Exception {
                log.info("握手前登录身份验证");
                attributes.put("request", request);
                attributes.put("response", response);
                attributes.put("wsHandler", wsHandler);
                return true;
            }
        
            @Override
            public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response, WebSocketHandler wsHandler, Exception exception) {
                log.info("握手后记录日志");
            }
        
            @Override
            public void afterConnectionEstablished(WebSocketSession session) throws Exception {
                log.info("建立链接保存会话");
                sessionMap.put(session.getId(), (NativeWebSocketSession) session);
            }
        
            @Override
            public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
                log.info("WebSocket关闭: " + status);
                sessionMap.remove(session.getId());
            }
        
            @Override
            protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
                log.info("接受来自客户端发送的文本信息: " + message.getPayload());
            }
        
            @Override
            protected void handleBinaryMessage(WebSocketSession session, BinaryMessage message) throws Exception {
                log.info("接受来自客户端发送的二进制信息: " + message.getPayload().toString());
            }
        
            @Override
            public void handleTransportError(WebSocketSession session, Throwable exception) throws Exception {
                log.info("WebSocket异常:异常信息: " + exception.toString(), exception);
            }
        
        }
        
##### 示例2. 纯java版,不引入springboot, 使用HTTP模块

        1. 引入依赖
        
        <!-- https://mvnrepository.com/artifact/com.github.wangzihaogithub/spring-boot-protocol -->
        <dependency>
          <groupId>com.github.wangzihaogithub</groupId>
          <artifactId>spring-boot-protocol</artifactId>
          <version>2.2.11</version>
        </dependency>

        2.编写代码
        
        public class HttpBootstrap {
            public static void main(String[] args) {
                StartupServer server = new StartupServer(8080);
                server.addProtocol(newHttpProtocol());
                server.start();
            }
            private static HttpServletProtocol newHttpProtocol() {
                ServletContext servletContext = new ServletContext();
                servletContext.setDocBase("D://demo", "/webapp"); // 静态资源文件夹(非必填,默认用临时目录)
                servletContext.addServlet("myHttpServlet", new com.github.netty.protocol.servlet.DefaultServlet())
                        .addMapping("/*");
                return new HttpServletProtocol(servletContext);
            }
        }
        
        2. 启动后,控制台已经看到http协议出现了,开启成功! 可以用浏览器打开或websocket服务了.  protocol = [http]
        10:10:26.026 [NettyX-Server-Boss-NIO-1-1] INFO com.github.netty.StartupServer - StartupServer@1 start (version = 2.2.3, port = 8080, pid = 6972, protocol = [http], os = windows 10) ...


##### 示例2. 纯java版,不引入springboot, 使用HTTP2 模块 (HTTP2不用配置, 自动开启h2c)

        1. 说明:  http2分为两个协议 http2加密(h2), http2明文(h2c)
        
        h2版本的协议是建立在TLS层之上的HTTP/2协议，这个标志被用在TLS应用层协议协商（TLS-ALPN）域和任何其它的TLS之上的HTTP/2协议。
        
        h2c版本是建立在明文的TCP之上的HTTP/2协议，这个标志被用在HTTP/1.1的升级协议头域和其它任何直接在TCP层之上的HTTP/2协议。

        想快速测试h2c可以用com.github.netty.protocol.servlet.http2.NettyHttp2Client 调用 http://localhost
        
        如果想带https, 需要开启SSL, HttpServletProtocol#setSslContext
         
        public static void main(String[] args) throws Exception {
            // h2c 调用测试
            NettyHttp2Client http2Client = new NettyHttp2Client("http://localhost")
                    .logger(LogLevel.INFO).awaitConnect();
            for (int i = 0; i < 1; i++) {
                DefaultFullHttpRequest request = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET,
                        "/test", Unpooled.EMPTY_BUFFER);
                http2Client.writeAndFlush(request).onSuccess(e -> {
                    System.out.println(e);
                });
            }
    
            List<NettyHttp2Client.H2Response> httpPromises = http2Client.flush().get();
            httpPromises.forEach(NettyHttp2Client.H2Response::close);
            Long closeTime = http2Client.close(true).get();
        }
        
##### 示例2. 纯java版,不引入springboot, 使用nprc(rpc-message)模块

        1. 引入依赖(需要大于2.2.7版本)
        
        <!-- https://mvnrepository.com/artifact/com.github.wangzihaogithub/spring-boot-protocol -->
        <dependency>
          <groupId>com.github.wangzihaogithub</groupId>
          <artifactId>spring-boot-protocol</artifactId>
          <version>2.2.11</version>
        </dependency>

        2.编写代码
        
        package com.github.netty.javanrpc.server;
    
         // rpc server demo
        public class RpcServerApplication {
            
            public static void main(String[] args) {
                StartupServer server = new StartupServer(80);
                server.addProtocol(newHttpProtocol());
                server.addProtocol(newRpcMessageProtocol());
                server.start();
            }
        
            private static NRpcProtocol newRpcMessageProtocol() {
                ApplicationX applicationX = new ApplicationX();
                applicationX.scanner(true,"com.github.netty.javanrpc.server")
                        .inject();
                return new NRpcProtocol(applicationX);
            }
        
            @ApplicationX.Component
            @NRpcService(value = "/demo", version = "1.0.0")
            public static class DemoService {
                public Map hello(String name) {
                    Map result = new LinkedHashMap();
                    result.put("name", name);
                    result.put("timestamp", System.currentTimeMillis());
                    return result;
                }
            }
        }
        
        
        // rpc client demo
        public class RpcClientApplication {
        
            public static void main(String[] args){
                RpcClient rpcClient = new RpcClient("localhost", 80);
                
                DemoClient demoClient = rpcClient.newInstance(DemoClient.class);
                DemoMessageClient demoMessageClient = rpcClient.newInstance(DemoMessageClient.class);
                DemoAsyncClient demoAsyncClient = rpcClient.newInstance(DemoAsyncClient.class);
                
                Map result = demoClient.hello("wang");
                System.out.println("result = " + result);
                demoAsyncClient.hello("wang").whenComplete((data, exception) -> {
                    System.out.println("data = " + data);
                    System.out.println("exception = " + exception);
                });
                // ...
            }
        
            @NRpcService(value = "/demo", version = "1.0.0", timeout = 2000)
            public interface DemoClient {
                Map hello(@NRpcParam("name") String name);
            }
            
            @NRpcService(value = "/demo", version = "1.0.0", timeout = 2000)
            public interface DemoAsyncClient {
                CompletableFuture<Map> hello(@NRpcParam("name") String name);
            }
         
            @NRpcService(value = "/demo", version = "1.0.0", timeout = 2000)
            public interface DemoMessageClient {
                // void is only send a message. not need to wait peer server for a reply
                void hello(@NRpcParam("name") String name);
            }
        }
            
        2. 启动后,控制台已经看到http协议出现了,开启成功! 可以运行客户端RpcClientApplication#main方法进行调用.  protocol = [nrpc]
        10:10:26.026 [NettyX-Server-Boss-NIO-1-1] INFO com.github.netty.StartupServer - StartupServer@1 start (version = 2.2.5, port = 8080, pid = 6972, protocol = [http, nrpc], os = windows 10) ...


##### 示例3. Springboot版,开启MQTT-Broker模块(需要手工开启), 注! 本项目是MQTT-Broker, 不是MQTT生产者与消费者
        
        1. 引入依赖
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-web</artifactId>
            <version>${spring-boot.version}</version>
        </dependency>
        
         <dependency>
              <groupId>com.github.wangzihaogithub</groupId>
              <artifactId>spring-boot-protocol</artifactId>
              <version>2.2.11</version>
        </dependency>
        
        2.编写启动类
        
        @EnableNettyEmbedded//切换容器的注解
        @SpringBootApplication
        public class ExampleApplication {
          public static void main(String[] args) {
              SpringApplication.run(ExampleApplication.class, args);
          }
        }
        
        3.修改application.yaml, 更多参数请看配置类: NettyProperties#Mqtt
        
            server:
              port: 8080
              netty:
                mqtt:
                  enabled: true
        
        4. 启动后,控制台已经看到mqtt协议出现了,开启成功! 可以用生产或消费者连服务了.  protocol = [http, NRPC/218, mqtt]
        2022-04-10 09:58:04.652  INFO 2716 --- [er-Boss-NIO-3-1] c.g.n.springboot.server.NettyTcpServer   : NettyTcpServer@1 start (version = 2.2.3, port = 8080, pid = 2716, protocol = [http, NRPC/218, mqtt], os = windows 10) ...
        2022-04-10 09:58:04.673  INFO 2716 --- [           main] c.github.netty.mqtt.MqttBrokerBootstrap  : Started MqttBrokerBootstrap in 2.235 seconds (JVM running for 3.807)
        
##### 示例4. Springboot版,开启MySQL模块(需要手工开启), 注! 本项目是Mysql-proxy, 可以改写mysql的请求相应
        1. 引入依赖
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-web</artifactId>
            <version>${spring-boot.version}</version>
        </dependency>
        
        2.编写启动类
        
        @EnableNettyEmbedded//切换容器的注解
        @SpringBootApplication
        public class ExampleApplication {
          public static void main(String[] args) {
              SpringApplication.run(ExampleApplication.class, args);
          }
        }
        
        3.修改application.yaml, 更多参数请看配置类: NettyProperties#Mysql  
        server:
          port: 8080
          netty:
            mysql:
              enabled: true
              mysql-host: 192.168.101.189
              mysql-port: 3306
              # 这个参数是用户自定义代理处理器 (非必填)
              backend-business-handler: com.github.netty.mysql.example.MysqlBackendHandler
              frontend-business-handler: com.github.netty.mysql.example.MysqlFrontendHandler
              # 开启日志可以会产生数据包日志文件 (异步批量写)
              proxy-log:
                enable: true
                
        4. 启动后,控制台已经看到mysql协议出现了,开启成功! 可以用mysql客户端连服务了.  protocol = [http, NRPC/218, mysql]
        2022-04-10 10:01:28.911  INFO 5800 --- [er-Boss-NIO-2-1] c.g.n.springboot.server.NettyTcpServer   : NettyTcpServer@1 start (version = 2.2.3, port = 8080, pid = 5800, protocol = [http, NRPC/218, mysql], os = windows 10) ...
        2022-04-10 10:01:28.919  INFO 5800 --- [           main] com.github.netty.mysql.MysqlBootstrap    : Started MysqlBootstrap in 2.661 seconds (JVM running for 3.838)

##### 示例5. Springboot版,使用用户自定义的基于TCP的协议

        1. 引入依赖
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-web</artifactId>
            <version>${spring-boot.version}</version>
        </dependency>
        
        2.编写启动类
        
        @EnableNettyEmbedded//切换容器的注解
        @SpringBootApplication
        public class ExampleApplication {
          public static void main(String[] args) {
              SpringApplication.run(ExampleApplication.class, args);
          }
        }
        
        3.编写自定义协议, 容器会自动注册实现了ProtocolHandler接口或ServerListener接口的类,AbstractProtocol 实现了这两个接口.
        
        @Component
        public class MyProtocol extends AbstractProtocol {
            private static final Charset UTF8 = Charset.forName("utf-8");
    
            @Override
            public boolean canSupport(ByteBuf msg) {
                String reqString = msg.toString(UTF8);
                return Objects.equals("开启吧!我的自定义协议", reqString);
            }
    
            @Override
            public void addPipeline(Channel channel) throws Exception {
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
    
        4. 编写客户端
        public class MyClient {
            public static void main(String[] args) throws IOException {
                Socket socket = new Socket();
                socket.connect(new InetSocketAddress("localhost", 8080));
        
                socket.getOutputStream().write("开启吧!我的自定义协议".getBytes(Charset.forName("utf-8")));
                socket.getOutputStream().flush();
        
                byte[] serverMsg = new byte[4096];
                socket.getInputStream().read(serverMsg);
                System.out.println("read = " + new String(serverMsg));
        
                for (int i = 0; i < 100; i++) {
                    String msg = "你好啊" + i + "先生.";
                    socket.getOutputStream().write(msg.getBytes(Charset.forName("utf-8")));
                    socket.getOutputStream().flush();
                }
                socket.getOutputStream().write("拜拜~".getBytes(Charset.forName("utf-8")));
                socket.close();
            }
        }
    
        5. 启动服务端后, 运行客户端main方法调用.
        
##### 示例6. 纯java版,不引入springboot, 使用用户自定义的基于TCP的协议

        1.编写启动类
        public class MyServer {
            public static void main(String[] args) {
                StartupServer server = new StartupServer(8080);
                server.addProtocol(new MyProtocol());
                server.start();
            }
        }
        
        2.编写自定义协议
        
        public class MyProtocol extends AbstractProtocol {
            private static final Charset UTF8 = Charset.forName("utf-8");
    
            @Override
            public boolean canSupport(ByteBuf msg) {
                String reqString = msg.toString(UTF8);
                return Objects.equals("开启吧!我的自定义协议", reqString);
            }
    
            @Override
            public void addPipeline(Channel channel) throws Exception {
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
    
        3. 编写客户端
        public class MyClient {
            public static void main(String[] args) throws IOException {
                Socket socket = new Socket();
                socket.connect(new InetSocketAddress("localhost", 8080));
        
                socket.getOutputStream().write("开启吧!我的自定义协议".getBytes(Charset.forName("utf-8")));
                socket.getOutputStream().flush();
        
                byte[] serverMsg = new byte[4096];
                socket.getInputStream().read(serverMsg);
                System.out.println("read = " + new String(serverMsg));
        
                for (int i = 0; i < 100; i++) {
                    String msg = "你好啊" + i + "先生.";
                    socket.getOutputStream().write(msg.getBytes(Charset.forName("utf-8")));
                    socket.getOutputStream().flush();
                }
                socket.getOutputStream().write("拜拜~".getBytes(Charset.forName("utf-8")));
                socket.close();
            }
        }
    
        4. 启动服务端后, 运行客户端main方法调用.
        
##### 示例7. 纯java版,不引入springboot, 使用内置的协议

        1.编写启动类
        public class MyServer {
            public static void main(String[] args) {
                StartupServer server = new StartupServer(8080);
                // 添加mqtt协议
                server.addProtocol(new com.github.netty.protocol.MqttProtocol());
                // 添加mysql协议
                server.addProtocol(new com.github.netty.protocol.MysqlProtocol(new InetSocketAddress("l92.168.101.1",3306)));
                // 添加一种rpc协议
                server.addProtocol(new com.github.netty.protocol.NRpcProtocol(new ApplicationX()));
                // 添加http或websocket
                server.addProtocol(new com.github.netty.protocol.HttpServletProtocol(new ServletContext()));
                // 添加自定义协议
                server.addProtocol(new AbstractProtocol() {
                    @Override
                    public String getProtocolName() {
                        return "hello world";
                    }
        
                    @Override
                    public boolean canSupport(ByteBuf clientFirstMsg) {
                        return true;
                    }
        
                    @Override
                    public void addPipeline(Channel channel) throws Exception {
                        channel.pipeline().addLast(new AbstractChannelHandler<ByteBuf, ByteBuf>() {
                            @Override
                            protected void onMessageReceived(ChannelHandlerContext ctx, ByteBuf msg) throws Exception {
                                System.out.println("收到! = " + msg.toString(Charset.forName("utf-8")));
                            }
        
                            @Override
                            public void channelActive(ChannelHandlerContext ctx) throws Exception {
                                System.out.println("新连接进入");
                            }
        
                            @Override
                            public void channelInactive(ChannelHandlerContext ctx) throws Exception {
                                System.out.println("连接断开");
                            }
                        });
                    }
                });
                // 启动
                server.start();
            }
        }
        
        2. 启动成功  (version = 2.2.3, port = 8080, pid = 6220, protocol = [hello world, http, NRPC/218, mqtt, mysql]
        
        10:52:28.772 [NettyX-Server-Boss-NIO-2-1] INFO com.github.netty.StartupServer - StartupServer@1 start (version = 2.2.3, port = 8080, pid = 6220, protocol = [hello world, http, NRPC/218, mqtt, mysql], os = windows 10) ...

 ---

#### 核心代码

com.github.netty.springboot.server.NettyTcpServer服务器启动时

com.github.netty.protocol.DynamicProtocolChannelHandler 接收新链接的第一个TCP数据包进行路由

com.github.netty.core.ProtocolHandler 处理之后的数据交换逻辑

#### 如何参与
    
作者邮箱 : 842156727@qq.com

讨论QQ群 : 779740988

![](https://user-images.githubusercontent.com/18204507/166250902-8f058288-fcd5-4bfb-8285-8e98b541786a.png)

* 有问题交issue, 想改代码直接pull request即可. github都会通过微信及时通知我.

* 有不懂得地方,我都会及时回复.

* 如果觉得这个产品还不错，请多多向您的朋友、同事推荐，感谢至极


http://alios.cn

http://liteos.com

http://rt-thread.org


