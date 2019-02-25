package com.github.netty.protocol.servlet;

import com.github.netty.core.util.CaseInsensitiveKeyMap;
import com.github.netty.core.util.ResourceManager;
import com.github.netty.protocol.servlet.util.HttpHeaderConstants;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufInputStream;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.http.multipart.FileUpload;

import javax.servlet.http.Part;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;

/**
 * formData File blocks
 * @author wangzihao
 */
public class ServletFilePart implements Part {
    private FileUpload fileUpload;
    private ResourceManager resourceManager;
    private Map<String,String> headerMap;

    public ServletFilePart(FileUpload fileUpload,ResourceManager resourceManager) {
        this.fileUpload = fileUpload;
        this.resourceManager = resourceManager;
    }

    @Override
    public InputStream getInputStream() throws IOException {
        InputStream inputStream;
        if(fileUpload.isInMemory()){
            ByteBuf byteBuf = Unpooled.wrappedBuffer(fileUpload.getByteBuf());
            inputStream = new ByteBufInputStream(byteBuf,false);
        }else {
            inputStream = new FileInputStream(fileUpload.getFile());
        }
        return inputStream;
    }

    @Override
    public String getContentType() {
        return fileUpload.getContentType();
    }

    @Override
    public String getName() {
        return fileUpload.getName();
    }

    @Override
    public String getSubmittedFileName() {
        return fileUpload.getFilename();
    }

    @Override
    public long getSize() {
        return fileUpload.length();
    }

    @Override
    public void write(String fileName) throws IOException {
        resourceManager.writeFile(getInputStream(),"/",fileName);
    }

    @Override
    public void delete() throws IOException {
        if(!fileUpload.isInMemory()) {
            fileUpload.delete();
        }
    }

    @Override
    public String getHeader(String name) {
        return getHeaderMap().get(name);
    }

    @Override
    public Collection<String> getHeaders(String name) {
        String value = getHeaderMap().get(name);
        if(value == null){
            return Collections.emptyList();
        }else {
            return Collections.singletonList(value);
        }
    }

    @Override
    public Collection<String> getHeaderNames() {
        return getHeaderMap().keySet();
    }

    private Map<String,String> getHeaderMap(){
        if(headerMap == null) {
            Map<String,String> headerMap = new CaseInsensitiveKeyMap<>(2);
            headerMap.put(HttpHeaderConstants.CONTENT_DISPOSITION.toString(),
                    HttpHeaderConstants.FORM_DATA + "; " + HttpHeaderConstants.NAME + "=\"" + getName() + "\"; " + HttpHeaderConstants.FILENAME + "=\"" + fileUpload.getFilename());
            headerMap.put(HttpHeaderConstants.CONTENT_LENGTH.toString(), fileUpload.length() + "");
            if (fileUpload.getCharset() != null) {
                headerMap.put(HttpHeaderConstants.CONTENT_TYPE.toString(), HttpHeaderConstants.CHARSET.toString() + '=' + fileUpload.getCharset().name());
            }
            this.headerMap = headerMap;
        }
        return headerMap;
    }

}
