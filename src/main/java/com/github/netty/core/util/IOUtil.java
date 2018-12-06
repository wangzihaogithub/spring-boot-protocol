package com.github.netty.core.util;

import io.netty.buffer.ByteBuf;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.Charset;
import java.util.Iterator;

/**
 * (输入|输出)流工具
 * @author acer01
 *  2018/8/11/011
 */
public class IOUtil {

    /**
     * 拷贝文件
     * @param sourcePath 源路径
     * @param sourceFileName 源文件名称
     * @param targetPath 目标路径
     * @param targetFileName 目标文件名称
     * @param append 是否拼接旧数据
     * @param buffCapacity 缓冲区大小
     * @throws FileNotFoundException
     * @throws IOException
     */
    public static void copyTo(String sourcePath,String sourceFileName,
                              String targetPath,String targetFileName,boolean append,int buffCapacity) throws FileNotFoundException,IOException {
        if(sourcePath == null){
            sourcePath = "";
        }
        if(targetPath == null){
            targetPath = "";
        }
        new File(targetPath).mkdirs();
        File inFile = new File(sourcePath.concat(File.separator).concat(sourceFileName));
        File outFile = new File(targetPath.concat(File.separator).concat(targetFileName));

        try(FileInputStream in =  new FileInputStream(inFile);
            FileOutputStream out = new FileOutputStream(outFile,append)) {
            FileChannel inChannel = in.getChannel();
            FileChannel outChannel = out.getChannel();
            ByteBuffer buffer = ByteBuffer.allocate(buffCapacity);
            while (true) {
                buffer.clear();
                int r = inChannel.read(buffer);
                if (r == -1) {
                    break;
                }
                buffer.flip();
                outChannel.write(buffer);
            }
        }
    }

    /**
     * 写文件
     * @param data 数据
     * @param targetPath 路径
     * @param targetFileName 文件名称
     * @param append 是否拼接旧数据
     * @throws IOException
     */
    public static void writeFile(byte[] data, String targetPath, String targetFileName, boolean append) throws IOException {
        if(targetPath == null){
            targetPath = "";
        }
        new File(targetPath).mkdirs();
        File outFile = new File(targetPath.concat(File.separator).concat(targetFileName));
        if(!outFile.exists()){
            outFile.createNewFile();
        }
        try(FileOutputStream out = new FileOutputStream(outFile,append)) {
            FileChannel outChannel = out.getChannel();
            ByteBuffer buffer = ByteBuffer.wrap(data);
            outChannel.write(buffer);
        }
    }

    /**
     * 写文件
     * @param dataIterator 数据
     * @param targetPath 路径
     * @param targetFileName 文件名称
     * @param append 是否拼接旧数据
     * @throws IOException
     */
    public static void writeFile(Iterator<ByteBuffer> dataIterator, String targetPath, String targetFileName, boolean append) throws IOException {
        if(targetPath == null){
            targetPath = "";
        }
        new File(targetPath).mkdirs();
        File outFile = new File(targetPath.concat(File.separator).concat(targetFileName));
        if(!outFile.exists()){
            outFile.createNewFile();
        }
        try(FileOutputStream out = new FileOutputStream(outFile,append)) {
            FileChannel outChannel = out.getChannel();
            while (dataIterator.hasNext()){
                ByteBuffer buffer = dataIterator.next();
                if(buffer != null) {
                    if(buffer.position() != 0){
                        buffer.flip();
                    }
                    outChannel.write(buffer);
                }
            }
        }catch (IOException e){
            throw e;
        }
    }

    /**
     * 写文件 (注:用完记得关闭)
     * @param targetPath 路径
     * @param targetFileName 文件名称
     * @param append 是否拼接旧数据
     * @throws FileNotFoundException
     */
    public static FileOutputStream writeFile(String targetPath, String targetFileName, boolean append) throws IOException {
        if(targetPath == null){
            targetPath = "";
        }
        new File(targetPath).mkdirs();
        File outFile = new File(targetPath.concat(File.separator).concat(targetFileName));
        if(!outFile.exists()){
            outFile.createNewFile();
        }
        return new FileOutputStream(outFile,append);
    }

