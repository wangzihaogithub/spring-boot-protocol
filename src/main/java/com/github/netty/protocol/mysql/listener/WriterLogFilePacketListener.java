package com.github.netty.protocol.mysql.listener;

import com.github.netty.core.util.*;
import com.github.netty.protocol.mysql.MysqlPacket;
import com.github.netty.protocol.mysql.Session;
import io.netty.channel.ChannelHandlerContext;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeUnit;


public class WriterLogFilePacketListener implements MysqlPacketListener {
    public static String KEY_LOG_WRITE_INTERVAL = "netty-mysql.log.writeInterval";
    public static String KEY_LOG_FILE_NAME = "netty-mysql.log.fileName";
    public static String KEY_LOG_PATH = "netty-mysql.log.path";
    private boolean enable = false;

    public boolean isEnable() {
        return enable;
    }

    public void setEnable(boolean enable) {
        this.enable = enable;
    }

    public void setLogWriteInterval(int logWriteInterval) {
        System.setProperty(KEY_LOG_WRITE_INTERVAL,String.valueOf(logWriteInterval));
    }
    public void setLogFileName(String fileName) {
        System.setProperty(KEY_LOG_FILE_NAME,fileName);
    }
    public void setLogPath(String path) {
        System.setProperty(KEY_LOG_PATH,path);
    }

    @Override
    public void onMysqlPacket(MysqlPacket packet, ChannelHandlerContext currentContext, Session session, String handlerType) {
        if(!enable){
            return;
        }
        String sessionId = session.getId();
        Queue<MysqlPacket> mysqlPackets = Lazy.UNWRITE_LOG_PACKET_MAP.computeIfAbsent(sessionId, k -> new ConcurrentLinkedQueue<>());
        mysqlPackets.add(packet);
    }

    public static class Lazy{
        public static final Map<String, Queue<MysqlPacket>> UNWRITE_LOG_PACKET_MAP = new ConcurrentHashMap<>();
        public static final ThreadPoolX WRITE_LOG_THREAD_POOL = new ThreadPoolX("Mysql-log",1,Thread.MIN_PRIORITY,false);
        public static final WriterLogRunnable RUNNABLE;
        static {
            try {
                long writeInterval = SystemPropertyUtil.getLong(KEY_LOG_WRITE_INTERVAL, 5000L);
                String fileName = SystemPropertyUtil.get(KEY_LOG_FILE_NAME, "netty-mysql.log");
                String path = SystemPropertyUtil.get(KEY_LOG_PATH, SystemPropertyUtil.get("user.dir", "./"));
                RUNNABLE = new WriterLogRunnable(fileName, path);
                WRITE_LOG_THREAD_POOL.scheduleAtFixedRate(RUNNABLE,
                        writeInterval, writeInterval, TimeUnit.MILLISECONDS);
                Runtime.getRuntime().addShutdownHook(new Thread(RUNNABLE, "netty-mysql.log.hook"));
            }catch (Exception e){
                throw new Error("netty-mysql.log, Lazy.class. init error="+e.toString(),e);
            }
        }
    }

    public static class WriterLogRunnable implements Runnable{
        private static final LoggerX LOGGER = LoggerFactoryX.getLogger(WriterLogRunnable.class);
        private String fileName;
        private String path;
        public WriterLogRunnable(String fileName, String path) {
            this.fileName = fileName;
            this.path = path;
        }

        @Override
        public void run() {
            StringBuilder sb = new StringBuilder();
            DateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

            List<String> removeKeyList = new ArrayList<>();
            for (Map.Entry<String, Queue<MysqlPacket>> entry : Lazy.UNWRITE_LOG_PACKET_MAP.entrySet()) {
                String key = entry.getKey();
                Queue<MysqlPacket> queue = entry.getValue();
                if(queue == null || queue.isEmpty()){
                    removeKeyList.add(key);
                    continue;
                }
                try {
                    IOUtil.writeFile(new Iterator<ByteBuffer>() {
                        private MysqlPacket packet;
                        @Override
                        public boolean hasNext() {
                            return (packet = queue.poll()) != null;
                        }

                        @Override
                        public ByteBuffer next() {
                            sb.setLength(0);
                            sb.append(dateFormat.format(packet.getTimestamp()));
                            sb.append('\t');
                            sb.append(packet.getSequenceId());
                            sb.append('\t');
                            sb.append(packet);
                            sb.append('\n');
                            return ByteBuffer.wrap(sb.toString().getBytes());
                        }
                    },path,key.concat(fileName),true);
                } catch (IOException e) {
                    LOGGER.error("writer mysql log error={},key={},path={},fileName={}",
                            e.toString(),key,path,fileName,e);
                }
            }
            for (String key : removeKeyList) {
                Queue<MysqlPacket> mysqlPackets = Lazy.UNWRITE_LOG_PACKET_MAP.get(key);
                if(mysqlPackets.isEmpty()){
                    Lazy.UNWRITE_LOG_PACKET_MAP.remove(key);
                }
            }
        }

    }
}
