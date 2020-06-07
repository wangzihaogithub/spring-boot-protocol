package com.github.netty.protocol.servlet;

import com.github.netty.core.util.CompositeByteBufX;
import com.github.netty.core.util.HttpHeaderUtil;
import com.github.netty.core.util.Recyclable;
import com.github.netty.core.util.ReflectUtil;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.DecoderResult;
import io.netty.handler.codec.http.*;

import java.lang.reflect.Method;

/**
 * NettyHttpResponse
 * @author wangzihao
 * 2018/7/28/028
 */
public class NettyHttpResponse implements HttpResponse, Recyclable {
    public static final HttpResponseStatus DEFAULT_STATUS = HttpResponseStatus.OK;

    private DecoderResult decoderResult;
    private HttpVersion version;
    private HttpHeaders headers;
    private HttpResponseStatus status;
    private CompositeByteBufX content;
    private LastHttpContent lastHttpContent;

    public NettyHttpResponse() {
        this.headers = new DefaultHttpHeaders(false);
        this.version = HttpVersion.HTTP_1_1;
        this.status = DEFAULT_STATUS;
        this.decoderResult = DecoderResult.SUCCESS;
    }

    /**
     * enableTransferEncodingChunked
     * @return LastHttpContent
     */
    public LastHttpContent enableTransferEncodingChunked(){
        if(!isTransferEncodingChunked()){
            HttpHeaderUtil.setTransferEncodingChunked(headers,true);
            lastHttpContent = new DefaultLastHttpContent(Unpooled.EMPTY_BUFFER,false);
        }
        return lastHttpContent;
    }

    public boolean isTransferEncodingChunked(){
        return HttpHeaderUtil.isTransferEncodingChunked(headers);
    }

    @Override
    public HttpResponseStatus getStatus() {
        return status;
    }

    @Override
    public HttpVersion getProtocolVersion() {
        return version;
    }

    @Override
    public DecoderResult getDecoderResult() {
        return decoderResult;
    }

    public HttpResponseStatus status() {
        return status;
    }

    public HttpVersion protocolVersion() {
        return version;
    }

    public DecoderResult decoderResult() {
        return decoderResult;
    }

    @Override
    public NettyHttpResponse setStatus(HttpResponseStatus status) {
        this.status = status;
        return this;
    }

    public LastHttpContent getLastHttpContent() {
        return lastHttpContent;
    }

    public CompositeByteBufX content() {
        return content;
    }

    public int refCnt() {
        return content.refCnt();
    }

    public NettyHttpResponse retain() {
        content.retain();
        return this;
    }

    public NettyHttpResponse retain(int increment) {
        content.retain(increment);
        return this;
    }

    public NettyHttpResponse touch() {
//        content.touch();
        Method method = ReflectUtil.getAccessibleMethod(content.getClass(), "touch");
        try {
            method.invoke(content);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return this;
    }

    public NettyHttpResponse touch(Object hint) {
//        content.touch(hint);
        Method method = ReflectUtil.getAccessibleMethod(content.getClass(), "touch",Object.class);
        try {
            method.invoke(content,hint);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return this;
    }

    public boolean release() {
        return content.release();
    }

    public boolean release(int decrement) {
        return content.release(decrement);
    }

    public NettyHttpResponse copy() {
        return replace(content().copy());
    }

    public NettyHttpResponse duplicate() {
        return replace(content().duplicate());
    }

    public NettyHttpResponse retainedDuplicate() {
        Method method = ReflectUtil.getAccessibleMethod(content.getClass(), "retainedDuplicate");
        ByteBuf byteBuf;
        try {
            byteBuf = (ByteBuf) method.invoke(content);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return replace(byteBuf);
    }

    public NettyHttpResponse replace(ByteBuf content) {
        NettyHttpResponse response = new NettyHttpResponse();
        if(content instanceof CompositeByteBufX){
             response.content = (CompositeByteBufX) content;
        }else {
            response.content = new CompositeByteBufX();
        }
        response.version = this.version;
        response.status = this.status;
        response.headers = this.headers;
        response.decoderResult = this.decoderResult;
        return response;
    }

    @Override
    public NettyHttpResponse setProtocolVersion(HttpVersion version) {
        this.version = version;
        return this;
    }

    public void setContent(ByteBuf content) {
        if(content instanceof CompositeByteBufX){
            this.content = (CompositeByteBufX) content;
        }else {
            this.content = new CompositeByteBufX();
            this.content.addComponent(content);
        }
    }

    @Override
    public HttpHeaders headers() {
        return headers;
    }

    @Override
    public void setDecoderResult(DecoderResult result) {
        this.decoderResult = result;
    }

    @Override
    public void recycle() {
        this.content = null;
        this.headers.clear();
        this.version = HttpVersion.HTTP_1_1;
        this.status = DEFAULT_STATUS;
        this.lastHttpContent = null;
        this.decoderResult = DecoderResult.SUCCESS;
    }

    @Override
    public int hashCode() {
        int result = decoderResult != null ? decoderResult.hashCode() : 0;
        result = 31 * result + (version != null ? version.hashCode() : 0);
        result = 31 * result + (headers != null ? headers.hashCode() : 0);
        result = 31 * result + (status != null ? status.hashCode() : 0);
        result = 31 * result + (content != null ? content.hashCode() : 0);
        return result;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o){
            return true;
        }
        if (o == null || getClass() != o.getClass()){
            return false;
        }

        NettyHttpResponse that = (NettyHttpResponse) o;
        if (!decoderResult.equals(that.decoderResult)){
            return false;
        }
        if (!version.equals(that.version)){
            return false;
        }
        if (!status.equals(that.status)){
            return false;
        }
        if (!headers.equals(that.headers)){
            return false;
        }
        return content.equals(that.content);
    }

    @Override
    public String toString() {
        return "NettyHttpResponse{" +
                "content=" + content +
                ", decoderResult=" + decoderResult +
                ", version=" + version +
                ", headers=" + headers +
                ", status=" + status +
                '}';
    }

}
