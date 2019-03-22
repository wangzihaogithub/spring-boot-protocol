package com.github.netty.core;

import io.netty.util.AsciiString;

import java.util.Arrays;
import java.util.Collections;
import java.util.Map;

/**
 * Packet
 * 2019/3/17/017.
 * @author acer01
 */
public class Packet {
    public static final byte TYPE_UNKNOWN = 0;
    public static final byte TYPE_REQUEST = 1;
    public static final byte TYPE_RESPONSE = 2;
    public static final byte TYPE_PING = 3;
    public static final byte TYPE_PONG = 4;

    public static final byte ACK_NO = 0;
    public static final byte ACK_YES = 1;

    public static final byte[] EMPTY_BODY = new byte[0];

    /**
     * Protocol version
     */
    private byte[] protocolVersion;
    /**
     * Packet type
     */
    private int packetType = TYPE_UNKNOWN;
    /**
     * 1 = Need ack
     * 0 = Don't need ack
     */
    private byte ack = ACK_NO;
    /**
     * Fields
     */
    private Map<AsciiString, AsciiString> fieldMap = Collections.emptyMap();
    /**
     * Body
     */
    private byte[] body = EMPTY_BODY;

    public Packet() {}

    public Packet(int packetType) {
        this.packetType = packetType;
    }

    public byte[] getProtocolVersion() {
        return protocolVersion;
    }

    public void setProtocolVersion(byte[] protocolVersion) {
        this.protocolVersion = protocolVersion;
    }

    public int getAck() {
        return ack;
    }

    public void setAck(byte ack) {
        this.ack = ack;
    }

    public int getPacketType() {
        return packetType;
    }

    public void setPacketType(int packetType) {
        this.packetType = packetType;
    }

    public byte[] getBody() {
        return body;
    }

    public void setBody(byte[] body) {
        this.body = body;
    }

    public Map<AsciiString, AsciiString> getFieldMap() {
        return fieldMap;
    }

    public void setFieldMap(Map<AsciiString, AsciiString> fieldMap) {
        this.fieldMap = fieldMap;
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder("{");
        sb.append("\"protocolHeader\":")
                .append(Arrays.toString(protocolVersion));
        sb.append(",\"packetType\":")
                .append(packetType);
        sb.append(",\"ack\":")
                .append(ack);
        sb.append(",\"bodyLength\":")
                .append((getBody() == null ? "null" : getBody().length));
        sb.append(",\"fieldMap\":")
                .append(fieldMap);
        sb.append('}');
        return sb.toString();
    }
}