# Spring-boot-protocol (用Netty实现)

将springboot的WebServer改为了NettyTcpServer, 为使用者扩充了网络编程的能力.

![](https://user-images.githubusercontent.com/18204507/68989252-9d871a80-087e-11ea-96e1-20c12689c12a.png)

多协议服务器, Springboot协议扩展包, 允许单端口提供多协议服务.其中内置多种网络传输(标准与规范)的实现库, 轻松添加或扩展协议. 例: HttpServlet, RPC, MQTT（物联网通讯协议）, Websocket, RTSP(流媒体协议), DNS（域名解析协议）,MYSQL协议.

    1.可以替代tomcat或jetty. 导包后一个@EnableNettyEmbedded注解即用. 
    
    2.支持http请求聚合, 然后用 select * from id in (httpRequestList). 示例：com.github.netty.http.example.HttpGroupByApiController.java
    
    3.支持异步零拷贝。sendFile, mmap. 示例：com.github.netty.http.example.HttpZeroCopyController.java
    
    4.HttpServlet性能比tomcat的NIO2高出 25%/TPS。
        1. Netty的池化内存,减少了GC对CPU的消耗 
        2. Tomcat的NIO2, 注册OP_WRITE后,tomcat会阻塞用户线程等待, 并没有释放线程. 
        3. 与tomcat不同,支持两种IO模型,可供用户选择
    
    5.RPC性能略胜阿里巴巴的Dubbo(因为IO模型设计与dubbo不同，减少了线程切换), 使用习惯保持与springcloud相同
    
    6.Mysql,MQTT等协议可以在不依赖协议网关, 单机单端口同时支持N种协议 (例: HTTP,MQTT,Mysql,Websocket.)
    
    7.可以添加自定义传输协议. (例: 定长传输, 分隔符传输)

    8.开启Mysql协议,代理处理客户端与服务端的数据包, 记录mysql日志.
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
    
    
作者邮箱 : 842156727@qq.com

github地址 : https://github.com/wangzihaogithub


如果需要不依赖spring的servlet, 可以使用 https://github.com/wangzihaogithub/netty-servlet (支持文件零拷贝,可扩展底层通讯)


### 使用方法

#### 1.添加依赖, 在pom.xml中加入 [![Maven Central](https://maven-badges.herokuapp.com/maven-central/com.github.wangzihaogithub/spring-boot-protocol/badge.svg)](https://search.maven.org/search?q=g:com.github.wangzihaogithub%20AND%20a:spring-boot-protocol)

```xml
<!-- https://mvnrepository.com/artifact/com.github.wangzihaogithub/spring-boot-protocol -->
<dependency>
  <groupId>com.github.wangzihaogithub</groupId>
  <artifactId>spring-boot-protocol</artifactId>
  <version>2.2.3</version>
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

#### 更多功能例子example-> [请点击这里查看示例代码](https://github.com/wangzihaogithub/netty-example "https://github.com/wangzihaogithub/netty-example")

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
        2022-04-10 09:58:04.673  INFO 2716 --- [           main] c.github.netty.mqtt.MqttBrokerBootstrap  : Started MqttBrokerBootstrap in 2.235 seconds (JVM running for 3.807)
        
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
        
            /**
             * Servlet原生的上传测试
             * @param request
             * @param response
             * @return
             * @throws IOException
             */
            @RequestMapping("/uploadForServlet")
            public ResponseEntity<String> uploadForServlet(HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException {
                Collection<Part> parts = request.getParts();
        
                for (Part part : parts) {
                    InputStream inputStream = part.getInputStream();
                    int available = inputStream.available();
                    inputStream.close();
                    String fileNameOrFieldName = Objects.toString(part.getSubmittedFileName(), part.getName());
                    Assert.isTrue(available != -1, fileNameOrFieldName);
                    logger.info("uploadForServlet -> file = {}, length = {}", fileNameOrFieldName,available);
                }
        
                for (Map.Entry<String, String[]> entry : request.getParameterMap().entrySet()) {
                    Assert.isTrue(entry.getKey().length() > 0, Arrays.toString(entry.getValue()));
                    logger.info("uploadForServlet -> field = {}, value = {}",entry.getKey(),entry.getValue());
                }
                return new ResponseEntity<>("success", HttpStatus.OK);
            }
        
            /**
             * spring的上传测试
             * @param params 文本参数
             * @param request MultipartHttpServletRequest
             * @return
             * @throws IOException
             */
            @RequestMapping("/uploadForSpring")
            public ResponseEntity<String> uploadForSpring(@RequestParam Map<String,String> params, MultipartHttpServletRequest request) throws IOException {
                for (List<MultipartFile> files : request.getMultiFileMap().values()) {
                    for (MultipartFile file : files) {
                        InputStream inputStream = file.getInputStream();
                        int available = inputStream.available();
                        inputStream.close();
                        String fileNameOrFieldName = Objects.toString(file.getOriginalFilename(), file.getName());
                        Assert.isTrue(available != -1, fileNameOrFieldName);
                        logger.info("uploadForSpring -> file = {}, length = {}", fileNameOrFieldName,available);
                    }
                }
        
                for (Map.Entry<String, String> entry : params.entrySet()) {
                    Assert.isTrue(entry.getKey().length() > 0, entry.getValue());
                    logger.info("uploadForSpring -> field = {}, value = {}",entry.getKey(),entry.getValue());
                }
                return new ResponseEntity<>("success", HttpStatus.OK);
            }
        
            /**
             * apache common-fileupload的上传测试
             * @param request
             * @param response
             * @return
             * @throws IOException
             * @throws FileUploadException
             */
            @RequestMapping("/uploadForApache")
            public ResponseEntity<String> uploadForApache(HttpServletRequest request, HttpServletResponse response) throws IOException, FileUploadException {
                boolean isMultipart = ServletFileUpload.isMultipartContent(request);
                if (isMultipart) {
                    ServletFileUpload upload = new ServletFileUpload();
                    Map<String, String> params = new HashMap<>();
                    FileItemIterator iter = upload.getItemIterator(request);
                    while (iter.hasNext()) {
                        FileItemStream item = iter.next();
                        if (item.isFormField()) {
                            String fieldName = item.getFieldName();
                            String value = Streams.asString(item.openStream());
                            params.put(fieldName, value);
                            Assert.isTrue(fieldName.length() > 0, value);
                            logger.info("uploadForApache -> field = {}, value = {}",fieldName,value);
                        } else {
                            try (InputStream is = item.openStream()) {
                                int available = is.available();
                                Path copyFileTo = Paths.get(System.getProperty("user.dir"), item.getName());
                                Files.copy(is, copyFileTo,
                                        StandardCopyOption.REPLACE_EXISTING);
        
                                Assert.isTrue(available != -1, item.getName());
                                logger.info("uploadForApache -> 上传至 = {}, file = {}, length = {}",copyFileTo,item.getName(),available);
                            }
                        }
                    }
                }
                return new ResponseEntity<>("success", HttpStatus.OK);
            }
        
            @RequestMapping("/downloadFile")
            public ResponseEntity<String> downloadFile(@RequestParam(required = false,defaultValue = "7") Integer size,HttpServletRequest request, HttpServletResponse response) throws Exception {
                String fileName = "CentOS-7-x86_64-DVD-2003.iso";
        
        //        byte[] file = new byte[1024 * 1024 * size];
        //        for (int i = 0; i < file.length; i++) {
        //            file[i] = (byte) i;
        //        }
                handleDownloadStream(fileName, new FileInputStream(new File("D:\\aaa.txt")), request, response);
                return new ResponseEntity<>(HttpStatus.OK);
            }
        
            @RequestMapping("/downloadFile1")
            public ResponseEntity<String> downloadFile1(HttpServletRequest request, HttpServletResponse response) throws Exception {
                NettyOutputStream outputStream = (NettyOutputStream) response.getOutputStream();
                outputStream.write(new File("C:\\Users\\Administrator\\Downloads\\android-x86-8.1-r5.iso"));
                return new ResponseEntity<>(HttpStatus.OK);
            }
        
            public void handleDownloadStream(String fileName, InputStream inputStream, HttpServletRequest request, HttpServletResponse res) throws IOException {
                byte[] buffer = new byte[4 * 1024];
                OutputStream os;
                try {
                    os = new BufferedOutputStream(res.getOutputStream());
                    res.reset();
                    String agent = request.getHeader("User-Agent");
                    if (agent == null) {
                        return;
                    }
                    agent = agent.toUpperCase();
        
                    //ie浏览器,火狐,Edge浏览器
                    if (agent.indexOf("MSIE") > 0 || agent.indexOf("RV:11.0") > 0 || agent.indexOf("EDGE") > 0 || agent.indexOf("SAFARI") > -1) {
                        fileName = URLEncoder.encode(fileName, "utf8").replaceAll("\\+", "%20");
                    } else {
                        fileName = new String(fileName.getBytes(StandardCharsets.UTF_8), "ISO8859_1");
                    }
                    //safari RFC 5987标准
                    if (agent.contains("SAFARI")) {
                        res.addHeader("content-disposition", "attachment;filename*=UTF-8''" + fileName);
                    } else {
                        res.addHeader("Content-disposition", "attachment; filename=\"" + fileName + '"');
                    }
                    res.setContentType("application/octet-stream");
                    res.setCharacterEncoding("UTF-8");
                    res.setContentLength(inputStream.available());
                    int length = 0;
                    while ((length = inputStream.read(buffer)) != -1) {
                        os.write(buffer, 0, length);
                    }
                    os.flush();
        
                } catch (Exception e) {
                    e.printStackTrace();
                } finally {
                    IOUtils.closeQuietly(inputStream);
                }
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
        
##### 示例2. 纯java版,不引入springboot, 使用HTTP或websocket模块

        public class HttpBootstrap {
            public static void main(String[] args) {
                StartupServer server = new StartupServer(8080);
                server.addProtocol(newHttpProtocol());
                server.start();
            }
            private static HttpServletProtocol newHttpProtocol() {
                ServletContext servletContext = new ServletContext();
                servletContext.addServlet("myHttpServlet", new MyHttpServlet())
                        .addMapping("/test");
                return new HttpServletProtocol(servletContext);
            }
        }
        
        2. 启动后,控制台已经看到http协议出现了,开启成功! 可以用浏览器打开或websocket服务了.  protocol = [http]
        10:10:26.026 [NettyX-Server-Boss-NIO-1-1] INFO com.github.netty.StartupServer - StartupServer@1 start (version = 2.2.3, port = 8080, pid = 6972, protocol = [http], os = windows 10) ...
    
##### 示例3. Springboot版,开启MQTT-Broker模块(需要手工开启), 注! 本项目是MQTT-Broker, 不是MQTT生产者与消费者
        
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

* 有问题交issue, 想改代码直接pull request即可. github都会通过微信及时通知我.

* 有不懂得地方,我都会及时回复.

* 如果觉得这个产品还不错，请多多向您的朋友、同事推荐，感谢至极


http://alios.cn

http://liteos.com

http://rt-thread.org