    /**
     * 读文件 (注:用完记得关闭)
     * @param sourcePath 路径
     * @param sourceFileName 文件名称
     * @return 文件流
     * @throws FileNotFoundException
     */
    public static FileInputStream readFile(String sourcePath, String sourceFileName) throws FileNotFoundException {
        if(sourcePath == null){
            sourcePath = "";
        }
        File inFile = new File(sourcePath.concat(File.separator).concat(sourceFileName));
        return new FileInputStream(inFile);
    }

    /**
     * 读文件
     * @param sourcePath 路径
     * @param sourceFileName 文件名称
     * @param charset 文件编码
     * @return 文件流
     * @throws FileNotFoundException
     */
    public static String readFileToString(String sourcePath, String sourceFileName,String charset) throws FileNotFoundException {
        if(sourcePath == null){
            sourcePath = "";
        }
        File inFile = new File(sourcePath.concat(File.separator).concat(sourceFileName));
        return readInput(new FileInputStream(inFile),charset);
    }

    public static int indexOf(ByteBuf byteBuf,byte value){
        int len = byteBuf.readableBytes();
        for(int i= 0; i<len; i++){
            byte b = byteBuf.getByte(i);
            if(b == value){
                return i;
            }
        }
        return -1;
    }

    /**
     * 删除目录下的 所有子目录或文件
     * @param dir 文件夹路径
     * @return boolean Returns "true" if all deletions were successful.
     *                 If a deletion fails, the method stops attempting to
     *                 delete and returns "false".
     */
    public static void deleteDirChild(File dir) {
        if (!dir.isDirectory()) {
            return;
        }

        for (String children : dir.list()) {
           deleteDir(new File(dir, children));
        }
    }

    /**
     * 删除文件或目录
     * @param dir 文件夹路径 或 文件路径
     * @return boolean Returns "true" if all deletions were successful.
     *                 If a deletion fails, the method stops attempting to
     *                 delete and returns "false".
     */
    public static boolean deleteDir(File dir) {
        if (dir.isDirectory()) {
            for (String children : dir.list()) {
                boolean success = deleteDir(new File(dir, children));
                if (!success) {
                    return false;
                }
            }
        }
        return dir.delete();
    }

    /**
     * 写模式变读模式
     * @param byteBuf
     */
    public static void writerModeToReadMode(ByteBuf byteBuf){
        if(byteBuf == null){
            return;
        }
        if(byteBuf.readableBytes() == 0 && byteBuf.capacity() > 0) {
            byteBuf.writerIndex(byteBuf.capacity());
        }
    }

    /**
     * 读取输入流
     * @param inputStream
     * @return
     */
    public static String readInput(InputStream inputStream){
        return readInput(inputStream, Charset.defaultCharset().name());
    }

