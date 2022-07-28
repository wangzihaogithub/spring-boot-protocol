package com.github.netty.protocol.rtsp;

import com.github.netty.core.AbstractChannelHandler;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.HttpRequest;

/**
 * R:
 * OPTIONS rtsp://192.168.1.152:554/cgi-bin/rtspStream/1 RTSP/1.0
 * CSeq: 2
 * User-Agent: LIVE555 Streaming Media v2012.01.13
 * <p>
 * A:
 * RTSP/1.0 200 OK
 * CSeq: 2
 * Date: Mon, Sep 24 2012 06:41:15 GMT
 * Public: OPTIONS, DESCRIBE, SETUP, TEARDOWN, PLAY, PAUSE, GET_PARAMETER, SET_PARAMETER
 * <p>
 * R:
 * DESCRIBE rtsp://192.168.1.152:554/cgi-bin/rtspStream/1 RTSP/1.0
 * CSeq: 3
 * User-Agent: LIVE555 Streaming Media v2012.01.13
 * Accept: application/sdp
 * <p>
 * A:
 * RTSP/1.0 200 OK
 * CSeq: 3
 * Date: Mon, Sep 24 2012 06:41:16 GMT
 * Content-Base: rtsp://192.168.1.152:554/cgi-bin/rtspStream/1/
 * Content-Type: application/sdp
 * Content-Length: 409
 * <p>
 * v=0
 * o=- 1348468876053786 1 IN IP4 192.168.1.152
 * s=Everfocus Media Server
 * t=0 0
 * c=IN IP4 0.0.0.0
 * a=tool:Everfocus Streaming Media Apr 10 2012 v1.0.1
 * a=type:broadcase
 * a=control:*
 * m=video 0 RTP/AVP 96
 * a=framerate:25
 * a=control:track1
 * a=rtpmap:96 H264/90000
 * a=fmtp:96 packetization-mode=1; profile-level-id=420020; sprop-parameter-sets=Z0IAIJWoFAHmQA==,aM48gA==
 * m=audio 0 RTP/AVP 0
 * a=control:track2
 * <p>
 * R:
 * SETUP rtsp://192.168.1.152:554/cgi-bin/rtspStream/1/track1 RTSP/1.0
 * CSeq: 4
 * User-Agent: LIVE555 Streaming Media v2012.01.13
 * Transport: RTP/AVP;unicast;client_port=3370-3371
 * <p>
 * A:
 * RTSP/1.0 200 OK
 * CSeq: 4
 * Date: Mon, Sep 24 2012 06:41:16 GMT
 * Transport: RTP/AVP;unicast;destination=192.168.1.153;source=192.168.1.152;client_port=3370-3371;server_port=6970-6971
 * Session: 123009;timeout=120
 * <p>
 * R:
 * PLAY rtsp://192.168.1.152:554/cgi-bin/rtspStream/1/ RTSP/1.0
 * CSeq: 5
 * User-Agent: LIVE555 Streaming Media v2012.01.13
 * Session: 123009
 * Range: npt=0.000-
 * <p>
 * A：
 * RTP stream data ......
 * <p>
 * <p>
 * C represents the RTSP client and S represents the RTSP server
 * <p>
 * 1. C- S:OPTION request //Ask S what methods are available
 * 1. S- C:OPTION response //The S response message includes all available methods provided
 * <p>
 * 2. C- S:DESCRIBE request //Request to get the media initialization description information provided by S
 * 2. S- C:DESCRIBE response //S response media initialization description information, mainly SDP
 * <p>
 * 3. C- S:SETUP request //Set the session's properties, as well as the transport mode, to remind S to establish a session
 * 3. S- C:SETUP response //S establishes the session, returns the session identifier, and session-related information
 * <p>
 * 4. C- S:PLAY request //C request play
 * 4. S- C:PLAY response //S replies to the requested information
 * <p>
 * S- C:Send streaming data
 * <p>
 * 5. C- S:TEARDOWN request //C requests to close the session
 * 5. S- C:TEARDOWN response //S should respond to the request
 * <p>
 * Created by wangzihao on 2018/12/5/005.
 */
@ChannelHandler.Sharable
public class RtspServerChannelHandler extends AbstractChannelHandler<HttpRequest, Object> {
    @Override
    protected void onMessageReceived(ChannelHandlerContext ctx, HttpRequest msg) throws Exception {

    }

}
