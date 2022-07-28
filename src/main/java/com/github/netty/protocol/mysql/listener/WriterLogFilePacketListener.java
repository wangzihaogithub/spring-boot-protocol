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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
        System.setProperty(KEY_LOG_WRITE_INTERVAL, String.valueOf(logWriteInterval));
    }

    public void setLogFileName(String fileName) {
        System.setProperty(KEY_LOG_FILE_NAME, fileName);
    }

    public void setLogPath(String path) {
        System.setProperty(KEY_LOG_PATH, path);
    }

    @Override
    public void onMysqlPacket(MysqlPacket packet, ChannelHandlerContext currentContext, Session session, String handlerType) {
        if (!enable) {
            return;
        }
        String sessionId = session.getId();
        Queue<LogRecord> logRecords = Lazy.UNWRITE_LOG_MAP.computeIfAbsent(sessionId, k -> new ConcurrentLinkedQueue<>());
        logRecords.add(new LogRecord(session, packet, handlerType));
    }

    public static class LogRecord {
        Session session;
        MysqlPacket mysqlPacket;
        String handlerType;

        public LogRecord(Session session, MysqlPacket mysqlPacket, String handlerType) {
            this.session = session;
            this.mysqlPacket = mysqlPacket;
            this.handlerType = handlerType;
        }
    }

    public static class Lazy {
        public static final Map<String, Queue<LogRecord>> UNWRITE_LOG_MAP = new ConcurrentHashMap<>();
        public static final ThreadPoolX WRITE_LOG_THREAD_POOL = new ThreadPoolX("Mysql-log", 1, Thread.MIN_PRIORITY, false);
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
            } catch (Exception e) {
                throw new Error("netty-mysql.log, Lazy.class. init error=" + e.toString(), e);
            }
        }
    }

    public static class WriterLogRunnable implements Runnable {
        private final LoggerX loggerX = LoggerFactoryX.getLogger(getClass());
        private final Pattern jsonEscapePattern = Pattern.compile("\"", Pattern.LITERAL);
        private final String jsonEscapeReplace = Matcher.quoteReplacement("\\\"");
        private final DateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        private final String fileName;
        private final String path;

        public WriterLogRunnable(String fileName, String path) {
            this.fileName = fileName;
            this.path = path;
        }

        @Override
        public void run() {
            StringBuilder sb = new StringBuilder();
            List<String> removeKeyList = new ArrayList<>();
            for (Map.Entry<String, Queue<LogRecord>> entry : Lazy.UNWRITE_LOG_MAP.entrySet()) {
                String key = IOUtil.trimFilename(entry.getKey());
                Queue<LogRecord> queue = entry.getValue();
                if (queue == null || queue.isEmpty()) {
                    removeKeyList.add(key);
                    continue;
                }
                try {
                    IOUtil.writeFile(new Iterator<ByteBuffer>() {
                        private LogRecord record;

                        @Override
                        public boolean hasNext() {
                            return (record = queue.poll()) != null;
                        }

                        @Override
                        public ByteBuffer next() {
                            sb.setLength(0);
                            sb.append("{\n\t\"timestamp\":\"");
                            sb.append(dateFormat.format(record.mysqlPacket.getTimestamp()));
                            sb.append("\",\n\t\"sequenceId\":");
                            sb.append(record.mysqlPacket.getSequenceId());
                            sb.append(",\n\t\"connectionId\":");
                            sb.append(record.session.getConnectionId());
                            sb.append(",\n\t\"handlerType\":\"");
                            sb.append(record.handlerType);
                            sb.append("\",\n\t\"clientCharset\":\"");
                            sb.append(record.session.getClientCharset());
                            sb.append("\",\n\t\"serverCharset\":\"");
                            sb.append(record.session.getServerCharset());
                            sb.append("\",\n\t\"packet\":\"");
                            sb.append(jsonEscapePattern.matcher(record.mysqlPacket.toString()).replaceAll(jsonEscapeReplace));
                            sb.append("\"\n},\n");
                            return ByteBuffer.wrap(sb.toString().getBytes());
                        }
                    }, path, key.concat(fileName), true);
                } catch (IOException e) {
                    loggerX.error("writer mysql log error={},key={},path={},fileName={}",
                            e.toString(), key, path, fileName, e);
                }
            }
            for (String key : removeKeyList) {
                Queue<LogRecord> records = Lazy.UNWRITE_LOG_MAP.get(key);
                if (records != null && records.isEmpty()) {
                    Lazy.UNWRITE_LOG_MAP.remove(key);
                }
            }
        }

    }
}
