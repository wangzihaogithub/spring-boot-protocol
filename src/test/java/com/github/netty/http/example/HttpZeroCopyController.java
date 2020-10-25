package com.github.netty.http.example;

import com.github.netty.protocol.servlet.NettyOutputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.WritableByteChannel;
import java.nio.charset.Charset;
import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 1.内存映射的处理方式. 零拷贝。 sendFile, mmap.
 *
 * http://localhost:8080/test/zeroCopy/helloSync
 * http://localhost:8080/test/zeroCopy/helloAsync
 *
 * @see NettyOutputStream#write(ByteBuffer)
 * @see NettyOutputStream#write(FileChannel, long, long)
 * @see FileChannel#map(FileChannel.MapMode, long, long)
 * @see FileChannel#transferTo(long, long, WritableByteChannel)
 * @see FileChannel#transferFrom(ReadableByteChannel, long, long)
 */
@EnableScheduling
@RestController
@RequestMapping("/zeroCopy")
public class HttpZeroCopyController {
    private final Logger logger = LoggerFactory.getLogger(getClass());
    private final Queue<CompletableFuture<MappedByteBuffer>> queue = new ConcurrentLinkedQueue<>();
    private final MappedByteBuffer helloAsyncMmap = createMappedByteBuffer("helloAsync",4096 * 100);
    private final MappedByteBuffer[] helloSyncMmaps = {
            setValue("{\"id\":1}", createMappedByteBuffer("helloSync1",4096 * 100)),
            setValue("{\"id\":2}", createMappedByteBuffer("helloSync2",4096 * 100)),
            setValue("{\"id\":3}", createMappedByteBuffer("helloSync3",4096 * 100)),
    };

    public HttpZeroCopyController() throws IOException {}

    /**
     * 零拷贝同步调用
     * 访问地址： http://localhost:8080/test/zeroCopy/helloSync
     * @param request netty request
     * @param response netty response
     */
    @RequestMapping("/helloSync")
    public void helloSync(HttpServletRequest request,HttpServletResponse response) throws IOException {
        response.setContentType("application/json;charset=UTF-8");

        try(NettyOutputStream outputStream = (NettyOutputStream)response.getOutputStream()){
            MappedByteBuffer mappedByteBuffer = helloSyncMmaps[ThreadLocalRandom.current().nextInt(0,helloSyncMmaps.length)];

            outputStream.write(mappedByteBuffer);
        }
    }

    /**
     * 零拷贝异步调用， 现在用定时任务模拟，您可以换成异步回掉。
     * 访问地址： http://localhost:8080/test/zeroCopy/helloAsync
     * @param request netty request
     * @param response netty response
     * @return CompletableFuture spring异步的处理方式， 这里的 MappedByteBuffer是在定时任务线程批量生成 {@link #on5000Delay}
     */
    @RequestMapping("/helloAsync")
    public CompletableFuture<java.nio.MappedByteBuffer> helloAsync(HttpServletRequest request,HttpServletResponse response){
        CompletableFuture<java.nio.MappedByteBuffer> future = new java.util.concurrent.CompletableFuture<>();
        queue.offer(future);

        return future.handle((mappedByteBuffer,throwable) -> {
            logger.info("async handle = {}",request.getRequestURL());

            response.setContentType("application/json;charset=UTF-8");
            try(NettyOutputStream outputStream = (NettyOutputStream)response.getOutputStream()){
                outputStream.write(mappedByteBuffer);
                // outputStream.write(new File("/home/temp.json"));
            } catch (IOException e) {
                e.printStackTrace();
            }
            return null;
        });
    }

    /**
     * 5秒间隔的定时任务
     * 单线程处理N个请求, 异步批量处理。 不存在并发。
     *
     * 时间复杂度： 原本每5秒O(N), 降为每5秒 O(1).
     * 空间复杂度：
     */
    @Scheduled(fixedDelay = 5000)
    public void on5000Delay() {
        //每5秒，只查库一次。 比如：这是查数据库动作
        String list = "[{\"random\":\""+ UUID.randomUUID()+"\"}]";
        setValue(list,helloAsyncMmap);

        java.util.concurrent.CompletableFuture<MappedByteBuffer> current;
        while ((current = queue.poll()) != null){
            //回掉方法
            current.complete(helloAsyncMmap);
            // current.completeExceptionally(new RuntimeException("test error"));
            logger.info("notify netty zero copy = {}",list);
        }
    }

    /**
     * 创建一个内存映射
     * @param key key
     * @param maxBodyLength 响应体字节上限
     * @return 内存映射
     * @throws IOException 文件创建失败异常
     */
    public static MappedByteBuffer createMappedByteBuffer(String key,int maxBodyLength) throws IOException {
        RandomAccessFile accessFile = new RandomAccessFile(File.createTempFile(key,"map"), "rw");
        FileChannel fileChannel = accessFile.getChannel();
        return fileChannel.map(FileChannel.MapMode.READ_WRITE,0,maxBodyLength);
    }

    /**
     * 给映射的内存赋值
     * @param value 值
     * @param buffer 映射的内存
     * @return buffer
     */
    public static MappedByteBuffer setValue(String value,MappedByteBuffer buffer){
        buffer.clear();
        buffer.put(value.getBytes(Charset.forName("UTF-8")));
        buffer.flip();
        return buffer;
    }
}
