/*
 * Copyright 2016 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */

package com.github.netty.protocol.mysql;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.util.AsciiString;

import java.nio.charset.Charset;
import java.util.EnumSet;
import java.util.Set;

public final class CodecUtils {
    private static final byte TERMINAL = 0x00;
    private static final int NULL_VALUE = 0xFB;
    private static final int SHORT_VALUE = 0xFC;
    private static final int MEDIUM_VALUE = 0xFD;
    private static final int LONG_VALUE = 0xFE;

    public static long readLengthEncodedInteger(ByteBuf buf) {
        return readLengthEncodedInteger(buf, buf.readUnsignedByte());
    }

    // https://dev.mysql.com/doc/internals/en/integer.html
    public static long readLengthEncodedInteger(ByteBuf buffer, int firstUnsignedByte) {
        switch (firstUnsignedByte) {
            case NULL_VALUE:
                return -1;
            case SHORT_VALUE:
                return buffer.readUnsignedShortLE();
            case MEDIUM_VALUE:
                return buffer.readUnsignedMediumLE();
            case LONG_VALUE:
                return buffer.readLongLE();
            default:
                return firstUnsignedByte;
        }
    }

    public static AsciiString readNullTerminatedString(ByteBuf buffer) {
        int len = buffer.bytesBefore(TERMINAL);
        byte[] bytes = new byte[len];
        buffer.readBytes(bytes);
        buffer.readByte();
        return new AsciiString(bytes, false);
    }

    public static String readNullTerminatedString(ByteBuf buffer, Charset charset) {
        int len = buffer.bytesBefore(TERMINAL);
        String s = buffer.readCharSequence(len, charset).toString();
        buffer.readByte();
        return s;
    }

    public static int findNullTermLen(ByteBuf buf) {
        int termIdx = buf.indexOf(buf.readerIndex(), buf.capacity(), (byte) 0);
        if (termIdx < 0) {
            return -1;
        }
        return termIdx - buf.readerIndex();
    }

    public static String readFixedLengthString(ByteBuf buf, int length, Charset charset) {
        if (length < 0) {
            return null;
        }
        String s = buf.toString(buf.readerIndex(), length, charset);
        buf.readerIndex(buf.readerIndex() + length);
        return s;
    }

    public static String readLengthEncodedString(ByteBuf buf, Charset charset) {
        long len = readLengthEncodedInteger(buf);
        return readFixedLengthString(buf, (int) len, charset);
    }

    public static String readLengthEncodedString(ByteBuf buf, int firstByte, Charset charset) {
        long len = readLengthEncodedInteger(buf, firstByte);
        return readFixedLengthString(buf, (int) len, charset);
    }

    public static <E extends Enum<E>> EnumSet<E> readShortEnumSet(ByteBuf buf, Class<E> enumClass) {
        return toEnumSet(enumClass, buf.readUnsignedShortLE());
    }

    public static <E extends Enum<E>> EnumSet<E> readIntEnumSet(ByteBuf buf, Class<E> enumClass) {
        return toEnumSet(enumClass, buf.readUnsignedIntLE());
    }

    public static <E extends Enum<E>> EnumSet<E> toEnumSet(Class<E> enumClass, long vector) {
        EnumSet<E> set = EnumSet.noneOf(enumClass);
        for (E e : enumClass.getEnumConstants()) {
            long mask = 1 << e.ordinal();
            if ((mask & vector) != 0) {
                set.add(e);
            }
        }
        return set;
    }

//    private static <E> E toEnum(Class<E> enumClass, int i) {
//        E[] enumConstants = enumClass.getEnumConstants();
//        if (i > enumConstants.length) {
//            throw new IndexOutOfBoundsException(String.format(
//                    "%d is too large of an ordinal to convert to the enum %s",
//                    i, enumClass.getName()));
//        }
//        return enumConstants[i];
//    }

    public static <E extends Enum<E>> long toLong(Set<E> set) {
        long vector = 0;
        for (E e : set) {
            if (e.ordinal() >= Long.SIZE) {
                throw new IllegalArgumentException("The enum set is too large to fit in a bit vector: " + set);
            }
            vector |= 1L << e.ordinal();
        }
        return vector;
    }

    public static void writeLengthEncodedInt(ByteBuf buf, Long n) {
        if (n == null) {
            buf.writeByte(NULL_VALUE);
        } else if (n < 0) {
            throw new IllegalArgumentException("Cannot encode a negative length: " + n);
        } else if (n < NULL_VALUE) {
            buf.writeByte(n.intValue());
        } else if (n < 0xFFFF) {
            buf.writeByte(SHORT_VALUE);
            buf.writeShortLE(n.intValue());
        } else if (n < 0xFFFFFF) {
            buf.writeByte(MEDIUM_VALUE);
            buf.writeMediumLE(n.intValue());
        } else {
            buf.writeByte(LONG_VALUE);
            buf.writeLongLE(n);
        }
    }

    public static void writeUB2(ByteBuf buffer, int i) {
        buffer.writeByte((byte) (i & 0xff));
        buffer.writeByte((byte) (i >>> 8));
    }

    public static void writeUB3(ByteBuf buffer, int i) {
        buffer.writeByte((byte) (i & 0xff));
        buffer.writeByte((byte) (i >>> 8));
        buffer.writeByte((byte) (i >>> 16));
    }

    public static void writeLengthEncodedString(ByteBuf buf, CharSequence sequence, Charset charset) {
        ByteBuf tmpBuf = Unpooled.buffer();
        try {
            tmpBuf.writeCharSequence(sequence, charset);
            writeLengthEncodedInt(buf, (long) tmpBuf.readableBytes());
            buf.writeBytes(tmpBuf);
        } finally {
            tmpBuf.release();
        }
    }

    public static void writeNullTerminatedString(ByteBuf buf, CharSequence sequence, Charset charset) {
        if (sequence != null) {
            buf.writeCharSequence(sequence, charset);
        }
        buf.writeByte(0);
    }

}
