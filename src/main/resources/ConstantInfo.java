package com.github.netty.protocol.rtsp;

import java.nio.charset.StandardCharsets;

public interface ConstantInfo {

    void readInfo(TestJvm.ClassReader reader);

    class ConstantStringInfo implements ConstantInfo{
        TestJvm.ConstantPool cp;
        int stringIndex;

        public String string()  {
            return cp.getUtf8(stringIndex);
        }

        @Override
        public void readInfo(TestJvm.ClassReader reader) {
            stringIndex = reader.readUint16();
        }
    }


    class ConstantDoubleInfo implements ConstantInfo {
        long val;

        public long value()  {
            return val;
        }

        @Override
        public void readInfo(TestJvm.ClassReader reader) {
            val = reader.readUint64();
        }

    }


    public class ConstantIntegerInfo implements ConstantInfo {
        @Override
        public void readInfo(TestJvm.ClassReader reader) {

        }
    }

    public class ConstantFloatInfo implements ConstantInfo {
        @Override
        public void readInfo(TestJvm.ClassReader reader) {

        }
    }

    public class ConstantLongInfo implements ConstantInfo {
        @Override
        public void readInfo(TestJvm.ClassReader reader) {

        }
    }

    public class ConstantUtf8Info implements ConstantInfo {
        String str ;
        @Override
        public void readInfo(TestJvm.ClassReader reader) {
            int length = reader.readUint16();
            byte[] bytes = reader.readBytes(length);
            str = new String(bytes, StandardCharsets.UTF_8);
        }
    }

    public class ConstantClassInfo implements ConstantInfo {
         int nameIndex;

        @Override
        public void readInfo(TestJvm.ClassReader reader) {
            nameIndex = reader.readUint16();
        }
    }

    public class ConstantFieldrefInfo implements ConstantInfo {
        @Override
        public void readInfo(TestJvm.ClassReader reader) {

        }
    }

    public class ConstantMethodrefInfo implements ConstantInfo {
        @Override
        public void readInfo(TestJvm.ClassReader reader) {

        }
    }

    public class ConstantInterfaceMethodrefInfo implements ConstantInfo {
        @Override
        public void readInfo(TestJvm.ClassReader reader) {

        }
    }

    public class ConstantNameAndTypeInfo implements ConstantInfo {
         int nameIndex;
         int descriptorIndex;

        @Override
        public void readInfo(TestJvm.ClassReader reader) {
            nameIndex = reader.readUint16();
            descriptorIndex = reader.readUint16();
        }
    }

    public class ConstantMethodTypeInfo implements ConstantInfo {
        @Override
        public void readInfo(TestJvm.ClassReader reader) {

        }
    }

    public class ConstantMethodHandleInfo implements ConstantInfo {
        @Override
        public void readInfo(TestJvm.ClassReader reader) {

        }
    }

    public class ConstantInvokeDynamicInfo implements ConstantInfo {
        @Override
        public void readInfo(TestJvm.ClassReader reader) {

        }
    }
}
