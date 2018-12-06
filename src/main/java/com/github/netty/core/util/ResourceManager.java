package com.github.netty.core.util;

import java.io.*;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.ByteBuffer;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Objects;
import java.util.Set;

/**
 * 资源管理 (注:操作的所有目录前缀都要加 /)
 * @author 84215
 */
public class ResourceManager {
    private LoggerX logger = new LoggerX(getClass());
    private String rootPath;
    private ClassLoader classLoader;
    private String workspace;

    public ResourceManager(String rootPath, String workspace,ClassLoader classLoader) {
        if(rootPath == null || rootPath.isEmpty()){
            throw new IllegalStateException("path empty");
        }
        if(rootPath.startsWith("file:") || rootPath.startsWith("FILE:")){
            this.rootPath = rootPath.replace("file:","").replace("FILE:","");
        }else {
            this.rootPath = rootPath;
        }
        if(workspace == null || workspace.equals("/")){
            workspace = "";
        }
        if(workspace.length() > 0 && workspace.charAt(0) != '/'){
            workspace = "/".concat(workspace);
        }
        this.workspace = workspace;
        this.classLoader = classLoader == null? getClass().getClassLoader():classLoader;
        logger.info("ResourceManager rootPath={0},workspace={1}",rootPath,workspace);
    }

    /**
     * 获取路径下的文件夹数量
     * @param path 路径
     * @return 文件夹数量
     */
    public int countDir(String path) {
        Objects.requireNonNull(path);
        if(path.isEmpty() || (path.charAt(path.length()-1) != '/')){
            path = path.concat("/");
        }
        String basePath = getRealPath(path);
        if (basePath == null) {
            return 0;
        }
        File theBaseDir = new File(basePath);
        if (!theBaseDir.exists() || !theBaseDir.isDirectory()) {
            return 0;
        }
        String theFiles[] = theBaseDir.list();
        if (theFiles == null) {
            return 0;
        }

        int count = 0;
        String rootPath = basePath.concat(File.separator);
        for (String filename : theFiles) {
            File testFile = new File(rootPath.concat(filename));
            if (testFile.isDirectory()) {
                count++;
            }
        }
        return count;
    }

    /**
     * 获取路径下的文件数量
     * @param path 路径
     * @return 文件数量
     */
    public int countFile(String path) {
        Objects.requireNonNull(path);
        if(path.isEmpty() || (path.charAt(path.length()-1) != '/')){
            path = path.concat("/");
        }
        String basePath = getRealPath(path);
        if (basePath == null) {
            return 0;
        }
        File theBaseDir = new File(basePath);
        if (!theBaseDir.exists() || !theBaseDir.isDirectory()) {
            return 0;
        }
        String theFiles[] = theBaseDir.list();
        if (theFiles == null) {
            return 0;
        }

        int count = 0;
        String rootPath = basePath.concat(File.separator);
        for (String filename : theFiles) {
            File testFile = new File(rootPath.concat(filename));
            if (testFile.isFile()) {
                count++;
            }
        }
        return count;
    }

    /**
     * 获取路径下的所有目录
     * @param path 路径
     * @return 目录. 如果没有则返回NULL
     */
    public Set<String> getResourcePaths(String path) {
        Objects.requireNonNull(path);
        if(path.isEmpty() || (path.charAt(path.length()-1) != '/')){
            path = path.concat("/");
        }
        String basePath = getRealPath(path);
        if (basePath == null) {
            return null;
        }
        File theBaseDir = new File(basePath);
        if (!theBaseDir.exists() || !theBaseDir.isDirectory()) {
            return null;
        }
        String theFiles[] = theBaseDir.list();
        if (theFiles == null) {
            return null;
        }

        Set<String> thePaths = null;
        String rootPath = basePath.concat(File.separator);
        for (String filename : theFiles) {
            File testFile = new File(rootPath.concat(filename));
            if (testFile.isFile()) {
                if(thePaths == null){
                    thePaths = new HashSet<>();
                }
                thePaths.add(filename);
            } else if (testFile.isDirectory()) {
                if(thePaths == null){
                    thePaths = new HashSet<>();
                }
                thePaths.add(filename.concat("/"));
            }
        }
        return thePaths;
    }

    /**
     * 获取资源
     * @param path 相对路径
     * @return url地址
     * @throws MalformedURLException
     */
    public URL getResource(String path) throws MalformedURLException {
        if(rootPath == null || rootPath.length() == 0){
            return null;
        }
        if (path.isEmpty() || path.charAt(0) != '/') {
            throw new MalformedURLException("Path '" + path + "' does not start with '/'");
        }

        String realPath = getRealPath(path);
        if(realPath == null){
            return null;
        }

        File file = new File(realPath);
        if(file.exists()){
            return new URL("file:".concat(realPath));
        }
        return null;
    }