    public static String readInput(InputStream inputStream,String encode){
        try {
            StringBuilder sb = new StringBuilder();
            BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, encode));
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }
            return sb.toString();
        }catch (Exception e){
            return null;
        }finally {
            if(inputStream != null){
                try {
                    inputStream.close();
                } catch (IOException e) {
                    //
                }
            }
        }
    }

    public static byte getByte(byte[] memory, int index) {
        return memory[index];
    }

    public static short getShort(byte[] memory, int index) {
        return (short) (memory[index] << 8 | memory[index + 1] & 0xFF);
    }

    public static short getShortLE(byte[] memory, int index) {
        return (short) (memory[index] & 0xff | memory[index + 1] << 8);
    }

    public static int getUnsignedMedium(byte[] memory, int index) {
        return  (memory[index]     & 0xff) << 16 |
                (memory[index + 1] & 0xff) <<  8 |
                memory[index + 2] & 0xff;
    }

    public static int getUnsignedMediumLE(byte[] memory, int index) {
        return  memory[index]     & 0xff         |
                (memory[index + 1] & 0xff) <<  8 |
                (memory[index + 2] & 0xff) << 16;
    }

    public static int getInt(byte[] memory, int index) {
        return  (memory[index]     & 0xff) << 24 |
                (memory[index + 1] & 0xff) << 16 |
                (memory[index + 2] & 0xff) <<  8 |
                memory[index + 3] & 0xff;
    }

    public static int getIntLE(byte[] memory, int index) {
        return  memory[index]      & 0xff        |
                (memory[index + 1] & 0xff) << 8  |
                (memory[index + 2] & 0xff) << 16 |
                (memory[index + 3] & 0xff) << 24;
    }

    public static long getLong(byte[] memory, int index) {
        return  ((long) memory[index]     & 0xff) << 56 |
                ((long) memory[index + 1] & 0xff) << 48 |
                ((long) memory[index + 2] & 0xff) << 40 |
                ((long) memory[index + 3] & 0xff) << 32 |
                ((long) memory[index + 4] & 0xff) << 24 |
                ((long) memory[index + 5] & 0xff) << 16 |
                ((long) memory[index + 6] & 0xff) <<  8 |
                (long) memory[index + 7] & 0xff;
    }

    public static long readLong(InputStream in) throws IOException {
        byte[] bytes = new byte[8];
        in.read(bytes);
        return getLong(bytes,0);
    }

    public static long getLongLE(byte[] memory, int index) {
        return  (long) memory[index]      & 0xff        |
                ((long) memory[index + 1] & 0xff) <<  8 |
                ((long) memory[index + 2] & 0xff) << 16 |
                ((long) memory[index + 3] & 0xff) << 24 |
                ((long) memory[index + 4] & 0xff) << 32 |
                ((long) memory[index + 5] & 0xff) << 40 |
                ((long) memory[index + 6] & 0xff) << 48 |
                ((long) memory[index + 7] & 0xff) << 56;
    }

    public static void setByte(byte[] memory, int index, int value) {
        memory[index] = (byte) value;
    }

    public static void setShort(byte[] memory, int index, int value) {
        memory[index]     = (byte) (value >>> 8);
        memory[index + 1] = (byte) value;
    }

    public static void setShortLE(byte[] memory, int index, int value) {
        memory[index]     = (byte) value;
        memory[index + 1] = (byte) (value >>> 8);
    }

    public static void setMedium(byte[] memory, int index, int value) {
        memory[index]     = (byte) (value >>> 16);
        memory[index + 1] = (byte) (value >>> 8);
        memory[index + 2] = (byte) value;
    }

    public static void setMediumLE(byte[] memory, int index, int value) {
        memory[index]     = (byte) value;
        memory[index + 1] = (byte) (value >>> 8);
        memory[index + 2] = (byte) (value >>> 16);
    }

    public static void setInt(byte[] memory, int index, int value) {
        memory[index]     = (byte) (value >>> 24);
        memory[index + 1] = (byte) (value >>> 16);
        memory[index + 2] = (byte) (value >>> 8);
        memory[index + 3] = (byte) value;
    }

    public static void setIntLE(byte[] memory, int index, int value) {
        memory[index]     = (byte) value;
        memory[index + 1] = (byte) (value >>> 8);
        memory[index + 2] = (byte) (value >>> 16);
        memory[index + 3] = (byte) (value >>> 24);
    }

    public static void setLong(byte[] memory, int index, long value) {
        memory[index]     = (byte) (value >>> 56);
        memory[index + 1] = (byte) (value >>> 48);
        memory[index + 2] = (byte) (value >>> 40);
        memory[index + 3] = (byte) (value >>> 32);
        memory[index + 4] = (byte) (value >>> 24);
        memory[index + 5] = (byte) (value >>> 16);
        memory[index + 6] = (byte) (value >>> 8);
        memory[index + 7] = (byte) value;
    }

    public static void setLongLE(byte[] memory, int index, long value) {
        memory[index]     = (byte) value;
        memory[index + 1] = (byte) (value >>> 8);
        memory[index + 2] = (byte) (value >>> 16);
        memory[index + 3] = (byte) (value >>> 24);
        memory[index + 4] = (byte) (value >>> 32);
        memory[index + 5] = (byte) (value >>> 40);
        memory[index + 6] = (byte) (value >>> 48);
        memory[index + 7] = (byte) (value >>> 56);
    }

}
