package com.github.netty.core.util;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.util.ReferenceCountUtil;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.Charset;
import java.util.Iterator;

/**
 * (input | output) stream tool
 * @author wangzihao
 *  2018/8/11/011
 */
public class IOUtil {
    public static final int BYTE_LENGTH = 1;
    public static final int INT_LENGTH = 4;
    public static final int CHAR_LENGTH = 2;
    public static final int LONG_LENGTH = 8;

    /**
     * Copy files
     * @param sourcePath sourcePath
     * @param sourceFileName sourceFileName
     * @param targetPath targetPath
     * @param targetFileName targetFileName
     * @param append Whether to concatenate old data
     * @param bufferCapacity buffer Capacity
     * @throws FileNotFoundException FileNotFoundException
     * @throws IOException IOException
     */
    public static void copyTo(String sourcePath,String sourceFileName,
                              String targetPath,String targetFileName,boolean append,int bufferCapacity) throws FileNotFoundException,IOException {
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
            FileOutputStream out = new FileOutputStream(outFile,append);
            FileChannel inChannel = in.getChannel();
            FileChannel outChannel = out.getChannel()) {
            ByteBuffer buffer = ByteBuffer.allocate(bufferCapacity);
            while (true) {
                buffer.clear();
                int r = inChannel.read(buffer);
                if (r == -1) {
                    break;
                }
                buffer.flip();
                outChannel.write(buffer);
            }
            outChannel.force(false);
        }
    }

    /**
     * writeFile
     * @param data data
     * @param targetPath targetPath
     * @param targetFileName targetFileName
     * @param append Whether to concatenate old data
     * @throws IOException IOException
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
        try(FileOutputStream out = new FileOutputStream(outFile,append);
            FileChannel outChannel = out.getChannel()) {
            ByteBuffer buffer = ByteBuffer.wrap(data);
            outChannel.write(buffer);
            outChannel.force(false);
        }
    }

    /**
     * writeFile
     * @param in data
     * @param targetPath targetPath
     * @param targetFileName targetFileName
     * @param append Whether to concatenate old data
     * @param bufferCapacity buffer Capacity
     * @throws IOException IOException
     */
    public static void writeFile(InputStream in, String targetPath, String targetFileName, boolean append,int bufferCapacity) throws IOException {
        if(targetPath == null){
            targetPath = "";
        }
        File parent = new File(targetPath);
        parent.mkdirs();
        File outFile = new File(parent,targetFileName);
        if(!outFile.exists()){
            outFile.createNewFile();
        }
        try(FileOutputStream out = new FileOutputStream(outFile,append);
            FileChannel outChannel = out.getChannel();
            InputStream inputStream = in) {
            if(inputStream instanceof FileInputStream){
                try(FileChannel inChannel = ((FileInputStream) inputStream).getChannel()){
                    long chunkSize = bufferCapacity;
                    long position = 0;
                    long size = inChannel.size();
                    while (position < size) {
                        if (chunkSize < size - position) {
                            chunkSize = size - position;
                        }
                        position += inChannel.transferTo(position, chunkSize, outChannel);
                    }
                }
            }else {
                byte[] buffer = new byte[bufferCapacity];
                int len;
                while ((len = inputStream.read(buffer)) != -1) {
                    out.write(buffer, 0, len);
                    out.flush();
                }
            }
        }catch (IOException e){
            throw e;
        }
    }

    /**
     * writeFile
     * @param dataIterator data
     * @param targetPath targetPath
     * @param targetFileName targetFileName
     * @param append Whether to concatenate old data
     * @throws IOException IOException
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
        try(FileOutputStream out = new FileOutputStream(outFile,append);
            FileChannel outChannel = out.getChannel();) {
            while (dataIterator.hasNext()){
                ByteBuffer buffer = dataIterator.next();
                if(buffer != null) {
                    if(buffer.position() != 0){
                        buffer.flip();
                    }
                    outChannel.write(buffer);
                }
            }
            outChannel.force(false);
        }catch (IOException e){
            throw e;
        }
    }

     /**
     * Read the file to bytebuffer.(note: remember to close after using)
      * @param sourcePath sourcePath
     * @param sourceFileName sourceFileName
     * @return bytebuffer
     * @throws FileNotFoundException FileNotFoundException
     * @throws IOException IOException
     */
    public static ByteBuf readFileToByteBuffer(String sourcePath, String sourceFileName) throws FileNotFoundException,IOException {
        if(sourcePath == null){
            sourcePath = "";
        }
        File inFile = new File(sourcePath.concat(File.separator).concat(sourceFileName));

        try(FileInputStream in =  new FileInputStream(inFile);
            FileChannel inChannel = in.getChannel();) {
            ByteBuffer byteBuffer = ByteBuffer.allocate(8192);
            ByteBuf buffer = Unpooled.buffer();
            while (true) {
                byteBuffer.clear();
                int r = inChannel.read(byteBuffer);
                if (r == -1) {
                    break;
                }
                byteBuffer.flip();
                buffer.writeBytes(byteBuffer);
            }
            return buffer;
        }
    }

    /**
     * Read file to byte[]
     * @param sourcePath sourcePath
     * @param sourceFileName sourceFileName
     * @return byte[]
     * @throws FileNotFoundException FileNotFoundException
     * @throws IOException IOException
     */
    public static byte[] readFileToBytes(String sourcePath, String sourceFileName) throws FileNotFoundException,IOException {
        ByteBuf byteBuf = readFileToByteBuffer(sourcePath,sourceFileName);
        writerModeToReadMode(byteBuf);
        try {
            if (byteBuf.hasArray()) {
                return byteBuf.array();
            }else {
                byte[] bytes = new byte[byteBuf.readableBytes()];
                byteBuf.writeBytes(bytes);
                return bytes;
            }
        }finally {
            ReferenceCountUtil.safeRelease(byteBuf);
        }
    }

    /**
     * Write file (note: close it after using)
     * @param targetPath targetPath
     * @param targetFileName targetFileName
     * @param append Whether to concatenate old data
     * @throws FileNotFoundException FileNotFoundException
     * @return FileOutputStream
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
     * Read the file (note: close it after using)
     * @param sourcePath sourcePath
     * @param sourceFileName sourceFileName
     * @return File stream
     * @throws FileNotFoundException FileNotFoundException
     */
    public static FileInputStream readFile(String sourcePath, String sourceFileName) throws FileNotFoundException {
        if(sourcePath == null){
            sourcePath = "";
        }
        File inFile = new File(sourcePath.concat(File.separator).concat(sourceFileName));
        return new FileInputStream(inFile);
    }

    /**
     * Read the file
     * @param sourcePath sourcePath
     * @param sourceFileName sourceFileName
     * @param charset charset
     * @return File stream
     * @throws FileNotFoundException FileNotFoundException
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
     * Delete all subdirectories or files in the directory
     * @param dir Folder path
     */
    public static void deleteDirChild(File dir) {
        if (!dir.isDirectory()) {
            return;
        }
        String[] childrens =  dir.list();
        if(childrens != null) {
            for (String children : childrens) {
                deleteDir(new File(dir, children));
            }
        }
    }

    /**
     * Delete a file or directory
     * @param dir Folder path or file path
     * @return boolean Returns "true" if all deletions were successful.
     *                 If a deletion fails, the method stops attempting to
     *                 delete and returns "false".
     */
    public static boolean deleteDir(File dir) {
        if (dir.isDirectory()) {
            String[] childrens =  dir.list();
            if(childrens != null) {
                for (String children : childrens) {
                    boolean success = deleteDir(new File(dir, children));
                    if (!success) {
                        return false;
                    }
                }
            }
        }
        return dir.delete();
    }

    /**
     * Write mode to read mode
     * @param byteBuf byteBuf
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
     * Read input stream
     * @param inputStream inputStream
     * @return InputText
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

    public static String getString(byte[] memory, Charset charset) {
        return new String(memory,charset);
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

    public static long getUnsignedInt(byte[] memory, int index) {
        return getInt(memory,index) & 0x0FFFFFFFFL;
    }

    public static int getUnsignedByte(byte[] memory, int index) {
        return getByte(memory,index) & 0x0FF;
    }

    public static int getUnsignedShort(byte[] memory, int index) {
        return getShort(memory,index) & 0x0FFFF;
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