    /**
     * 获取数据输入流
     * @param path 路径
     * @return 数据量
     */
    public InputStream getResourceAsStream(String path) {
        try {
            if (path.isEmpty() || path.charAt(0) != '/') {
                logger.warn("Path '{0}' does not start with '/'",path);
                return null;
            }

            URL url = getResource(path);
            if(url == null){
                return null;
            }
            return url.openStream();
        } catch (IOException e) {
            logger.warn("Throwing exception when getResourceAsStream of {0}, case {1} ",path,e.getMessage());
            return null;
        }
    }

    /**
     * 获取真实路径
     * @param path 相对路径
     * @return
     */
    public String getRealPath(String path) {
        if (path.length() > 0 && path.charAt(0) != '/') {
            path = File.separator.concat(path);
        }
        String realPath;
        if(workspace.isEmpty()){
            realPath = rootPath.concat(path);
        }else {
            realPath = rootPath.concat(workspace).concat(path);
        }
        return realPath;
    }

    public ClassLoader getClassLoader() {
        return classLoader;
    }

    /**
     * 写文件
     * @param dataIterator 数据
     * @param targetPath 路径
     * @param targetFileName 文件名称
     * @throws IOException
     */
    public void writeFile(Iterator<ByteBuffer> dataIterator, String targetPath, String targetFileName) throws IOException {
        IOUtil.writeFile(dataIterator, getRealPath(targetPath),targetFileName,false);
    }

    /**
     * 写文件
     * @param data 数据
     * @param targetPath 路径
     * @param targetFileName 文件名称
     * @throws IOException
     */
    public void writeFile(byte[]data, String targetPath, String targetFileName) throws IOException {
        IOUtil.writeFile(data, getRealPath(targetPath),targetFileName,false);
    }

    /**
     * 写文件
     * @param data 数据
     * @param targetPath 路径
     * @param targetFileName 文件名称
     * @param append 是否拼接旧数据
     * @throws IOException
     */
    public void writeFile(byte[]data, String targetPath, String targetFileName, boolean append) throws IOException {
        IOUtil.writeFile(data, getRealPath(targetPath),targetFileName,append);
    }

    /**
     * 写文件 (注:用完记得关闭)
     * @param targetPath 路径
     * @param targetFileName 文件名称
     * @param append 是否拼接旧数据
     * @throws IOException
     */
    public FileOutputStream writeFile(String targetPath, String targetFileName, boolean append) throws IOException {
        return IOUtil.writeFile(getRealPath(targetPath),targetFileName,append);
    }

    /**
     * 读文件 (注:用完记得关闭)
     * @param sourcePath 路径
     * @param sourceFileName 文件名称
     * @return 文件流
     * @throws FileNotFoundException
     */
    public FileInputStream readFile(String sourcePath,String sourceFileName) throws FileNotFoundException {
        return IOUtil.readFile(getRealPath(sourcePath),sourceFileName);
    }

    /**
     * 拷贝文件
     * @param sourcePath 源路径
     * @param sourceFileName 源文件名称
     * @param targetPath 目标路径
     * @param targetFileName 目标文件名称
     * @param buffCapacity 缓冲区大小
     * @throws FileNotFoundException
     * @throws IOException
     */
    public void copyTo(String sourcePath,String sourceFileName,
                                    String targetPath,String targetFileName,int buffCapacity) throws FileNotFoundException,IOException {
        IOUtil.copyTo(getRealPath(sourcePath),sourceFileName,
                getRealPath(targetPath),targetFileName,false,buffCapacity);
    }

    /**
     * 创建文件 (可以连带父文件创建)
     * @param path 文件路径
     * @return <code>true</code> if and only if the directory was created,
     *          along with all necessary parent directories; <code>false</code>
     *          otherwise
     */
    public boolean mkdirs(String path) {
        if(path == null || path.isEmpty()) {
            return false;
        }
        if(path.charAt(0) != '/'){
            throw new IllegalArgumentException("Path '"+path+"' must start with '/'");
        }
        return new File(getRealPath(path)).mkdirs();
    }

    /**
     * 删除目录
     * @param path
     * @return
     */
    public boolean delete(String path) {
        if(path == null){
            throw new NullPointerException("path");
        }
        if(path.isEmpty() || path.charAt(0) != '/'){
            throw new IllegalArgumentException("Path '"+path+"' must start with '/'");
        }
        return IOUtil.deleteDir(new File(getRealPath(path)));
    }

    /**
     * 删除目录下的所有子目录
     * @param path
     * @return
     */
    public void deleteChild(String path) {
        if(path == null){
            throw new NullPointerException("path");
        }
        if(path.isEmpty() || path.charAt(0) != '/'){
            throw new IllegalArgumentException("Path '"+path+"' must start with '/'");
        }
        IOUtil.deleteDirChild(new File(getRealPath(path)));
    }

    /**
     * 获取工作空间
     * @return
     */
    public String getWorkspace() {
        return workspace;
    }

    /**
     * 获取根路径
     * @return
     */
    public String getRootPath() {
        return rootPath;
    }

    @Override
    public String toString() {
        return "ResourceManager{" +
                "rootPath='" + rootPath + '\'' +
                ", workspace='" + workspace + '\'' +
                '}';
    }
}
