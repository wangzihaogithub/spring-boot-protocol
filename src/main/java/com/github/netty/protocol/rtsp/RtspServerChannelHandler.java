package com.github.netty.protocol.rtsp;

import com.github.netty.core.AbstractChannelHandler;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.HttpRequest;

/**
     R:
     OPTIONS rtsp://192.168.1.152:554/cgi-bin/rtspStream/1 RTSP/1.0
     CSeq: 2
     User-Agent: LIVE555 Streaming Media v2012.01.13

     A:
     RTSP/1.0 200 OK
     CSeq: 2
     Date: Mon, Sep 24 2012 06:41:15 GMT
     Public: OPTIONS, DESCRIBE, SETUP, TEARDOWN, PLAY, PAUSE, GET_PARAMETER, SET_PARAMETER

     R:
     DESCRIBE rtsp://192.168.1.152:554/cgi-bin/rtspStream/1 RTSP/1.0
     CSeq: 3
     User-Agent: LIVE555 Streaming Media v2012.01.13
     Accept: application/sdp

     A:
     RTSP/1.0 200 OK
     CSeq: 3
     Date: Mon, Sep 24 2012 06:41:16 GMT
     Content-Base: rtsp://192.168.1.152:554/cgi-bin/rtspStream/1/
     Content-Type: application/sdp
     Content-Length: 409

     v=0
     o=- 1348468876053786 1 IN IP4 192.168.1.152
     s=Everfocus Media Server
     t=0 0
     c=IN IP4 0.0.0.0
     a=tool:Everfocus Streaming Media Apr 10 2012 v1.0.1
     a=type:broadcase
     a=control:*
     m=video 0 RTP/AVP 96
     a=framerate:25
     a=control:track1
     a=rtpmap:96 H264/90000
     a=fmtp:96 packetization-mode=1; profile-level-id=420020; sprop-parameter-sets=Z0IAIJWoFAHmQA==,aM48gA==
     m=audio 0 RTP/AVP 0
     a=control:track2

     R:
     SETUP rtsp://192.168.1.152:554/cgi-bin/rtspStream/1/track1 RTSP/1.0
     CSeq: 4
     User-Agent: LIVE555 Streaming Media v2012.01.13
     Transport: RTP/AVP;unicast;client_port=3370-3371

     A:
     RTSP/1.0 200 OK
     CSeq: 4
     Date: Mon, Sep 24 2012 06:41:16 GMT
     Transport: RTP/AVP;unicast;destination=192.168.1.153;source=192.168.1.152;client_port=3370-3371;server_port=6970-6971
     Session: 123009;timeout=120

     R:
     PLAY rtsp://192.168.1.152:554/cgi-bin/rtspStream/1/ RTSP/1.0
     CSeq: 5
     User-Agent: LIVE555 Streaming Media v2012.01.13
     Session: 123009
     Range: npt=0.000-

     A：
     RTP stream data ......


     C表示rtsp客户端, S表示rtsp服务端

     1. C->S:OPTION request //询问S有哪些方法可用
     1. S->C:OPTION response //S回应信息中包括提供的所有可用方法

     2. C->S:DESCRIBE request //要求得到S提供的媒体初始化描述信息
     2. S->C:DESCRIBE response //S回应媒体初始化描述信息，主要是sdp

     3. C->S:SETUP request //设置会话的属性，以及传输模式，提醒S建立会话
     3. S->C:SETUP response //S建立会话，返回会话标识符，以及会话相关信息

     4. C->S:PLAY request //C请求播放
     4. S->C:PLAY response //S回应该请求的信息

     S->C:发送流媒体数据

     5. C->S:TEARDOWN request //C请求关闭会话
     5. S->C:TEARDOWN response //S回应该请求

 * Created by acer01 on 2018/12/5/005.
 */
@ChannelHandler.Sharable
public class RtspServerChannelHandler extends AbstractChannelHandler<HttpRequest,Object> {
    @Override
    protected void onMessageReceived(ChannelHandlerContext ctx, HttpRequest msg) throws Exception {

    }

}
