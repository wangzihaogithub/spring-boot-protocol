package com.github.netty.protocol.servlet;

import com.github.netty.core.util.CaseInsensitiveKeyMap;
import com.github.netty.core.util.ResourceManager;
import com.github.netty.protocol.servlet.util.HttpHeaderConstants;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufInputStream;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.http.multipart.Attribute;

import javax.servlet.http.Part;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;

/**
 * formData Text block
 * @author wangzihao
 */
public class ServletTextPart implements Part {
    private Attribute attribute;
    private ResourceManager resourceManager;
    private Map<String,String> headerMap;

    public ServletTextPart(Attribute attribute, ResourceManager resourceManager) {
        this.attribute = attribute;
        this.resourceManager = resourceManager;
    }

    @Override
    public InputStream getInputStream() throws IOException {
        InputStream inputStream;
        if(attribute.isInMemory()){
            ByteBuf byteBuf = Unpooled.wrappedBuffer(attribute.getByteBuf());
            inputStream = new ByteBufInputStream(byteBuf,false);
        }else {
            inputStream = new FileInputStream(attribute.getFile());
        }
        return inputStream;
    }

    @Override
    public String getContentType() {
        return null;
    }

    @Override
    public String getName() {
        return attribute.getName();
    }

    @Override
    public String getSubmittedFileName() {
        return null;
    }

    @Override
    public long getSize() {
        return attribute.length();
    }

    @Override
    public void write(String fileName) throws IOException {
        resourceManager.writeFile(getInputStream(),"/",fileName);
    }

    @Override
    public void delete() throws IOException {
        if(!attribute.isInMemory()) {
            attribute.delete();
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
                    HttpHeaderConstants.FORM_DATA + "; " + HttpHeaderConstants.NAME + "=\"" + getName() + "\"; ");
            headerMap.put(HttpHeaderConstants.CONTENT_LENGTH.toString(), attribute.length() + "");
            if (attribute.getCharset() != null) {
                headerMap.put(HttpHeaderConstants.CONTENT_TYPE.toString(), HttpHeaderConstants.CHARSET.toString() + '=' + attribute.getCharset().name());
            }
            this.headerMap = headerMap;
        }
        return headerMap;
    }

}
