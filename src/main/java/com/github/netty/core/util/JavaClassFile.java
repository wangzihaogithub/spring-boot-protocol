package com.github.netty.core.util;

import java.io.*;
import java.lang.instrument.IllegalClassFormatException;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * java类文件
 *
 * ClassFile {
 *     u4             magic;
 *     u2             minor_version;
 *     u2             major_version;
 *     u2             constant_pool_count;
 *     cp_info        constant_pool[constant_pool_count-1];
 *     u2             access_flags;
 *     u2             this_class;
 *     u2             super_class;
 *     u2             interfaces_count;
 *     u2             interfaces[interfaces_count];
 *     u2             fields_count;
 *     field_info     fields[fields_count];
 *     u2             methods_count;
 *     method_info    methods[methods_count];
 *     u2             attributes_count;
 *     attribute_info attributes[attributes_count];
 * }
 *
 * class文件结构.官方文档 https://docs.oracle.com/javase/specs/jvms/se8/html/jvms-4.html
 * 参考文件 com.sun.org.apache.bcel.internal.classfile.ClassParser
 * @author wangzihao
 */
public class JavaClassFile {
    private static final Attribute[] EMPTY_ATTRIBUTES = {};
    private static final Attribute.CodeException[] EMPTY_CODE_EXCEPTIONS = {};
    private static final Attribute.LineNumber[] EMPTY_LINE_NUMBER_TABLE = {};
    private static final Attribute.LocalVariable[] EMPTY_LOCAL_VARIABLE_TABLE = {};
    private static final Attribute.InnerClass[] EMPTY_INNER_CLASSES = {};
    private static final Attribute.StackMapEntry[] EMPTY_STACK_MAP_ENTRY = {};
    private static final Attribute.StackMapFrame[] EMPTY_STACK_MAP_FRAME = {};
    private static final Attribute.StackMapType[] EMPTY_STACK_MAP_TYPE = {};
    private static final int[] EMPTY_EXCEPTION_INDEX_TABLE = {};

    private long minorVersion;
    private JavaVersion majorVersion;
    private ConstantPool constantPool;
    private int accessFlags;
    private int thisClassIndex;
    private int superClassIndex;
    private int[] interfacesIndex;
    private Member[] fields;
    private Member[] methods;
    private Attribute[] attributes;

    public JavaClassFile(String path, String name) throws ClassNotFoundException, IOException, IllegalClassFormatException {
        this(new ClassReader(path, name));
    }

    public JavaClassFile(InputStream in) throws IOException, IllegalClassFormatException {
        this(new ClassReader(in));
    }

    public JavaClassFile(byte[] codes) throws IllegalClassFormatException {
        this(new ClassReader(codes));
    }

    public JavaClassFile(ClassReader reader) throws IllegalClassFormatException {
        int magic = reader.readInt32();
        //第一位必须是 cafe babe
        if (magic != 0xCAFEBABE){
            throw new IllegalClassFormatException("is not a Java .class file");
        }
        this.minorVersion = reader.readUint16();
        this.majorVersion = JavaVersion.valueOf(reader.readUint16());
        this.constantPool = readConstantPool(reader);
        this.accessFlags = readAccessFlags(reader);
        this.thisClassIndex = reader.readUint16();
        this.superClassIndex = reader.readUint16();
        this.interfacesIndex = reader.readUint16s();
        this.fields = readMembers(reader);
        this.methods = readMembers(reader);
        this.attributes = readAttributes(reader);
        reader.close();
    }

    private int readAccessFlags(ClassReader reader) {
        int accessFlags = reader.readUint16();
        if((accessFlags & Modifier.INTERFACE) != 0) {
            accessFlags |= Modifier.ABSTRACT;
        }
        return accessFlags;
    }

    private ConstantPool readConstantPool(ClassReader reader) {
        int constantPoolCount = reader.readUint16();
        return new ConstantPool(constantPoolCount,reader);
    }

    private Member[] readMembers(ClassReader reader)  {
        int memberCount = reader.readUint16();
        Member[] members = new Member[memberCount];
        for (int i =0; i<members.length; i++) {
            members[i] = new Member();
            members[i].accessFlags = reader.readUint16();
            members[i].nameIndex = reader.readUint16();
            members[i].descriptorIndex = reader.readUint16();
            members[i].name = constantPool.getUtf8(members[i].nameIndex);
            members[i].descriptorName = constantPool.getUtf8(members[i].descriptorIndex);
            members[i].attributes = readAttributes(reader);
        }
        return members;
    }

    private Attribute[] readAttributes(ClassReader reader)  {
        int attributesCount = reader.readUint16();
        if(attributesCount == 0){
            return EMPTY_ATTRIBUTES;
        }else {
            Attribute[] attributes = new Attribute[attributesCount];
            for (int i = 0; i < attributes.length; i++) {
                int attrNameIndex = reader.readUint16();
                attributes[i] = new Attribute(attrNameIndex, reader.readInt32(), reader);
            }
            return attributes;
        }
    }
    public JavaVersion getMajorVersion() {
        return majorVersion;
    }
    public Attribute[] getAttributes() {
        return attributes;
    }
    public ConstantPool getConstantPool() {
        return constantPool;
    }
    public int getAccessFlags() {
        return accessFlags;
    }
    public Member[] getFields() {
        return fields;
    }
    public Member[] getMethods() {
        return methods;
    }
    public String getThisClassName() {
        return constantPool.getClassName(thisClassIndex);
    }
    public String getSuperClassName() {
        if (superClassIndex != 0 ){
            return constantPool.getClassName(superClassIndex);
        }
        return "";
    }
    public String[] getInterfaceNames() {
        String[] interfaceNames = new String[interfacesIndex.length];
        for (int i=0; i<interfaceNames.length; i++){
            interfaceNames[i] = constantPool.getClassName(interfacesIndex[i]);
        }
        return interfaceNames;
    }

    public Member getMethod(String methodName,Class<?>[] parameterTypes,Class<?> returnType){
        String methodDescriptor = Member.Type.getMethodDescriptor(parameterTypes,returnType);
        for(Member method : methods){
            if(methodName.equals(method.name())
                && methodDescriptor.equals(method.descriptorName())){
                return method;
            }
        }
        return null;
    }

    @Override
    public String toString() {
        return new StringJoiner(",", "{", "}")
                .add("\"majorVersion\":\"" + majorVersion + "\"")
                .add("\"minorVersion\":" + minorVersion)
                .add("\"accessFlags\":\"" + Modifier.toString(accessFlags) + "\"")
                .add("\"thisClassIndex\":" + thisClassIndex)
                .add("\"thisClassName\":\"" + getThisClassName() + "\"")
                .add("\"superClassIndex\":" + superClassIndex)
                .add("\"superClassName\":\"" + getSuperClassName() + "\"")
                .add("\"interfaces\":" + toJsonArray(getInterfaceNames()))
                .add("\"fields\":" + toJsonArray(getFields()))
                .add("\"methods\":" + toJsonArray(getMethods()))
                .add("\"attributes\":" + toJsonArray(attributes))
                .add("\"constantPool\":" + toJsonArray(constantPool.constants))
                .add("\"constantPoolDataLength\":" +
                        Arrays.stream(constantPool.constants)
                                .filter(Objects::nonNull)
                                .mapToInt(ConstantPool.ConstantInfo::length)
                                .sum())
                .toString();
    }

    public static String toJsonArray(Object array) {
        if (array == null) {
            return "null";
        }
        if (array instanceof Object[]) {
            StringJoiner joiner = new StringJoiner(",", "[", "]");
            for (Object e : (Object[]) array) {
                if (e instanceof Number) {
                    joiner.add(e.toString());
                } else if (e == null) {
                    joiner.add("null");
                } else if (e.getClass().isArray()) {
                    joiner.add(toJsonArray(array));
                } else if (e instanceof CharSequence) {
                    joiner.add("\"" + e + "\"");
                } else {
                    joiner.add(e.toString());
                }
            }
            return joiner.toString();
        }
        if (array instanceof byte[]) {
            return Arrays.toString((byte[]) array);
        }
        if (array instanceof int[]) {
            return Arrays.toString((int[]) array);
        }
        if (array instanceof short[]) {
            return Arrays.toString((short[]) array);
        }
        if (array instanceof long[]) {
            return Arrays.toString((long[]) array);
        }
        if (array instanceof double[]) {
            return Arrays.toString((double[]) array);
        }
        return array.toString();
    }

    public enum JavaVersion{
        /**
         * Java ClassFile versions (the minor version is stored in the 16 most
         * significant bits, and the
         * major version in the 16 least significant bits).
         */
        V1_1(3 << 16 | 45),
        V1_2(0 << 16 | 46),
        V1_3(0 << 16 | 47),
        V1_4(0 << 16 | 48),
        V1_5(0 << 16 | 49),
        V1_6(0 << 16 | 50),
        V1_7(0 << 16 | 51),
        V1_8(0 << 16 | 52),
        V9(0 << 16 | 53),
        V10(0 << 16 | 54),
        V11(0 << 16 | 55),
        V12(0 << 16 | 56);

        private long major;
        JavaVersion(long major) {
            this.major = major;
        }

        public long getMajor() {
            return major;
        }

        public static JavaVersion valueOf(long major){
            for(JavaVersion version : values()){
                if(version.major == major){
                    return version;
                }
            }
            throw new IllegalArgumentException("major");
        }
    }

    public static class ConstantPool {
        private ConstantInfo[] constants;
        public ConstantPool(int constantPoolCount, ClassReader reader) {
            constants = new ConstantInfo[constantPoolCount];
            // The constant_pool table is indexed from 1 to constant_pool_count - 1.
            for (int i = 1; i < constantPoolCount; i++) {
                ConstantInfo constantInfo = readConstantInfo(i,reader);
                constants[i] = constantInfo;
                // http://docs.oracle.com/javase/specs/jvms/se8/html/jvms-4.html#jvms-4.4.5
                // All 8-byte constants take up two entries in the constant_pool table of the class file.
                // If a CONSTANT_Long_info or CONSTANT_Double_info structure is the item in the constant_pool
                // table at index n, then the next usable item in the pool is located at index n+2.
                // The constant_pool index n+1 must be valid but is considered unusable.
                if (constantInfo instanceof ConstantDoubleInfo
                        ||constantInfo instanceof ConstantLongInfo) {
                    i++;
                }
            }
        }

        private ConstantInfo readConstantInfo(int index,ClassReader reader){
            int tag = reader.readUint8();
            ConstantInfo constantInfo;
            switch (tag) {
                case ConstantInfo.CONSTANT_INTEGER:
                    constantInfo = new ConstantIntegerInfo(index,reader);
                    break;
                case ConstantInfo.CONSTANT_FLOAT:
                    constantInfo = new ConstantFloatInfo(index,reader);
                    break;
                case ConstantInfo.CONSTANT_LONG:
                    constantInfo = new ConstantLongInfo(index,reader);
                    break;
                case ConstantInfo.CONSTANT_DOUBLE:
                    constantInfo = new ConstantDoubleInfo(index,reader);
                    break;
                case ConstantInfo.CONSTANT_UTF8:
                    constantInfo = new ConstantUtf8Info(index,reader);
                    break;
                case ConstantInfo.CONSTANT_STRING:
                    constantInfo = new ConstantStringInfo(index,reader);
                    break;
                case ConstantInfo.CONSTANT_CLASS:
                    constantInfo = new ConstantClassInfo(index,reader);
                    break;
                case ConstantInfo.CONSTANT_FIELD_REF:
                    constantInfo = new ConstantFieldRefInfo(index,new ConstantMemberRefInfo(reader));
                    break;
                case ConstantInfo.CONSTANT_METHOD_REF:
                    constantInfo = new ConstantMethodRefInfo(index,new ConstantMemberRefInfo(reader));
                    break;
                case ConstantInfo.CONSTANT_INTERFACE_METHOD_REF:
                    constantInfo = new ConstantInterfaceMethodRefInfo(index,new ConstantMemberRefInfo(reader));
                    break;
                case ConstantInfo.CONSTANT_NAME_AND_TYPE:
                    constantInfo = new ConstantNameAndTypeInfo(index,reader);
                    break;
                case ConstantInfo.CONSTANT_METHOD_HANDLE:
                    constantInfo = new ConstantMethodHandleInfo(index,reader);
                    break;
                case ConstantInfo.CONSTANT_INVOKE_DYNAMIC:
                    constantInfo = new ConstantInvokeDynamicInfo(index,reader);
                    break;
                case ConstantInfo.CONSTANT_METHOD_TYPE:
                    constantInfo = new ConstantMethodTypeInfo(index,reader);
                    break;
                default: {
                    //用户可以自定义解析器
                    System.out.println("Unkown constant pool tag: " + tag);
                    constantInfo = new ConstantUnkownInfo(index,tag);
                    break;
                }
            }
            return constantInfo;
        }

        public ConstantInfo getConstantInfo(int index)  {
            if(index >= constants.length){
                throw new ArrayIndexOutOfBoundsException(index);
            }
            ConstantInfo cpInfo = constants[index];
            if(cpInfo == null){
                System.out.println("Bad constant pool index: "+ index);
            }
            return cpInfo;
        }

        public ConstantNameAndTypeInfo getNameAndType(int index) {
            return (ConstantNameAndTypeInfo) getConstantInfo(index);
        }
        public String getClassName(int index) {
            return getUtf8(((ConstantClassInfo)getConstantInfo(index)).nameIndex);
        }
        public String getUtf8(int stringIndex) {
            return((ConstantUtf8Info)getConstantInfo(stringIndex)).value();
        }
        public String getClassNameForToString(int index) {
            if(index == 0){
                return null;
            }
            return getUtf8ForToString(((ConstantClassInfo)getConstantInfo(index)).nameIndex);
        }
        public String getUtf8ForToString(int stringIndex) {
            if(stringIndex == 0){
                return null;
            }
            return ((ConstantUtf8Info) getConstantInfo(stringIndex)).valueToString();
        }
        public int getInteger(int index) {
            return((ConstantIntegerInfo)getConstantInfo(index)).value();
        }
        public double getDouble(int index) {
            return((ConstantDoubleInfo)getConstantInfo(index)).value();
        }
        public int getFloat(int index) {
            return((ConstantFloatInfo)getConstantInfo(index)).value();
        }
        public long getLong(int index) {
            return((ConstantLongInfo)getConstantInfo(index)).value();
        }
        public interface ConstantInfo {
            int CONSTANT_UTF8 = 1;
            int CONSTANT_INTEGER = 3;
            int CONSTANT_FLOAT = 4;
            int CONSTANT_LONG = 5;
            int CONSTANT_DOUBLE = 6;
            int CONSTANT_CLASS = 7;
            int CONSTANT_STRING = 8;
            int CONSTANT_FIELD_REF = 9;
            int CONSTANT_METHOD_REF = 10;
            int CONSTANT_INTERFACE_METHOD_REF = 11;
            int CONSTANT_NAME_AND_TYPE = 12;
            int CONSTANT_METHOD_HANDLE = 15;
            int CONSTANT_METHOD_TYPE = 16;
            int CONSTANT_INVOKE_DYNAMIC = 18;

            String name();
            int length();
        }

        public class ConstantStringInfo implements ConstantInfo {
            private int stringIndex;
            private int index;
            public ConstantStringInfo(int index,ClassReader reader) {
                this.index = index;
                this.stringIndex = reader.readUint16();
            }
            public String value() {
                return getUtf8(stringIndex);
            }

            @Override
            public String name() {
                return "String";
            }

            @Override
            public int length() {
                return 2;
            }

            @Override
            public String toString() {
                return new StringJoiner(",","{","}")
                        .add("\"index\":"+ index)
                        .add("\"constant\":\""+name()+"\"")
                        .add("\"stringIndex\":"+stringIndex)
                        .add("\"value\":\""+ getUtf8ForToString(stringIndex)+"\"")
                        .toString();
            }
        }

        public class ConstantDoubleInfo implements ConstantInfo {
            private byte[] value;
            private int index;
            public ConstantDoubleInfo(int index,ClassReader reader) {
                this.index = index;
                value = reader.readInt8s(8);
            }

            public double value() {
                long data = ((long) value[0] & 0xff) << 56 |
                            ((long) value[1] & 0xff) << 48 |
                            ((long) value[2] & 0xff) << 40 |
                            ((long) value[3] & 0xff) << 32 |
                            ((long) value[4] & 0xff) << 24 |
                            ((long) value[5] & 0xff) << 16 |
                            ((long) value[6] & 0xff) <<  8 |
                            (long) value[7] & 0xff;
                return Double.longBitsToDouble(data);
            }

            @Override
            public String name() {
                return "Double";
            }
            @Override
            public int length() {
                return 8;
            }
            @Override
            public String toString() {
                return new StringJoiner(",","{","}")
                        .add("\"index\":"+ index)
                        .add("\"constant\":\""+name()+"\"")
                        .add("\"length\":"+length())
                        .add("\"value\":"+toJsonArray(value))
                        .toString();
            }
        }

        public class ConstantIntegerInfo implements ConstantInfo {
            private int value;
            private int index;
            public ConstantIntegerInfo(int index,ClassReader reader) {
                this.index = index;
                this.value = reader.readInt32();
            }
            @Override
            public String name() {
                return "Integer";
            }
            public int value() {
                return value;
            }
            @Override
            public int length() {
                return 4;
            }
            @Override
            public String toString() {
                return new StringJoiner(",","{","}")
                        .add("\"index\":"+ index)
                        .add("\"constant\":\""+name()+"\"")
                        .add("\"length\":"+length())
                        .add("\"value\":"+value)
                        .toString();
            }

        }

        public class ConstantFloatInfo implements ConstantInfo {
            private int value;
            private int index;
            public ConstantFloatInfo(int index,ClassReader reader) {
                this.index = index;
                value = reader.readInt32();
            }
            public int value() {
                return value;
            }
            @Override
            public String name() {
                return "Float";
            }
            @Override
            public int length() {
                return 4;
            }
            @Override
            public String toString() {
                return new StringJoiner(",","{","}")
                        .add("\"index\":"+ index)
                        .add("\"constant\":\""+name()+"\"")
                        .add("\"length\":"+length())
                        .add("\"value\":"+value)
                        .toString();
            }
        }

        public class ConstantLongInfo implements ConstantInfo {
            private byte[] value;
            private int index;
            public ConstantLongInfo(int index,ClassReader reader) {
                this.index = index;
                value = reader.readInt8s(8);
            }

            public long value() {
                long data = ((long) value[0] & 0xff) << 56 |
                        ((long) value[1] & 0xff) << 48 |
                        ((long) value[2] & 0xff) << 40 |
                        ((long) value[3] & 0xff) << 32 |
                        ((long) value[4] & 0xff) << 24 |
                        ((long) value[5] & 0xff) << 16 |
                        ((long) value[6] & 0xff) <<  8 |
                        (long) value[7] & 0xff;
                return data;
            }

            @Override
            public String name() {
                return "Long";
            }
            @Override
            public int length() {
                return 8;
            }
            @Override
            public String toString() {
                return new StringJoiner(",","{","}")
                        .add("\"index\":"+ index)
                        .add("\"constant\":\""+name()+"\"")
                        .add("\"length\":"+length())
                        .add("\"value\":"+toJsonArray(value))
                        .toString();
            }
        }

        public class ConstantUtf8Info implements ConstantInfo {
            private String value;
            private String valueToString;
            private int length;
            private int index;
            public ConstantUtf8Info(int index,ClassReader reader) {
                this.index = index;
                length = reader.readUint16();
                byte[] bytes = reader.readInt8s(length);
                value = new String(bytes, StandardCharsets.UTF_8);
            }
            public String value() {
                return value;
            }
            private String valueToString() {
                if(valueToString == null) {
                    valueToString = value.replace("\"", "\\\"")
                            .replace(":", "\\:")
                            .replace("{", "\\{")
                            .replace("}", "\\}")
                            .replace("[", "\\[")
                            .replace("]", "\\]");
                }
                return valueToString;
            }
            @Override
            public String name() {
                return "UTF8";
            }
            @Override
            public int length() {
                return length;
            }

            @Override
            public String toString() {
                return new StringJoiner(",","{","}")
                        .add("\"index\":"+ index)
                        .add("\"constant\":\""+name()+"\"")
                        .add("\"length\":"+length())
                        .add("\"value\":\""+valueToString()+"\"")
                        .toString();
            }
        }

        public class ConstantClassInfo implements ConstantInfo {
            private int nameIndex;
            private int index;
            public ConstantClassInfo(int index,ClassReader reader) {
                this.index = index;
                this.nameIndex = reader.readUint16();;
            }
            public String value() {
                return getUtf8(nameIndex);
            }
            @Override
            public String name() {
                return "Class";
            }
            @Override
            public int length() {
                return 2;
            }
            @Override
            public String toString() {
                return new StringJoiner(",","{","}")
                        .add("\"index\":"+ index)
                        .add("\"constant\":\""+name()+"\"")
                        .add("\"length\":"+length())
                        .add("\"nameIndex\":"+nameIndex)
                        .add("\"name\":\""+ getUtf8ForToString(nameIndex)+"\"")
                        .toString();
            }
        }

        public class ConstantFieldRefInfo implements ConstantInfo {
            private ConstantMemberRefInfo memberRefInfo;
            private int index;
            public ConstantFieldRefInfo(int index,ConstantMemberRefInfo memberRefInfo) {
                this.index = index;
                this.memberRefInfo = memberRefInfo;
            }
            public ConstantMemberRefInfo value() {
                return memberRefInfo;
            }
            @Override
            public String name() {
                return "FieldRef";
            }
            @Override
            public int length() {
                return 0;
            }
            @Override
            public String toString() {
                return new StringJoiner(",","{","}")
                        .add("\"index\":"+ index)
                        .add("\"constant\":\""+name()+"\"")
                        .add("\"length\":"+length())
                        .add("\"memberRef\":"+ memberRefInfo)
                        .toString();
            }
        }

        public class ConstantMemberRefInfo implements ConstantInfo {
            private int classIndex;
            private int nameAndTypeIndex;
            public ConstantMemberRefInfo(ClassReader reader) {
                classIndex = reader.readUint16();
                nameAndTypeIndex = reader.readUint16();
            }
            public String className() {
                return getClassName(classIndex);
            }
            public ConstantNameAndTypeInfo nameAndType() {
                return getNameAndType(nameAndTypeIndex);
            }
            @Override
            public String name() {
                return "MemberRef";
            }
            @Override
            public int length() {
                return 4;
            }
            @Override
            public String toString() {
                return new StringJoiner(",","{","}")
                        .add("\"constant\":\""+name()+"\"")
                        .add("\"length\":"+length())
                        .add("\"classIndex\":"+classIndex)
                        .add("\"nameAndTypeIndex\":"+nameAndTypeIndex)
                        .add("\"class\":\""+ className()+"\"")
                        .add("\"nameAndType\":"+ nameAndType())
                        .toString();
            }
        }

        public class ConstantMethodRefInfo implements ConstantInfo {
            private ConstantMemberRefInfo memberRefInfo;
            private int index;
            public ConstantMethodRefInfo(int index,ConstantMemberRefInfo memberRefInfo) {
                this.index = index;
                this.memberRefInfo = memberRefInfo;
            }
            @Override
            public String name() {
                return "MethodRef";
            }
            @Override
            public int length() {
                return 0;
            }
            @Override
            public String toString() {
                return new StringJoiner(",","{","}")
                        .add("\"index\":"+ index)
                        .add("\"constant\":\""+name()+"\"")
                        .add("\"length\":"+length())
                        .add("\"memberRef\":"+ memberRefInfo)
                        .toString();
            }
        }

        public class ConstantInterfaceMethodRefInfo implements ConstantInfo {
            private ConstantMemberRefInfo memberRefInfo;
            private int index;
            public ConstantInterfaceMethodRefInfo(int index,ConstantMemberRefInfo memberRefInfo) {
                this.index = index;
                this.memberRefInfo = memberRefInfo;
            }
            @Override
            public String name() {
                return "InterfaceMethodRef";
            }
            @Override
            public int length() {
                return 0;
            }
            @Override
            public String toString() {
                return new StringJoiner(",","{","}")
                        .add("\"index\":"+ index)
                        .add("\"constant\":\""+name()+"\"")
                        .add("\"length\":"+length())
                        .add("\"memberRef\":"+ memberRefInfo)
                        .toString();
            }
        }

        public class ConstantNameAndTypeInfo implements ConstantInfo {
            private int nameIndex;
            private int descriptorIndex;
            private int index;
            public ConstantNameAndTypeInfo (int index,ClassReader reader) {
                nameIndex = reader.readUint16();
                this.index = index;
                descriptorIndex = reader.readUint16();
            }
            @Override
            public String name() {
                return "NameAndType";
            }
            @Override
            public int length() {
                return 4;
            }
            @Override
            public String toString() {
                return new StringJoiner(",","{","}")
                        .add("\"index\":"+ index)
                        .add("\"constant\":\""+name()+"\"")
                        .add("\"length\":"+length())
                        .add("\"name\":\""+ getUtf8ForToString(nameIndex)+"\"")
                        .add("\"descriptor\":\""+ getUtf8ForToString(descriptorIndex)+"\"")
                        .add("\"nameIndex\":"+nameIndex)
                        .add("\"descriptorIndex\":"+descriptorIndex)
                        .toString();
            }
        }

        public class ConstantMethodTypeInfo implements ConstantInfo {
            private int descriptorIndex;
            private int index;
            public ConstantMethodTypeInfo (int index,ClassReader reader) {
                this.index = index;
                descriptorIndex = reader.readUint16();
            }
            @Override
            public String name() {
                return "MethodType";
            }
            @Override
            public int length() {
                return 2;
            }
            @Override
            public String toString() {
                return new StringJoiner(",","{","}")
                        .add("\"index\":"+ index)
                        .add("\"constant\":\""+name()+"\"")
                        .add("\"length\":"+length())
                        .add("\"descriptorIndex\":"+descriptorIndex)
                        .add("\"descriptor\":\""+ getUtf8ForToString(descriptorIndex)+"\"")
                        .toString();
            }
        }

        public class ConstantMethodHandleInfo implements ConstantInfo {
            private int referenceKind;
            private int referenceIndex;
            private int index;
            public ConstantMethodHandleInfo (int index,ClassReader reader) {
                this.index = index;
                referenceKind = reader.readUint8();
                referenceIndex = reader.readUint16();
            }
            @Override
            public String name() {
                return "MethodHandle";
            }
            @Override
            public int length() {
                return 6;
            }
            @Override
            public String toString() {
                return new StringJoiner(",","{","}")
                        .add("\"index\":"+ index)
                        .add("\"constant\":\""+name()+"\"")
                        .add("\"length\":"+length())
                        .add("\"referenceKind\":"+referenceKind)
                        .add("\"referenceIndex\":"+referenceIndex)
                        .toString();
            }
        }

        public class ConstantInvokeDynamicInfo implements ConstantInfo {
            private int bootstrapMethodAttrIndex;
            private int nameAndTypeIndex;
            private int index;
            public ConstantInvokeDynamicInfo (int index,ClassReader reader) {
                this.index = index;
                bootstrapMethodAttrIndex = reader.readUint16();
                nameAndTypeIndex = reader.readUint16();
            }
            public ConstantNameAndTypeInfo nameAndType() {
                return getNameAndType(nameAndTypeIndex);
            }
            @Override
            public String name() {
                return "InvokeDynamic";
            }
            @Override
            public int length() {
                return 8;
            }
            @Override
            public String toString() {
                return new StringJoiner(",","{","}")
                        .add("\"index\":"+ index)
                        .add("\"constant\":\""+name()+"\"")
                        .add("\"length\":"+length())
                        .add("\"bootstrapMethodAttrIndex\":"+bootstrapMethodAttrIndex)
                        .add("\"nameAndTypeIndex\":"+nameAndTypeIndex)
                        .add("\"nameAndType\":"+nameAndType())
                        .toString();
            }
        }

        public class ConstantUnkownInfo implements ConstantInfo {
            private int tag;
            private int index;
            public ConstantUnkownInfo(int index,int tag) {
                this.index = index;
                this.tag = tag;
            }
            @Override
            public String name() {
                return "Unkown";
            }
            @Override
            public int length() {
                return 0;
            }
            @Override
            public String toString() {
                return new StringJoiner(",","{","}")
                        .add("\"index\":"+ index)
                        .add("\"constant\":\""+name()+"\"")
                        .add("\"length\":"+length())
                        .add("\"tag\":"+tag)
                        .toString();
            }
        }
    }

    public static class Member {
        private int accessFlags;
        private int nameIndex;
        private int descriptorIndex;
        private String name;
        private String descriptorName;
        private Attribute[] attributes;
        private Class<?>[] javaArgumentTypes;
        private Type[] argumentTypes;

        /**
         * 获取入参在局部变量表的位置
         * @return 局部变量表所在下标的数组
         */
        public int[] getArgumentLocalVariableTableIndex() {
            Type[] argumentTypes = getArgumentTypes();
            int[] lvtIndex = new int[argumentTypes.length];
            int nextIndex = Modifier.isStatic(accessFlags) ? 0 : 1;//静态没有this
            for (int i = 0; i < argumentTypes.length; i++) {
                lvtIndex[i] = nextIndex;
                if (argumentTypes[i] == Type.LONG_TYPE || argumentTypes[i] == Type.DOUBLE_TYPE) {
                    nextIndex += 2;
                }
                else {
                    nextIndex++;
                }
            }
            return lvtIndex;
        }

        private Type[] getArgumentTypes(){
            if(argumentTypes == null){
                argumentTypes = Type.getArgumentTypes(descriptorName());
            }
            return argumentTypes;
        }

        public Class<?>[] getJavaArgumentTypes(){
            if(javaArgumentTypes == null){
                Type[] argumentTypes = getArgumentTypes();
                Class<?>[] javaArgumentTypes = new Class<?>[argumentTypes.length];
                for (int i = 0; i < argumentTypes.length; i++) {
                    javaArgumentTypes[i] = ReflectUtil.resolveClassName(
                            argumentTypes[i].getClassName(),null);
                }
                this.javaArgumentTypes = javaArgumentTypes;
            }
            return javaArgumentTypes;
        }

        public java.lang.reflect.Member getJavaMember(Class target) throws NoSuchMethodException {
            String name = name();
            Class<?>[] argumentTypes = getJavaArgumentTypes();
            if ("<init>".equals(name)) {
                return target.getDeclaredConstructor(argumentTypes);
            }else {
                return target.getDeclaredMethod(name, argumentTypes);
            }
        }

        public String name() {
            return name;
        }

        public String descriptorName() {
            return descriptorName;
        }

        public Attribute.LocalVariable[] localVariableTable(){
            if(this.attributes == null){
                return null;
            }
            for(Attribute attributeInfo : this.attributes){
                if(attributeInfo.isAttrCode()){
                    Attribute[] codeAttributes = attributeInfo.attributes();
                    for(Attribute codeAttributeInfo : codeAttributes) {
                        if(codeAttributeInfo.isAttrLocalVariableTable() || codeAttributeInfo.isAttrLocalVariableTypeTable()){
                            return codeAttributeInfo.localVariableTable();
                        }
                    }
                    return EMPTY_LOCAL_VARIABLE_TABLE;
                }
            }
            return null;
        }

        @Override
        public String toString() {
            StringJoiner joiner = new StringJoiner(",","{","}");
            joiner.add("\"accessFlags\":\""+Modifier.toString(accessFlags)+"\"");
            joiner.add("\"name\":\""+ name()+"\"");
            joiner.add("\"descriptorName\":\""+ descriptorName()+"\"");
            joiner.add("\"attributes\":"+toJsonArray(attributes));
            return joiner.toString();
        }


        public static final class Type {
            /** The sort of the {@code void} type. See {@link #getSort}. */
            public static final int VOID = 0;

            /** The sort of the {@code boolean} type. See {@link #getSort}. */
            public static final int BOOLEAN = 1;

            /** The sort of the {@code char} type. See {@link #getSort}. */
            public static final int CHAR = 2;

            /** The sort of the {@code byte} type. See {@link #getSort}. */
            public static final int BYTE = 3;

            /** The sort of the {@code short} type. See {@link #getSort}. */
            public static final int SHORT = 4;

            /** The sort of the {@code int} type. See {@link #getSort}. */
            public static final int INT = 5;

            /** The sort of the {@code float} type. See {@link #getSort}. */
            public static final int FLOAT = 6;

            /** The sort of the {@code long} type. See {@link #getSort}. */
            public static final int LONG = 7;

            /** The sort of the {@code double} type. See {@link #getSort}. */
            public static final int DOUBLE = 8;

            /** The sort of array reference types. See {@link #getSort}. */
            public static final int ARRAY = 9;

            /** The sort of object reference types. See {@link #getSort}. */
            public static final int OBJECT = 10;

            /** The sort of method types. See {@link #getSort}. */
            public static final int METHOD = 11;

            /** The (private) sort of object reference types represented with an internal name. */
            private static final int INTERNAL = 12;

            /** The descriptors of the primitive types. */
            private static final String PRIMITIVE_DESCRIPTORS = "VZCBSIFJD";

            /** The {@code void} type. */
            public static final Type VOID_TYPE = new Type(VOID, PRIMITIVE_DESCRIPTORS, VOID, VOID + 1);

            /** The {@code boolean} type. */
            public static final Type BOOLEAN_TYPE =
                    new Type(BOOLEAN, PRIMITIVE_DESCRIPTORS, BOOLEAN, BOOLEAN + 1);

            /** The {@code char} type. */
            public static final Type CHAR_TYPE = new Type(CHAR, PRIMITIVE_DESCRIPTORS, CHAR, CHAR + 1);

            /** The {@code byte} type. */
            public static final Type BYTE_TYPE = new Type(BYTE, PRIMITIVE_DESCRIPTORS, BYTE, BYTE + 1);

            /** The {@code short} type. */
            public static final Type SHORT_TYPE = new Type(SHORT, PRIMITIVE_DESCRIPTORS, SHORT, SHORT + 1);

            /** The {@code int} type. */
            public static final Type INT_TYPE = new Type(INT, PRIMITIVE_DESCRIPTORS, INT, INT + 1);

            /** The {@code float} type. */
            public static final Type FLOAT_TYPE = new Type(FLOAT, PRIMITIVE_DESCRIPTORS, FLOAT, FLOAT + 1);

            /** The {@code long} type. */
            public static final Type LONG_TYPE = new Type(LONG, PRIMITIVE_DESCRIPTORS, LONG, LONG + 1);

            /** The {@code double} type. */
            public static final Type DOUBLE_TYPE =
                    new Type(DOUBLE, PRIMITIVE_DESCRIPTORS, DOUBLE, DOUBLE + 1);

            // -----------------------------------------------------------------------------------------------
            // Fields
            // -----------------------------------------------------------------------------------------------

            /**
             * The sort of this type. Either {@link #VOID}, {@link #BOOLEAN}, {@link #CHAR}, {@link #BYTE},
             * {@link #SHORT}, {@link #INT}, {@link #FLOAT}, {@link #LONG}, {@link #DOUBLE}, {@link #ARRAY},
             * {@link #OBJECT}, {@link #METHOD} or {@link #INTERNAL}.
             */
            private final int sort;

            /**
             * A buffer containing the value of this field or method type. This value is an internal name for
             * {@link #OBJECT} and {@link #INTERNAL} types, and a field or method descriptor in the other
             * cases.
             *
             * <p>For {@link #OBJECT} types, this field also contains the descriptor: the characters in
             * [{@link #valueBegin},{@link #valueEnd}) contain the internal name, and those in [{@link
             * #valueBegin} - 1, {@link #valueEnd} + 1) contain the descriptor.
             */
            private final String valueBuffer;

            /**
             * The beginning index, inclusive, of the value of this Java field or method type in {@link
             * #valueBuffer}. This value is an internal name for {@link #OBJECT} and {@link #INTERNAL} types,
             * and a field or method descriptor in the other cases.
             */
            private final int valueBegin;

            /**
             * The end index, exclusive, of the value of this Java field or method type in {@link
             * #valueBuffer}. This value is an internal name for {@link #OBJECT} and {@link #INTERNAL} types,
             * and a field or method descriptor in the other cases.
             */
            private final int valueEnd;

            /**
             * Constructs a reference type.
             *
             * @param sort the sort of this type, see {@link #sort}.
             * @param valueBuffer a buffer containing the value of this field or method type.
             * @param valueBegin the beginning index, inclusive, of the value of this field or method type in
             *     valueBuffer.
             * @param valueEnd the end index, exclusive, of the value of this field or method type in
             *     valueBuffer.
             */
            private Type(final int sort, final String valueBuffer, final int valueBegin, final int valueEnd) {
                this.sort = sort;
                this.valueBuffer = valueBuffer;
                this.valueBegin = valueBegin;
                this.valueEnd = valueEnd;
            }

            // -----------------------------------------------------------------------------------------------
            // Methods to get Type(s) from a descriptor, a reflected Method or Constructor, other types, etc.
            // -----------------------------------------------------------------------------------------------

            /**
             * Returns the {@link Type} corresponding to the given type descriptor.
             *
             * @param typeDescriptor a field or method type descriptor.
             * @return the {@link Type} corresponding to the given type descriptor.
             */
            public static Type getType(final String typeDescriptor) {
                return getTypeInternal(typeDescriptor, 0, typeDescriptor.length());
            }

            /**
             * Returns the {@link Type} corresponding to the given class.
             *
             * @param clazz a class.
             * @return the {@link Type} corresponding to the given class.
             */
            public static Type getType(final Class<?> clazz) {
                if (clazz.isPrimitive()) {
                    if (clazz == Integer.TYPE) {
                        return INT_TYPE;
                    } else if (clazz == Void.TYPE) {
                        return VOID_TYPE;
                    } else if (clazz == Boolean.TYPE) {
                        return BOOLEAN_TYPE;
                    } else if (clazz == Byte.TYPE) {
                        return BYTE_TYPE;
                    } else if (clazz == Character.TYPE) {
                        return CHAR_TYPE;
                    } else if (clazz == Short.TYPE) {
                        return SHORT_TYPE;
                    } else if (clazz == Double.TYPE) {
                        return DOUBLE_TYPE;
                    } else if (clazz == Float.TYPE) {
                        return FLOAT_TYPE;
                    } else if (clazz == Long.TYPE) {
                        return LONG_TYPE;
                    } else {
                        throw new AssertionError();
                    }
                } else {
                    return getType(getDescriptor(clazz));
                }
            }

            /**
             * Returns the method {@link Type} corresponding to the given constructor.
             *
             * @param constructor a {@link Constructor} object.
             * @return the method {@link Type} corresponding to the given constructor.
             */
            public static Type getType(final Constructor<?> constructor) {
                return getType(getConstructorDescriptor(constructor));
            }

            /**
             * Returns the method {@link Type} corresponding to the given method.
             *
             * @param method a {@link Method} object.
             * @return the method {@link Type} corresponding to the given method.
             */
            public static Type getType(final Method method) {
                return getType(getMethodDescriptor(method));
            }

            /**
             * Returns the type of the elements of this array type. This method should only be used for an
             * array type.
             *
             * @return Returns the type of the elements of this array type.
             */
            public Type getElementType() {
                final int numDimensions = getDimensions();
                return getTypeInternal(valueBuffer, valueBegin + numDimensions, valueEnd);
            }

            /**
             * Returns the {@link Type} corresponding to the given internal name.
             *
             * @param internalName an internal name.
             * @return the {@link Type} corresponding to the given internal name.
             */
            public static Type getObjectType(final String internalName) {
                return new Type(
                        internalName.charAt(0) == '[' ? ARRAY : INTERNAL, internalName, 0, internalName.length());
            }

            /**
             * Returns the {@link Type} corresponding to the given method descriptor. Equivalent to <code>
             * Type.getType(methodDescriptor)</code>.
             *
             * @param methodDescriptor a method descriptor.
             * @return the {@link Type} corresponding to the given method descriptor.
             */
            public static Type getMethodType(final String methodDescriptor) {
                return new Type(METHOD, methodDescriptor, 0, methodDescriptor.length());
            }

            /**
             * Returns the method {@link Type} corresponding to the given argument and return types.
             *
             * @param returnType the return type of the method.
             * @param argumentTypes the argument types of the method.
             * @return the method {@link Type} corresponding to the given argument and return types.
             */
            public static Type getMethodType(final Type returnType, final Type... argumentTypes) {
                return getType(getMethodDescriptor(returnType, argumentTypes));
            }

            /**
             * Returns the argument types of methods of this type. This method should only be used for method
             * types.
             *
             * @return the argument types of methods of this type.
             */
            public Type[] getArgumentTypes() {
                return getArgumentTypes(getDescriptor());
            }

            /**
             * Returns the {@link Type} values corresponding to the argument types of the given method
             * descriptor.
             *
             * @param methodDescriptor a method descriptor.
             * @return the {@link Type} values corresponding to the argument types of the given method
             *     descriptor.
             */
            public static Type[] getArgumentTypes(final String methodDescriptor) {
                // First step: compute the number of argument types in methodDescriptor.
                int numArgumentTypes = 0;
                // Skip the first character, which is always a '('.
                int currentOffset = 1;
                // Parse the argument types, one at a each loop iteration.
                while (methodDescriptor.charAt(currentOffset) != ')') {
                    while (methodDescriptor.charAt(currentOffset) == '[') {
                        currentOffset++;
                    }
                    if (methodDescriptor.charAt(currentOffset++) == 'L') {
                        // Skip the argument descriptor content.
                        currentOffset = methodDescriptor.indexOf(';', currentOffset) + 1;
                    }
                    ++numArgumentTypes;
                }

                // Second step: create a Type instance for each argument type.
                Type[] argumentTypes = new Type[numArgumentTypes];
                // Skip the first character, which is always a '('.
                currentOffset = 1;
                // Parse and create the argument types, one at each loop iteration.
                int currentArgumentTypeIndex = 0;
                while (methodDescriptor.charAt(currentOffset) != ')') {
                    final int currentArgumentTypeOffset = currentOffset;
                    while (methodDescriptor.charAt(currentOffset) == '[') {
                        currentOffset++;
                    }
                    if (methodDescriptor.charAt(currentOffset++) == 'L') {
                        // Skip the argument descriptor content.
                        currentOffset = methodDescriptor.indexOf(';', currentOffset) + 1;
                    }
                    argumentTypes[currentArgumentTypeIndex++] =
                            getTypeInternal(methodDescriptor, currentArgumentTypeOffset, currentOffset);
                }
                return argumentTypes;
            }

            /**
             * Returns the {@link Type} values corresponding to the argument types of the given method.
             *
             * @param method a method.
             * @return the {@link Type} values corresponding to the argument types of the given method.
             */
            public static Type[] getArgumentTypes(final Method method) {
                Class<?>[] classes = method.getParameterTypes();
                Type[] types = new Type[classes.length];
                for (int i = classes.length - 1; i >= 0; --i) {
                    types[i] = getType(classes[i]);
                }
                return types;
            }

            /**
             * Returns the return type of methods of this type. This method should only be used for method
             * types.
             *
             * @return the return type of methods of this type.
             */
            public Type getReturnType() {
                return getReturnType(getDescriptor());
            }

            /**
             * Returns the {@link Type} corresponding to the return type of the given method descriptor.
             *
             * @param methodDescriptor a method descriptor.
             * @return the {@link Type} corresponding to the return type of the given method descriptor.
             */
            public static Type getReturnType(final String methodDescriptor) {
                // Skip the first character, which is always a '('.
                int currentOffset = 1;
                // Skip the argument types, one at a each loop iteration.
                while (methodDescriptor.charAt(currentOffset) != ')') {
                    while (methodDescriptor.charAt(currentOffset) == '[') {
                        currentOffset++;
                    }
                    if (methodDescriptor.charAt(currentOffset++) == 'L') {
                        // Skip the argument descriptor content.
                        currentOffset = methodDescriptor.indexOf(';', currentOffset) + 1;
                    }
                }
                return getTypeInternal(methodDescriptor, currentOffset + 1, methodDescriptor.length());
            }

            /**
             * Returns the {@link Type} corresponding to the return type of the given method.
             *
             * @param method a method.
             * @return the {@link Type} corresponding to the return type of the given method.
             */
            public static Type getReturnType(final Method method) {
                return getType(method.getReturnType());
            }

            /**
             * Returns the {@link Type} corresponding to the given field or method descriptor.
             *
             * @param descriptorBuffer a buffer containing the field or method descriptor.
             * @param descriptorBegin the beginning index, inclusive, of the field or method descriptor in
             *     descriptorBuffer.
             * @param descriptorEnd the end index, exclusive, of the field or method descriptor in
             *     descriptorBuffer.
             * @return the {@link Type} corresponding to the given type descriptor.
             */
            private static Type getTypeInternal(
                    final String descriptorBuffer, final int descriptorBegin, final int descriptorEnd) {
                switch (descriptorBuffer.charAt(descriptorBegin)) {
                    case 'V':
                        return VOID_TYPE;
                    case 'Z':
                        return BOOLEAN_TYPE;
                    case 'C':
                        return CHAR_TYPE;
                    case 'B':
                        return BYTE_TYPE;
                    case 'S':
                        return SHORT_TYPE;
                    case 'I':
                        return INT_TYPE;
                    case 'F':
                        return FLOAT_TYPE;
                    case 'J':
                        return LONG_TYPE;
                    case 'D':
                        return DOUBLE_TYPE;
                    case '[':
                        return new Type(ARRAY, descriptorBuffer, descriptorBegin, descriptorEnd);
                    case 'L':
                        return new Type(OBJECT, descriptorBuffer, descriptorBegin + 1, descriptorEnd - 1);
                    case '(':
                        return new Type(METHOD, descriptorBuffer, descriptorBegin, descriptorEnd);
                    default:
                        throw new IllegalArgumentException();
                }
            }

            // -----------------------------------------------------------------------------------------------
            // Methods to get class names, internal names or descriptors.
            // -----------------------------------------------------------------------------------------------

            /**
             * Returns the binary name of the class corresponding to this type. This method must not be used
             * on method types.
             *
             * @return the binary name of the class corresponding to this type.
             */
            public String getClassName() {
                switch (sort) {
                    case VOID:
                        return "void";
                    case BOOLEAN:
                        return "boolean";
                    case CHAR:
                        return "char";
                    case BYTE:
                        return "byte";
                    case SHORT:
                        return "short";
                    case INT:
                        return "int";
                    case FLOAT:
                        return "float";
                    case LONG:
                        return "long";
                    case DOUBLE:
                        return "double";
                    case ARRAY:
                        StringBuilder stringBuilder = new StringBuilder(getElementType().getClassName());
                        for (int i = getDimensions(); i > 0; --i) {
                            stringBuilder.append("[]");
                        }
                        return stringBuilder.toString();
                    case OBJECT:
                    case INTERNAL:
                        return valueBuffer.substring(valueBegin, valueEnd).replace('/', '.');
                    default:
                        throw new AssertionError();
                }
            }

            /**
             * Returns the internal name of the class corresponding to this object or array type. The internal
             * name of a class is its fully qualified name (as returned by Class.getName(), where '.' are
             * replaced by '/'). This method should only be used for an object or array type.
             *
             * @return the internal name of the class corresponding to this object type.
             */
            public String getInternalName() {
                return valueBuffer.substring(valueBegin, valueEnd);
            }

            /**
             * Returns the internal name of the given class. The internal name of a class is its fully
             * qualified name, as returned by Class.getName(), where '.' are replaced by '/'.
             *
             * @param clazz an object or array class.
             * @return the internal name of the given class.
             */
            public static String getInternalName(final Class<?> clazz) {
                return clazz.getName().replace('.', '/');
            }

            /**
             * Returns the descriptor corresponding to this type.
             *
             * @return the descriptor corresponding to this type.
             */
            public String getDescriptor() {
                if (sort == OBJECT) {
                    return valueBuffer.substring(valueBegin - 1, valueEnd + 1);
                } else if (sort == INTERNAL) {
                    return new StringBuilder()
                            .append('L')
                            .append(valueBuffer, valueBegin, valueEnd)
                            .append(';')
                            .toString();
                } else {
                    return valueBuffer.substring(valueBegin, valueEnd);
                }
            }

            /**
             * Returns the descriptor corresponding to the given class.
             *
             * @param clazz an object class, a primitive class or an array class.
             * @return the descriptor corresponding to the given class.
             */
            public static String getDescriptor(final Class<?> clazz) {
                StringBuilder stringBuilder = new StringBuilder();
                appendDescriptor(clazz, stringBuilder);
                return stringBuilder.toString();
            }

            /**
             * Returns the descriptor corresponding to the given constructor.
             *
             * @param constructor a {@link Constructor} object.
             * @return the descriptor of the given constructor.
             */
            public static String getConstructorDescriptor(final Constructor<?> constructor) {
                StringBuilder stringBuilder = new StringBuilder();
                stringBuilder.append('(');
                Class<?>[] parameters = constructor.getParameterTypes();
                for (Class<?> parameter : parameters) {
                    appendDescriptor(parameter, stringBuilder);
                }
                return stringBuilder.append(")V").toString();
            }

            /**
             * Returns the descriptor corresponding to the given argument and return types.
             *
             * @param returnType the return type of the method.
             * @param argumentTypes the argument types of the method.
             * @return the descriptor corresponding to the given argument and return types.
             */
            public static String getMethodDescriptor(final Type returnType, final Type... argumentTypes) {
                StringBuilder stringBuilder = new StringBuilder();
                stringBuilder.append('(');
                for (Type argumentType : argumentTypes) {
                    argumentType.appendDescriptor(stringBuilder);
                }
                stringBuilder.append(')');
                returnType.appendDescriptor(stringBuilder);
                return stringBuilder.toString();
            }

            /**
             * Returns the descriptor corresponding to the given method.
             *
             * @param method a {@link Method} object.
             * @return the descriptor of the given method.
             */
            public static String getMethodDescriptor(final Method method) {
                return getMethodDescriptor(method.getParameterTypes(),method.getReturnType());
            }

            public static String getMethodDescriptor(Class<?>[] parameterTypes,Class<?> returnType) {
                StringBuilder stringBuilder = new StringBuilder();
                stringBuilder.append('(');
                for (Class<?> parameter : parameterTypes) {
                    appendDescriptor(parameter, stringBuilder);
                }
                stringBuilder.append(')');
                appendDescriptor(returnType, stringBuilder);
                return stringBuilder.toString();
            }

            /**
             * Appends the descriptor corresponding to this type to the given string buffer.
             *
             * @param stringBuilder the string builder to which the descriptor must be appended.
             */
            private void appendDescriptor(final StringBuilder stringBuilder) {
                if (sort == OBJECT) {
                    stringBuilder.append(valueBuffer, valueBegin - 1, valueEnd + 1);
                } else if (sort == INTERNAL) {
                    stringBuilder.append('L').append(valueBuffer, valueBegin, valueEnd).append(';');
                } else {
                    stringBuilder.append(valueBuffer, valueBegin, valueEnd);
                }
            }

            /**
             * Appends the descriptor of the given class to the given string builder.
             *
             * @param clazz the class whose descriptor must be computed.
             * @param stringBuilder the string builder to which the descriptor must be appended.
             */
            private static void appendDescriptor(final Class<?> clazz, final StringBuilder stringBuilder) {
                Class<?> currentClass = clazz;
                while (currentClass.isArray()) {
                    stringBuilder.append('[');
                    currentClass = currentClass.getComponentType();
                }
                if (currentClass.isPrimitive()) {
                    char descriptor;
                    if (currentClass == Integer.TYPE) {
                        descriptor = 'I';
                    } else if (currentClass == Void.TYPE) {
                        descriptor = 'V';
                    } else if (currentClass == Boolean.TYPE) {
                        descriptor = 'Z';
                    } else if (currentClass == Byte.TYPE) {
                        descriptor = 'B';
                    } else if (currentClass == Character.TYPE) {
                        descriptor = 'C';
                    } else if (currentClass == Short.TYPE) {
                        descriptor = 'S';
                    } else if (currentClass == Double.TYPE) {
                        descriptor = 'D';
                    } else if (currentClass == Float.TYPE) {
                        descriptor = 'F';
                    } else if (currentClass == Long.TYPE) {
                        descriptor = 'J';
                    } else {
                        throw new AssertionError();
                    }
                    stringBuilder.append(descriptor);
                } else {
                    stringBuilder.append('L');
                    String name = currentClass.getName();
                    int nameLength = name.length();
                    for (int i = 0; i < nameLength; ++i) {
                        char car = name.charAt(i);
                        stringBuilder.append(car == '.' ? '/' : car);
                    }
                    stringBuilder.append(';');
                }
            }

            // -----------------------------------------------------------------------------------------------
            // Methods to get the sort, dimension, size, and opcodes corresponding to a Type or descriptor.
            // -----------------------------------------------------------------------------------------------

            /**
             * Returns the sort of this type.
             *
             * @return {@link #VOID}, {@link #BOOLEAN}, {@link #CHAR}, {@link #BYTE}, {@link #SHORT}, {@link
             *     #INT}, {@link #FLOAT}, {@link #LONG}, {@link #DOUBLE}, {@link #ARRAY}, {@link #OBJECT} or
             *     {@link #METHOD}.
             */
            public int getSort() {
                return sort == INTERNAL ? OBJECT : sort;
            }

            /**
             * Returns the number of dimensions of this array type. This method should only be used for an
             * array type.
             *
             * @return the number of dimensions of this array type.
             */
            public int getDimensions() {
                int numDimensions = 1;
                while (valueBuffer.charAt(valueBegin + numDimensions) == '[') {
                    numDimensions++;
                }
                return numDimensions;
            }

            /**
             * Returns the size of values of this type. This method must not be used for method types.
             *
             * @return the size of values of this type, i.e., 2 for {@code long} and {@code double}, 0 for
             *     {@code void} and 1 otherwise.
             */
            public int getSize() {
                switch (sort) {
                    case VOID:
                        return 0;
                    case BOOLEAN:
                    case CHAR:
                    case BYTE:
                    case SHORT:
                    case INT:
                    case FLOAT:
                    case ARRAY:
                    case OBJECT:
                    case INTERNAL:
                        return 1;
                    case LONG:
                    case DOUBLE:
                        return 2;
                    default:
                        throw new AssertionError();
                }
            }

            /**
             * Returns the size of the arguments and of the return value of methods of this type. This method
             * should only be used for method types.
             *
             * @return the size of the arguments of the method (plus one for the implicit this argument),
             *     argumentsSize, and the size of its return value, returnSize, packed into a single int i =
             *     {@code (argumentsSize &lt;&lt; 2) | returnSize} (argumentsSize is therefore equal to {@code
             *     i &gt;&gt; 2}, and returnSize to {@code i &amp; 0x03}).
             */
            public int getArgumentsAndReturnSizes() {
                return getArgumentsAndReturnSizes(getDescriptor());
            }

            /**
             * Computes the size of the arguments and of the return value of a method.
             *
             * @param methodDescriptor a method descriptor.
             * @return the size of the arguments of the method (plus one for the implicit this argument),
             *     argumentsSize, and the size of its return value, returnSize, packed into a single int i =
             *     {@code (argumentsSize &lt;&lt; 2) | returnSize} (argumentsSize is therefore equal to {@code
             *     i &gt;&gt; 2}, and returnSize to {@code i &amp; 0x03}).
             */
            public static int getArgumentsAndReturnSizes(final String methodDescriptor) {
                int argumentsSize = 1;
                // Skip the first character, which is always a '('.
                int currentOffset = 1;
                int currentChar = methodDescriptor.charAt(currentOffset);
                // Parse the argument types and compute their size, one at a each loop iteration.
                while (currentChar != ')') {
                    if (currentChar == 'J' || currentChar == 'D') {
                        currentOffset++;
                        argumentsSize += 2;
                    } else {
                        while (methodDescriptor.charAt(currentOffset) == '[') {
                            currentOffset++;
                        }
                        if (methodDescriptor.charAt(currentOffset++) == 'L') {
                            // Skip the argument descriptor content.
                            currentOffset = methodDescriptor.indexOf(';', currentOffset) + 1;
                        }
                        argumentsSize += 1;
                    }
                    currentChar = methodDescriptor.charAt(currentOffset);
                }
                currentChar = methodDescriptor.charAt(currentOffset + 1);
                if (currentChar == 'V') {
                    return argumentsSize << 2;
                } else {
                    int returnSize = (currentChar == 'J' || currentChar == 'D') ? 2 : 1;
                    return argumentsSize << 2 | returnSize;
                }
            }

            /**
             * Returns a JVM instruction opcode adapted to this {@link Type}. This method must not be used for
             * method types.
             *
             * @param opcode a JVM instruction opcode. This opcode must be one of ILOAD, ISTORE, IALOAD,
             *     IASTORE, IADD, ISUB, IMUL, IDIV, IREM, INEG, ISHL, ISHR, IUSHR, IAND, IOR, IXOR and
             *     IRETURN.
             * @return an opcode that is similar to the given opcode, but adapted to this {@link Type}. For
             *     example, if this type is {@code float} and {@code opcode} is IRETURN, this method returns
             *     FRETURN.
             */
            public int getOpcode(final int opcode) {
                if (opcode == Opcodes.IALOAD || opcode == Opcodes.IASTORE) {
                    switch (sort) {
                        case BOOLEAN:
                        case BYTE:
                            return opcode + (Opcodes.BALOAD - Opcodes.IALOAD);
                        case CHAR:
                            return opcode + (Opcodes.CALOAD - Opcodes.IALOAD);
                        case SHORT:
                            return opcode + (Opcodes.SALOAD - Opcodes.IALOAD);
                        case INT:
                            return opcode;
                        case FLOAT:
                            return opcode + (Opcodes.FALOAD - Opcodes.IALOAD);
                        case LONG:
                            return opcode + (Opcodes.LALOAD - Opcodes.IALOAD);
                        case DOUBLE:
                            return opcode + (Opcodes.DALOAD - Opcodes.IALOAD);
                        case ARRAY:
                        case OBJECT:
                        case INTERNAL:
                            return opcode + (Opcodes.AALOAD - Opcodes.IALOAD);
                        case METHOD:
                        case VOID:
                            throw new UnsupportedOperationException();
                        default:
                            throw new AssertionError();
                    }
                } else {
                    switch (sort) {
                        case VOID:
                            if (opcode != Opcodes.IRETURN) {
                                throw new UnsupportedOperationException();
                            }
                            return Opcodes.RETURN;
                        case BOOLEAN:
                        case BYTE:
                        case CHAR:
                        case SHORT:
                        case INT:
                            return opcode;
                        case FLOAT:
                            return opcode + (Opcodes.FRETURN - Opcodes.IRETURN);
                        case LONG:
                            return opcode + (Opcodes.LRETURN - Opcodes.IRETURN);
                        case DOUBLE:
                            return opcode + (Opcodes.DRETURN - Opcodes.IRETURN);
                        case ARRAY:
                        case OBJECT:
                        case INTERNAL:
                            if (opcode != Opcodes.ILOAD && opcode != Opcodes.ISTORE && opcode != Opcodes.IRETURN) {
                                throw new UnsupportedOperationException();
                            }
                            return opcode + (Opcodes.ARETURN - Opcodes.IRETURN);
                        case METHOD:
                            throw new UnsupportedOperationException();
                        default:
                            throw new AssertionError();
                    }
                }
            }

            // -----------------------------------------------------------------------------------------------
            // Equals, hashCode and toString.
            // -----------------------------------------------------------------------------------------------

            /**
             * Tests if the given object is equal to this type.
             *
             * @param object the object to be compared to this type.
             * @return {@literal true} if the given object is equal to this type.
             */
            @Override
            public boolean equals(final Object object) {
                if (this == object) {
                    return true;
                }
                if (!(object instanceof Type)) {
                    return false;
                }
                Type other = (Type) object;
                if ((sort == INTERNAL ? OBJECT : sort) != (other.sort == INTERNAL ? OBJECT : other.sort)) {
                    return false;
                }
                int begin = valueBegin;
                int end = valueEnd;
                int otherBegin = other.valueBegin;
                int otherEnd = other.valueEnd;
                // Compare the values.
                if (end - begin != otherEnd - otherBegin) {
                    return false;
                }
                for (int i = begin, j = otherBegin; i < end; i++, j++) {
                    if (valueBuffer.charAt(i) != other.valueBuffer.charAt(j)) {
                        return false;
                    }
                }
                return true;
            }

            /**
             * Returns a hash code value for this type.
             *
             * @return a hash code value for this type.
             */
            @Override
            public int hashCode() {
                int hashCode = 13 * (sort == INTERNAL ? OBJECT : sort);
                if (sort >= ARRAY) {
                    for (int i = valueBegin, end = valueEnd; i < end; i++) {
                        hashCode = 17 * (hashCode + valueBuffer.charAt(i));
                    }
                }
                return hashCode;
            }

            /**
             * Returns a string representation of this type.
             *
             * @return the descriptor of this type.
             */
            @Override
            public String toString() {
                return getDescriptor();
            }
        }
    }

    public class Attribute extends LinkedHashMap<String,Object>{
        public Attribute(int attrNameIndex, int length, ClassReader reader) {
            String attrName = constantPool.getUtf8(attrNameIndex);
            put("attrNameIndex",attrNameIndex);
            put("attrName",attrName);
            put("length",length);

            try {
                reader.mark();
                decode(attrName, length, reader);
            }catch (Exception e){
                put("decodeAttributeException",e.getMessage());
                reader.reset();
                byte[] decodeAttributeExceptionBytes = reader.readInt8s(length);
                put("decodeAttributeExceptionBytes",decodeAttributeExceptionBytes);
            }
        }

        private void decode(String attrName, int length, ClassReader reader){
            switch (attrName){
                case "ConstantValue" :{
                    int constantValueIndex = reader.readUint16();
                    put("constantValueIndex",constantValueIndex);
                    break;
                }
                case "SourceFile" :{
                    int sourceFileIndex = reader.readUint16();
                    put("sourceFileIndex",sourceFileIndex);
                    break;
                }
                case "Code" :{
                    put("maxStack",reader.readUint16());
                    put("maxLocals",reader.readUint16());
                    int codeLength = reader.readInt32();
                    short[] opcodes = reader.readUInt8s(codeLength);
                    put("opcodes",new Opcodes(opcodes));

                    int codeExceptionsLength = reader.readUint16();
                    CodeException[] codeExceptions;
                    if(codeExceptionsLength == 0){
                        codeExceptions = EMPTY_CODE_EXCEPTIONS;
                    }else {
                        codeExceptions = new CodeException[codeExceptionsLength];
                        for(int i=0; i<codeExceptions.length; i++){
                            codeExceptions[i] = new CodeException(reader.readUint16(),reader.readUint16(),reader.readUint16(),reader.readUint16());
                        }
                    }
                    put("exceptionTable",codeExceptions);
                    put("attributes", readAttributes(reader));
                    break;
                }
                case "Exceptions" :{
                    int exceptionIndexTableLength = reader.readUint16();
                    int[] exceptionIndexTable;
                    if(exceptionIndexTableLength == 0){
                        exceptionIndexTable = EMPTY_EXCEPTION_INDEX_TABLE;
                    }else {
                        exceptionIndexTable = new int[exceptionIndexTableLength];
                        for(int i=0; i < exceptionIndexTable.length; i++) {
                            exceptionIndexTable[i] = reader.readUint16();
                        }
                    }
                    put("exceptionIndexTable",exceptionIndexTable);
                    break;
                }
                case "LineNumberTable" :{
                    int lineNumberTableLength = reader.readUint16();
                    LineNumber[] lineNumberTable;
                    if(lineNumberTableLength == 0){
                        lineNumberTable = EMPTY_LINE_NUMBER_TABLE;
                    }else {
                        lineNumberTable = new LineNumber[lineNumberTableLength];
                        for(int i=0; i < lineNumberTable.length; i++) {
                            lineNumberTable[i] = new LineNumber(reader.readUint16(),reader.readUint16());
                        }
                    }
                    put("lineNumberTable",lineNumberTable);
                    break;
                }
                case "LocalVariableTable":
                case "LocalVariableTypeTable" :{
                    int localVariableTableLength = reader.readUint16();
                    LocalVariable[] localVariableTable;
                    if(localVariableTableLength == 0){
                        localVariableTable = EMPTY_LOCAL_VARIABLE_TABLE;
                    }else {
                        localVariableTable = new LocalVariable[localVariableTableLength];
                    }
                    for(int i=0; i < localVariableTable.length; i++) {
                        localVariableTable[i] = new LocalVariable(
                                reader.readUint16(),reader.readUint16(),
                                reader.readUint16(),reader.readUint16(),reader.readUint16());
                    }
                    put("localVariableTable",localVariableTable);
                    break;
                }
                case "InnerClasses" :{
                    int numberOfClassesLength = reader.readUint16();
                    InnerClass[] numberOfClasses;
                    if(numberOfClassesLength == 0){
                        numberOfClasses = EMPTY_INNER_CLASSES;
                    }else {
                        numberOfClasses = new InnerClass[numberOfClassesLength];
                    }
                    for(int i=0; i < numberOfClasses.length; i++) {
                        numberOfClasses[i] = new InnerClass(
                                reader.readUint16(),reader.readUint16(),
                                reader.readUint16(),reader.readUint16());
                    }
                    put("numberOfClasses",numberOfClasses);
                    break;
                }
                case "Synthetic" :{
                    if(length>0) {
                        byte[] syntheticBytes = reader.readInt8s(length);
                        put("bytes",syntheticBytes);
                        System.err.println("Synthetic attribute with length > 0");
                    }
                    break;
                }
                case "Deprecated" :{
                    if(length>0) {
                        byte[] deprecatedBytes = reader.readInt8s(length);
                        put("bytes",deprecatedBytes);
                        System.err.println("Deprecated attribute with length > 0");
                    }
                    break;
                }
                case "PMGClass" :{
                    put("pmgClassIndex",reader.readUint16());
                    put("pmgIndex",reader.readUint16());
                    break;
                }
                case "Signature" :{
                    put("signatureIndex",reader.readUint16());
                    break;
                }
                case "StackMap" :{
                    int stackMapsLength = reader.readUint16();
                    StackMapEntry[] stackMaps;
                    if(stackMapsLength == 0){
                        stackMaps = EMPTY_STACK_MAP_ENTRY;
                    }else {
                        stackMaps = new StackMapEntry[stackMapsLength];
                        for(int i=0; i<stackMaps.length; i++){
                            int byteCodeOffset = reader.readInt16();
                            int typesOfLocalsSize = reader.readUint16();
                            StackMapEntry stackMapEntry = new StackMapEntry(byteCodeOffset,typesOfLocalsSize);
                            for (int j=0; j<stackMapEntry.typesOfLocals.length; j++) {
                                stackMapEntry.typesOfLocals[j] = new StackMapType(reader);
                            }
                            stackMaps[i] = stackMapEntry;
                        }
                    }
                    put("map",stackMaps);
                    break;
                }
                case "StackMapTable" :{
                    int numberOfEntries = reader.readUint16();
                    StackMapFrame[] entries;
                    if(numberOfEntries == 0){
                        entries = EMPTY_STACK_MAP_FRAME;
                    }else {
                        entries = new StackMapFrame[numberOfEntries];
                        for(int i=0; i<numberOfEntries; i++){
                            entries[i] = new StackMapFrame(reader);
                        }
                    }
                    put("entries",entries);
                    break;
                }
                case "RuntimeVisibleAnnotations" :{
                    int numberOfAnnotations = reader.readUint16();
                    Annotation[] annotations = new Annotation[numberOfAnnotations];
                    for(int i=0; i< numberOfAnnotations; i++){
                        annotations[i] = new Annotation(reader);
                    }
                    put("annotations",annotations);
                    break;
                }
                default:{
                    byte[] unkownBytes = reader.readInt8s(length);
                    put("unkownBytes",unkownBytes);
                    break;
                }
            }
        }
        public int length() {
            return (int) get("length");
        }

        public String attrName() {
            return (String) get("attrName");
        }

        public boolean isAttrLocalVariableTable(){
            return "LocalVariableTable".equals(attrName());
        }
        public boolean isAttrLocalVariableTypeTable(){
            return "LocalVariableTypeTable".equals(attrName());
        }

        public boolean isAttrCode(){
            return "Code".equals(attrName());
        }

        public LocalVariable[] localVariableTable() {
            Object localVariableTable = get("localVariableTable");
            if(localVariableTable instanceof LocalVariable[]){
                return (LocalVariable[]) localVariableTable;
            }
            return null;
        }

        public Attribute[] attributes() {
            Object attributes = get("attributes");
            if(attributes instanceof Attribute[]){
                return (Attribute[]) attributes;
            }
            return null;
        }

        @Override
        public String toString() {
            StringJoiner joiner = new StringJoiner(",","{","}");
            Iterator<Map.Entry<String,Object>> i = entrySet().iterator();
            while (i.hasNext()) {
                Map.Entry<String,Object> e = i.next();
                String key = e.getKey();
                Object value = e.getValue();
                if(value instanceof Number){
                    joiner.add("\""+key+"\":"+value);
                }else if(value == null){
                    joiner.add("\""+key+"\":null");
                }else if(value.getClass().isArray()){
                    joiner.add("\""+key+"\":"+toJsonArray(value));
                }else if(value instanceof CharSequence){
                    joiner.add("\""+key+"\":\""+value+"\"");
                }else {
                    joiner.add("\""+key+"\":"+value);
                }
            }
            return joiner.toString();
        }

        public class CodeException{
            private int startPc;
            private int endPc;
            private int handlerPc;
            private int catchType;
            public CodeException(int startPc, int endPc, int handlerPc, int catchType) {
                this.startPc = startPc;
                this.endPc = endPc;
                this.handlerPc = handlerPc;
                this.catchType = catchType;
            }

            @Override
            public String toString() {
                return new StringJoiner(",", "{", "}")
                        .add("\"startPc\":" + startPc)
                        .add("\"endPc\":" + endPc)
                        .add("\"handlerPc\":" + handlerPc)
                        .add("\"catchType\":" + catchType)
                        .toString();
            }
        }

        public class LineNumber{
            private int startPc;    // Program Counter (PC) corresponds to line
            private int lineNumber; // number in source file
            public LineNumber(int startPc, int lineNumber) {
                this.startPc = startPc;
                this.lineNumber = lineNumber;
            }

            @Override
            public String toString() {
                return new StringJoiner(",", "{", "}")
                        .add("\"startPc\":" + startPc)
                        .add("\"lineNumber\":" + lineNumber)
                        .toString();
            }
        }

        public class LocalVariable{
            private int startPc;        // Range in which the variable is valid
            private int length;
            private int nameIndex;      // Index in constant pool of variable name
            private int signatureIndex; // Index of variable signature
            private int index;     // Variable is `index'th local variable on this method's frame.
            public LocalVariable(int startPc, int length, int nameIndex, int signatureIndex, int index) {
                this.startPc = startPc;
                this.length = length;
                this.nameIndex = nameIndex;
                this.signatureIndex = signatureIndex;
                this.index = index;
            }

            @Override
            public String toString() {
                return new StringJoiner(",", "{", "}")
                        .add("\"index\":" + index)
                        .add("\"name\":\"" + constantPool.getUtf8ForToString(nameIndex)+"\"")
                        .add("\"signatureName\":\"" + constantPool.getUtf8ForToString(signatureIndex)+"\"")
                        .add("\"startPc\":" + startPc)
                        .add("\"length\":" + length)
                        .add("\"nameIndex\":" + nameIndex)
                        .add("\"signatureIndex\":" + signatureIndex)
                        .toString();
            }

            public int startPc() {
                return startPc;
            }

            public int nameIndex() {
                return nameIndex;
            }

            public int signatureIndex() {
                return signatureIndex;
            }

            public int index() {
                return index;
            }

            public int length() {
                return length;
            }

            public String name() {
                return constantPool.getUtf8(nameIndex);
            }

            public String signatureName() {
                return constantPool.getUtf8(signatureIndex);
            }
        }

        public class InnerClass{
            private int innerClassIndex;
            private int outerClassIndex;
            private int innerNameIndex;
            private int innerAccessFlags;
            public InnerClass(int innerClassIndex, int outerClassIndex, int innerNameIndex, int innerAccessFlags) {
                this.innerClassIndex = innerClassIndex;
                this.outerClassIndex = outerClassIndex;
                this.innerNameIndex = innerNameIndex;
                this.innerAccessFlags = innerAccessFlags;
            }

            public String innerName() {
                if(innerNameIndex == 0){
                    return null;
                }else {
                    return constantPool.getUtf8(innerNameIndex);
                }
            }
            public String innerClassName() {
                return constantPool.getClassName(innerClassIndex);
            }
            public String outerClassName() {
                if(outerClassIndex == 0){
                    return null;
                }else {
                    return constantPool.getClassName(outerClassIndex);
                }
            }
            @Override
            public String toString() {
                String innerName = constantPool.getUtf8ForToString(innerNameIndex);
                String toStringInnerName = innerName == null? "null":"\"" + innerName+"\"";
                String outerClassName = constantPool.getClassNameForToString(outerClassIndex);
                String toStringOuterClassName = outerClassName == null? "null":"\"" + outerClassName+"\"";

                return new StringJoiner(",", "{", "}")
                        .add("\"innerAccessFlags\":\"" + Modifier.toString(innerAccessFlags)+"\"")
                        .add("\"innerName\":" +toStringInnerName)
                        .add("\"innerClassName\":\"" + innerClassName()+"\"")
                        .add("\"outerClassName\":" + toStringOuterClassName)
                        .add("\"innerNameIndex\":" + innerNameIndex)
                        .add("\"innerClassIndex\":" + innerClassIndex)
                        .add("\"outerClassIndex\":" + outerClassIndex)
                        .toString();
            }
        }

        public class StackMapEntry{
            private int byteCodeOffset;
            private StackMapType[] typesOfLocals;
            public StackMapEntry(int byteCodeOffset, int typesOfLocalsSize) {
                this.byteCodeOffset = byteCodeOffset;
                if(typesOfLocalsSize == 0){
                    this.typesOfLocals = EMPTY_STACK_MAP_TYPE;
                }else {
                    this.typesOfLocals = new StackMapType[typesOfLocalsSize];
                }
            }

            @Override
            public String toString() {
                return new StringJoiner(",", "{", "}")
                        .add("\"byteCodeOffset\":" + byteCodeOffset)
                        .add("\"typesOfLocals\":\"" + toJsonArray(typesOfLocals))
                        .toString();
            }
        }

        public class StackMapType{
            private byte type;
            private int objectVariableIndex = -1;
            private int offset = -1;

            public StackMapType(ClassReader reader) {
                type = reader.readInt8();
                if (type == Opcodes.ITEM_OBJECT) {
                    objectVariableIndex = reader.readInt16();
                }else if(type == Opcodes.ITEM_UNINITIALIZED){
                    offset = reader.readInt16();
                }
            }

            public String getTypeName() {
                switch (type){
                    case Opcodes.ITEM_TOP:{
                        return "top";
                    }
                    case Opcodes.ITEM_INTEGER:{
                        return "integer";
                    }
                    case Opcodes.ITEM_FLOAT:{
                        return "float";
                    }
                    case Opcodes.ITEM_DOUBLE:{
                        return "double";
                    }
                    case Opcodes.ITEM_LONG:{
                        return "long";
                    }
                    case Opcodes.ITEM_NULL:{
                        return "null";
                    }
                    case Opcodes.ITEM_UNINITIALIZED_THIS:{
                        return "uninitializedThis";
                    }
                    case Opcodes.ITEM_OBJECT:{
                        return "object";
                    }
                    case Opcodes.ITEM_UNINITIALIZED:{
                        return "uninitialized";
                    }default:{
                        return "unkown";
                    }
                }
            }

            @Override
            public String toString() {
                StringJoiner joiner = new StringJoiner(",", "{", "}")
                        .add("\"type\":" + type)
                        .add("\"typeName\":\"" + getTypeName()+"\"");
                if(type == Opcodes.ITEM_OBJECT){
                    joiner.add("\"objectVariableIndex\":" + objectVariableIndex);
                    joiner.add("\"objectVariable\":\"" + constantPool.getClassNameForToString(objectVariableIndex)+"\"");
                }
                if(type == Opcodes.ITEM_UNINITIALIZED){
                    joiner.add("\"offset\":" + offset);
                }
                return joiner.toString();
            }
        }
        public class StackMapFrame{
            private short frameType;
            private String frameTypeName;
            private Integer offsetDelta;
            private StackMapType[] stacks;
            private StackMapType[] locals;
            public StackMapFrame(ClassReader reader) {
                frameType= reader.readUint8();
                if (frameType >= 0 && frameType <= 63){
                    frameTypeName = "same";
                }else if (frameType >= 64 && frameType <= 127){
                    frameTypeName = "same_locals_1_stack_item_frame";
                    stacks = new StackMapType[]{new StackMapType(reader)};
                }else if (frameType == 247){
                    frameTypeName = "same_locals_1_stack_item_frame_extended";
                    offsetDelta = reader.readUint16();
                    stacks = new StackMapType[]{new StackMapType(reader)};
                }else if (frameType >= 248 && frameType <= 250){
                    frameTypeName = "chop_frame";
                    offsetDelta = reader.readUint16();
                }else if (frameType == 251){
                    frameTypeName = "same_frame_extended";
                    offsetDelta = reader.readUint16();
                }else if (frameType >= 252 && frameType <=254){
                    frameTypeName = "append_frame";
                    offsetDelta = reader.readUint16();

                    locals = new StackMapType[frameType - 251];
                    for(int i=0; i<locals.length; i++){
                        locals[i] = new StackMapType(reader);
                    }
                }else if (frameType == 255){
                    frameTypeName = "full_frame";
                    offsetDelta = reader.readUint16();

                    locals = new StackMapType[reader.readUint16()];
                    for(int i=0; i<locals.length; i++){
                        locals[i] = new StackMapType(reader);
                    }

                    stacks = new StackMapType[reader.readUint16()];
                    for(int i=0; i<stacks.length; i++){
                        stacks[i] = new StackMapType(reader);
                    }
                }
            }
            public short getFrameType() {
                return frameType;
            }
            public String getFrameTypeName() {
                return frameTypeName;
            }
            public Integer getOffsetDelta() {
                return offsetDelta;
            }
            public StackMapType[] getStacks() {
                return stacks;
            }
            public StackMapType[] getLocals() {
                return locals;
            }
            @Override
            public String toString() {
                StringJoiner joiner = new StringJoiner(",", "{", "}")
                        .add("\"frameType\":" + frameType)
                        .add("\"frameTypeName\":\""+ frameTypeName+"\"");
                if(offsetDelta != null){
                    joiner.add("\"offsetDelta\":" + offsetDelta);
                }
                if(stacks != null) {
                    joiner.add("\"stacks\":" + toJsonArray(stacks));
                }
                if(locals != null) {
                    joiner.add("\"locals\":" + toJsonArray(locals));
                }
                return joiner.toString();
            }
        }

        public class Annotation{
            private int typeIndex;
            private ElementValue[] elementValues;
            public Annotation(ClassReader reader) {
                this.typeIndex = reader.readUint16();;
                this.elementValues = new ElementValue[reader.readUint16()];
                for(int i = 0; i< elementValues.length; i++){
                    int valueIndex = reader.readUint16();
                    char tag = (char) reader.readInt8();
                    this.elementValues[i] = newElementValue(tag,reader);
                    this.elementValues[i].valueIndex = valueIndex;
                }
            }

            public Annotation.ElementValue newElementValue(char tag,ClassReader reader){
                Annotation.ElementValue newElementValue;
                switch (tag) {
                    case 'e': {
                        Annotation.EnumElementValue elementValue = new Annotation.EnumElementValue();
                        elementValue.typeNameIndex = reader.readUint16();
                        elementValue.constNameIndex = reader.readUint16();
                        newElementValue = elementValue;
                        break;
                    }
                    case '@':{
                        Annotation.AnnotationElementValue elementValue = new Annotation.AnnotationElementValue();
                        elementValue.annotationValue = new Annotation(reader);
                        newElementValue = elementValue;
                        break;
                    }
                    case 'c': {
                        Annotation.ClassElementValue elementValue = new Annotation.ClassElementValue();
                        elementValue.classInfoIndex = reader.readUint16();
                        newElementValue = elementValue;
                        break;
                    }
                    case '[': {
                        Annotation.ArrayElementValue elementValue = new Annotation.ArrayElementValue();
                        ElementValue[] elementValues = new ElementValue[reader.readUint16()];
                        for(int i=0; i<elementValues.length; i++){
                            char elementTag = (char) reader.readInt8();
                            elementValues[i] = newElementValue(elementTag,reader);
                        }
                        elementValue.arrayValue = elementValues;
                        newElementValue = elementValue;
                        break;
                    }
                    case 'B':{
                        Annotation.ByteElementValue elementValue = new Annotation.ByteElementValue();
                        elementValue.constValueIndex = reader.readUint16();
                        newElementValue = elementValue;
                        break;
                    }
                    case 'C':{
                        Annotation.CharElementValue elementValue = new Annotation.CharElementValue();
                        elementValue.constValueIndex = reader.readUint16();
                        newElementValue = elementValue;
                        break;
                    }
                    case 'D':{
                        Annotation.DoubleElementValue elementValue = new Annotation.DoubleElementValue();
                        elementValue.constValueIndex = reader.readUint16();
                        newElementValue = elementValue;
                        break;
                    }
                    case 'F':{
                        Annotation.FloatElementValue elementValue = new Annotation.FloatElementValue();
                        elementValue.constValueIndex = reader.readUint16();
                        newElementValue = elementValue;
                        break;
                    }
                    case 'I':{
                        Annotation.IntElementValue elementValue = new Annotation.IntElementValue();
                        elementValue.constValueIndex = reader.readUint16();
                        newElementValue = elementValue;
                        break;
                    }
                    case 'J':{
                        Annotation.LongElementValue elementValue = new Annotation.LongElementValue();
                        elementValue.constValueIndex = reader.readUint16();
                        newElementValue = elementValue;
                        break;
                    }
                    case 'S':{
                        Annotation.ShortElementValue elementValue = new Annotation.ShortElementValue();
                        elementValue.constValueIndex = reader.readUint16();
                        newElementValue = elementValue;
                        break;
                    }
                    case 'Z':{
                        Annotation.BooleanElementValue elementValue = new Annotation.BooleanElementValue();
                        elementValue.constValueIndex = reader.readUint16();
                        newElementValue = elementValue;
                        break;
                    }
                    case 's':{
                        Annotation.StringElementValue elementValue = new Annotation.StringElementValue();
                        elementValue.constValueIndex = reader.readUint16();
                        newElementValue = elementValue;
                        break;
                    }
                    default:{
                        throw new IllegalStateException("a unkown annotation tag. tag = '"+tag+"'");
                    }
                }
                newElementValue.tag = tag;
                return newElementValue;
            }

            @Override
            public String toString() {
                return new StringJoiner(",", "{", "}")
                        .add("\"typeIndex\":" + typeIndex)
                        .add("\"type\":\"" + constantPool.getUtf8ForToString(typeIndex) + "\"")
                        .add("\"elementValues\":" + toJsonArray(elementValues))
                        .toString();
            }

            public class ElementValue{
                protected int valueIndex;
                protected char tag;

                @Override
                public String toString() {
                    StringJoiner joiner = new StringJoiner(",", "{", "}")
                            .add("\"tag\":\"" + tag+"\"")
                            .add("\"type\":\"" + getClass().getSimpleName()+"\"");
                    if(valueIndex != 0) {
                        joiner.add("\"valueIndex\":" + valueIndex);
                        joiner.add("\"value\":\"" + constantPool.getUtf8ForToString(valueIndex) + "\"");
                    }
                    toStringAppend(joiner);
                    return joiner.toString();
                }

                public void toStringAppend(StringJoiner joiner){}
            }
            public class ClassElementValue extends ElementValue{
                private int classInfoIndex;
                @Override
                public void toStringAppend(StringJoiner joiner) {
                    joiner.add("\"classInfoIndex\":" + classInfoIndex)
                            .add("\"classInfo\":\"" + constantPool.getUtf8ForToString(classInfoIndex)+"\"");
                }
            }
            public class StringElementValue extends ElementValue{
                private int constValueIndex;
                @Override
                public void toStringAppend(StringJoiner joiner) {
                    joiner.add("\"constValueIndex\":" + constValueIndex)
                            .add("\"constValue\":\"" + constantPool.getUtf8ForToString(constValueIndex)+"\"");
                }
            }
            public class ByteElementValue extends ElementValue{
                private int constValueIndex;
                @Override
                public void toStringAppend(StringJoiner joiner) {
                    joiner.add("\"constValueIndex\":" + constValueIndex)
                            .add("\"constValue\":" + constantPool.getInteger(constValueIndex));
                }
            }
            public class CharElementValue extends ElementValue{
                private int constValueIndex;
                @Override
                public void toStringAppend(StringJoiner joiner) {
                    joiner.add("\"constValueIndex\":" + constValueIndex)
                            .add("\"constValue\":" + constantPool.getInteger(constValueIndex));
                }
            }
            public class DoubleElementValue extends ElementValue{
                private int constValueIndex;
                @Override
                public void toStringAppend(StringJoiner joiner) {
                    joiner.add("\"constValueIndex\":" + constValueIndex)
                            .add("\"constValue\":" + constantPool.getDouble(constValueIndex));
                }
            }
            public class FloatElementValue extends ElementValue{
                private int constValueIndex;
                @Override
                public void toStringAppend(StringJoiner joiner) {
                    joiner.add("\"constValueIndex\":" + constValueIndex)
                            .add("\"constValue\":" + constantPool.getFloat(constValueIndex));
                }
            }
            public class IntElementValue extends ElementValue{
                private int constValueIndex;
                @Override
                public void toStringAppend(StringJoiner joiner) {
                    joiner.add("\"constValueIndex\":" + constValueIndex)
                            .add("\"constValue\":" + constantPool.getInteger(constValueIndex));
                }
            }
            public class LongElementValue extends ElementValue{
                private int constValueIndex;
                @Override
                public void toStringAppend(StringJoiner joiner) {
                    joiner.add("\"constValueIndex\":" + constValueIndex)
                            .add("\"constValue\":" + constantPool.getLong(constValueIndex));
                }
            }
            public class ShortElementValue extends ElementValue{
                private int constValueIndex;
                @Override
                public void toStringAppend(StringJoiner joiner) {
                    joiner.add("\"constValueIndex\":" + constValueIndex)
                            .add("\"constValue\":" + constantPool.getInteger(constValueIndex));
                }
            }
            public class BooleanElementValue extends ElementValue{
                private int constValueIndex;
                @Override
                public void toStringAppend(StringJoiner joiner) {
                    joiner.add("\"constValueIndex\":" + constValueIndex)
                            .add("\"constValue\":" + constantPool.getInteger(constValueIndex));
                }
            }
            public class ArrayElementValue extends ElementValue{
                private ElementValue[] arrayValue;
                @Override
                public void toStringAppend(StringJoiner joiner) {
                    joiner.add("\"arrayValue\":" + toJsonArray(arrayValue))
                            .add("\"length\":" + arrayValue.length);
                }
            }
            public class AnnotationElementValue extends ElementValue{
                private Annotation annotationValue;
                @Override
                public void toStringAppend(StringJoiner joiner) {
                    joiner.add("\"annotationValue\":" + annotationValue);
                }
            }
            public class EnumElementValue extends ElementValue{
                private int constNameIndex;
                private int typeNameIndex;
                @Override
                public void toStringAppend(StringJoiner joiner) {
                    joiner.add("\"constNameIndex\":" + constNameIndex)
                            .add("\"constName\":\"" + constantPool.getUtf8ForToString(constNameIndex)+"\"")
                            .add("\"typeNameIndex\":" + typeNameIndex)
                            .add("\"typeName\":\"" + constantPool.getUtf8ForToString(typeNameIndex)+"\"");
                }
            }
        }
    }

    public static class ClassReader implements Closeable{
        /** 字节码数组 */
        private byte[] codes;
        /** 当前读取数组的下标 */
        private int index;
        /** 文件大小 */
        private int length;
        /** 标记下标,用于回滚 */
        private int markIndex;

        public ClassReader(String path, String fileName) throws FileNotFoundException,IOException {
            this(new FileInputStream(new File(path + File.separator + fileName)));
        }

        public ClassReader(InputStream in) throws IOException {
            try {
                byte[] buffer = new byte[in.available()];
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                while (in.read(buffer) != -1) {
                    out.write(buffer);
                }
                this.codes = out.toByteArray();
                this.length = this.codes.length;
            }finally {
                in.close();
            }
        }

        public ClassReader(byte[] codes) {
            this.codes = codes;
            this.length = codes.length;
        }

        public void mark() {
            this.markIndex = index;
        }

        public void reset() {
            this.index = this.markIndex;
        }

        /**
         * 读取8位-无符号
         * @return 8位-无符号
         */
        public short readUint8(){
            return (short) (readInt8() & 0x0FF);
        }

        /**
         * 读取8位-有符号
         * @return 8位-有符号
         */
        public byte readInt8(){
            byte value = codes[index];
            index = index + 1;
            return value;
        }

        /**
         * 读取16位-无符号
         * @return 16位-无符号
         */
        public int readUint16(){
            return readInt16() & 0x0FFFF;
        }

        /**
         * 读取16位-有符号
         * @return 16位-有符号
         */
        public int readInt16(){
            int value = (short) (codes[index] << 8 | codes[index + 1] & 0xFF);
            index = index + 2;
            return value;
        }

        /**
         * 读取32位-有符号
         * @return 32位-有符号
         */
        public int readInt32(){
            int value = (codes[index] & 0xff) << 24 |
                    (codes[index + 1] & 0xff) << 16 |
                    (codes[index + 2] & 0xff) <<  8 |
                    codes[index + 3] & 0xff;
            index = index + 4;
            return value;
        }

        /**
         * 读取64位-无符号
         * @return 64位-无符号
         */
        public long readUint64(){
            long value = ((long) codes[index]     & 0xff) << 56 |
                    ((long) codes[index + 1] & 0xff) << 48 |
                    ((long) codes[index + 2] & 0xff) << 40 |
                    ((long) codes[index + 3] & 0xff) << 32 |
                    ((long) codes[index + 4] & 0xff) << 24 |
                    ((long) codes[index + 5] & 0xff) << 16 |
                    ((long) codes[index + 6] & 0xff) <<  8 |
                    (long) codes[index + 7] & 0xff;
            index = index + 8;
            return value;
        }

        /**
         * 读取16位-无符号-数组
         * @return 16位-无符号-数组
         */
        public int[] readUint16s(){
            int length = readUint16();
            int[] values = new int[length];
            for(int i=0; i<length; i++){
                values[i] = readUint16();
            }
            return values;
        }

        /**
         * 读取8位-有符号-数组
         * @param length 长度
         * @return 8位-有符号-数组
         */
        public byte[] readInt8s(int length){
            byte[] values = new byte[length];
            for(int i=0; i<length; i++){
                values[i] = readInt8();
            }
            return values;
        }

        /**
         * 读取8位-无符号-数组
         * @param length 长度
         * @return 8位-无符号-数组
         */
        public short[] readUInt8s(int length){
            short[] values = new short[length];
            for(int i=0; i<length; i++){
                values[i] = readUint8();
            }
            return values;
        }

        @Override
        public void close(){
            this.codes = null;
        }

        @Override
        public String toString() {
            return new StringJoiner(",","{","}")
                .add("\"file\":\""+length+"b, "+(length/1024)+"kb\"")
                .add("\"readIndex\":"+index)
                .toString();
        }
    }

    public static class Opcodes {

        // Possible values for the type operand of the NEWARRAY instruction.
        // See https://docs.oracle.com/javase/specs/jvms/se9/html/jvms-6.html#jvms-6.5.newarray.

        public static final int T_BOOLEAN = 4;
        public static final int  T_CHAR = 5;
        public static final int T_FLOAT = 6;
        public static final int  T_DOUBLE = 7;
        public static final int  T_BYTE = 8;
        public static final int  T_SHORT = 9;
        public static final int  T_INT = 10;
        public static final int  T_LONG = 11;

        // Possible values for the reference_kind field of CONSTANT_MethodHandle_info structures.
        // See https://docs.oracle.com/javase/specs/jvms/se9/html/jvms-4.html#jvms-4.4.8.

        public static final int  H_GETFIELD = 1;
        public static final int  H_GETSTATIC = 2;
        public static final int  H_PUTFIELD = 3;
        public static final int  H_PUTSTATIC = 4;
        public static final int  H_INVOKEVIRTUAL = 5;
        public static final int  H_INVOKESTATIC = 6;
        public static final int  H_INVOKESPECIAL = 7;
        public static final int  H_NEWINVOKESPECIAL = 8;
        public static final int  H_INVOKEINTERFACE = 9;


        /** An expanded frame.
         *
         * A flag to expand the stack map frames. By default stack map frames are visited in their
         * original format (i.e. "expanded" for classes whose version is less than V1_6, and "compressed"
         * for the other classes). If this flag is set, stack map frames are always visited in expanded
         * format (this option adds a decompression/compression step in ClassReader and ClassWriter which
         * degrades performance quite a lot).
         */
        public static final int  F_NEW = -1;

        /** A compressed frame with complete frame data. */
        public static final int  F_FULL = 0;

        /**
         * A compressed frame where locals are the same as the locals in the previous frame, except that
         * additional 1-3 locals are defined, and with an empty stack.
         */
        public static final int  F_APPEND = 1;

        /**
         * A compressed frame where locals are the same as the locals in the previous frame, except that
         * the last 1-3 locals are absent and with an empty stack.
         */
        public static final int  F_CHOP = 2;

        /**
         * A compressed frame with exactly the same locals as the previous frame and with an empty stack.
         */
        public static final int  F_SAME = 3;

        /**
         * A compressed frame with exactly the same locals as the previous frame and with a single value
         * on the stack.
         */
        public static final int  F_SAME1 = 4;

        // Standard stack map frame element types, .

        public static final byte ITEM_TOP = 0;
        public static final byte ITEM_INTEGER = 1;
        public static final byte ITEM_FLOAT = 2;
        public static final byte ITEM_DOUBLE = 3;
        public static final byte ITEM_LONG = 4;
        public static final byte ITEM_NULL = 5;
        public static final byte ITEM_UNINITIALIZED_THIS = 6;
        public static final byte ITEM_OBJECT = 7;
        public static final byte ITEM_UNINITIALIZED = 8;

        // The JVM opcode values (with the MethodVisitor method name used to visit them in comment, and
        // where '-' means 'same method name as on the previous line').
        // See https://docs.oracle.com/javase/specs/jvms/se9/html/jvms-6.html.

        /** Java VM opcodes.
         */
        public static final short NOP              = 0;
        public static final short ACONST_NULL      = 1;
        public static final short ICONST_M1        = 2;
        public static final short ICONST_0         = 3;
        public static final short ICONST_1         = 4;
        public static final short ICONST_2         = 5;
        public static final short ICONST_3         = 6;
        public static final short ICONST_4         = 7;
        public static final short ICONST_5         = 8;
        public static final short LCONST_0         = 9;
        public static final short LCONST_1         = 10;
        public static final short FCONST_0         = 11;
        public static final short FCONST_1         = 12;
        public static final short FCONST_2         = 13;
        public static final short DCONST_0         = 14;
        public static final short DCONST_1         = 15;
        public static final short BIPUSH           = 16;
        public static final short SIPUSH           = 17;
        public static final short LDC              = 18;
        public static final short LDC_W            = 19;
        public static final short LDC2_W           = 20;
        public static final short ILOAD            = 21;
        public static final short LLOAD            = 22;
        public static final short FLOAD            = 23;
        public static final short DLOAD            = 24;
        public static final short ALOAD            = 25;
        public static final short ILOAD_0          = 26;
        public static final short ILOAD_1          = 27;
        public static final short ILOAD_2          = 28;
        public static final short ILOAD_3          = 29;
        public static final short LLOAD_0          = 30;
        public static final short LLOAD_1          = 31;
        public static final short LLOAD_2          = 32;
        public static final short LLOAD_3          = 33;
        public static final short FLOAD_0          = 34;
        public static final short FLOAD_1          = 35;
        public static final short FLOAD_2          = 36;
        public static final short FLOAD_3          = 37;
        public static final short DLOAD_0          = 38;
        public static final short DLOAD_1          = 39;
        public static final short DLOAD_2          = 40;
        public static final short DLOAD_3          = 41;
        public static final short ALOAD_0          = 42;
        public static final short ALOAD_1          = 43;
        public static final short ALOAD_2          = 44;
        public static final short ALOAD_3          = 45;
        public static final short IALOAD           = 46;
        public static final short LALOAD           = 47;
        public static final short FALOAD           = 48;
        public static final short DALOAD           = 49;
        public static final short AALOAD           = 50;
        public static final short BALOAD           = 51;
        public static final short CALOAD           = 52;
        public static final short SALOAD           = 53;
        public static final short ISTORE           = 54;
        public static final short LSTORE           = 55;
        public static final short FSTORE           = 56;
        public static final short DSTORE           = 57;
        public static final short ASTORE           = 58;
        public static final short ISTORE_0         = 59;
        public static final short ISTORE_1         = 60;
        public static final short ISTORE_2         = 61;
        public static final short ISTORE_3         = 62;
        public static final short LSTORE_0         = 63;
        public static final short LSTORE_1         = 64;
        public static final short LSTORE_2         = 65;
        public static final short LSTORE_3         = 66;
        public static final short FSTORE_0         = 67;
        public static final short FSTORE_1         = 68;
        public static final short FSTORE_2         = 69;
        public static final short FSTORE_3         = 70;
        public static final short DSTORE_0         = 71;
        public static final short DSTORE_1         = 72;
        public static final short DSTORE_2         = 73;
        public static final short DSTORE_3         = 74;
        public static final short ASTORE_0         = 75;
        public static final short ASTORE_1         = 76;
        public static final short ASTORE_2         = 77;
        public static final short ASTORE_3         = 78;
        public static final short IASTORE          = 79;
        public static final short LASTORE          = 80;
        public static final short FASTORE          = 81;
        public static final short DASTORE          = 82;
        public static final short AASTORE          = 83;
        public static final short BASTORE          = 84;
        public static final short CASTORE          = 85;
        public static final short SASTORE          = 86;
        public static final short POP              = 87;
        public static final short POP2             = 88;
        public static final short DUP              = 89;
        public static final short DUP_X1           = 90;
        public static final short DUP_X2           = 91;
        public static final short DUP2             = 92;
        public static final short DUP2_X1          = 93;
        public static final short DUP2_X2          = 94;
        public static final short SWAP             = 95;
        public static final short IADD             = 96;
        public static final short LADD             = 97;
        public static final short FADD             = 98;
        public static final short DADD             = 99;
        public static final short ISUB             = 100;
        public static final short LSUB             = 101;
        public static final short FSUB             = 102;
        public static final short DSUB             = 103;
        public static final short IMUL             = 104;
        public static final short LMUL             = 105;
        public static final short FMUL             = 106;
        public static final short DMUL             = 107;
        public static final short IDIV             = 108;
        public static final short LDIV             = 109;
        public static final short FDIV             = 110;
        public static final short DDIV             = 111;
        public static final short IREM             = 112;
        public static final short LREM             = 113;
        public static final short FREM             = 114;
        public static final short DREM             = 115;
        public static final short INEG             = 116;
        public static final short LNEG             = 117;
        public static final short FNEG             = 118;
        public static final short DNEG             = 119;
        public static final short ISHL             = 120;
        public static final short LSHL             = 121;
        public static final short ISHR             = 122;
        public static final short LSHR             = 123;
        public static final short IUSHR            = 124;
        public static final short LUSHR            = 125;
        public static final short IAND             = 126;
        public static final short LAND             = 127;
        public static final short IOR              = 128;
        public static final short LOR              = 129;
        public static final short IXOR             = 130;
        public static final short LXOR             = 131;
        public static final short IINC             = 132;
        public static final short I2L              = 133;
        public static final short I2F              = 134;
        public static final short I2D              = 135;
        public static final short L2I              = 136;
        public static final short L2F              = 137;
        public static final short L2D              = 138;
        public static final short F2I              = 139;
        public static final short F2L              = 140;
        public static final short F2D              = 141;
        public static final short D2I              = 142;
        public static final short D2L              = 143;
        public static final short D2F              = 144;
        public static final short I2B              = 145;
        public static final short INT2BYTE         = 145; // Old notion
        public static final short I2C              = 146;
        public static final short INT2CHAR         = 146; // Old notion
        public static final short I2S              = 147;
        public static final short INT2SHORT        = 147; // Old notion
        public static final short LCMP             = 148;
        public static final short FCMPL            = 149;
        public static final short FCMPG            = 150;
        public static final short DCMPL            = 151;
        public static final short DCMPG            = 152;
        public static final short IFEQ             = 153;
        public static final short IFNE             = 154;
        public static final short IFLT             = 155;
        public static final short IFGE             = 156;
        public static final short IFGT             = 157;
        public static final short IFLE             = 158;
        public static final short IF_ICMPEQ        = 159;
        public static final short IF_ICMPNE        = 160;
        public static final short IF_ICMPLT        = 161;
        public static final short IF_ICMPGE        = 162;
        public static final short IF_ICMPGT        = 163;
        public static final short IF_ICMPLE        = 164;
        public static final short IF_ACMPEQ        = 165;
        public static final short IF_ACMPNE        = 166;
        public static final short GOTO             = 167;
        public static final short JSR              = 168;
        public static final short RET              = 169;
        public static final short TABLESWITCH      = 170;
        public static final short LOOKUPSWITCH     = 171;
        public static final short IRETURN          = 172;
        public static final short LRETURN          = 173;
        public static final short FRETURN          = 174;
        public static final short DRETURN          = 175;
        public static final short ARETURN          = 176;
        public static final short RETURN           = 177;
        public static final short GETSTATIC        = 178;
        public static final short PUTSTATIC        = 179;
        public static final short GETFIELD         = 180;
        public static final short PUTFIELD         = 181;
        public static final short INVOKEVIRTUAL    = 182;
        public static final short INVOKESPECIAL    = 183;
        public static final short INVOKENONVIRTUAL = 183; // Old name in JDK 1.0
        public static final short INVOKESTATIC     = 184;
        public static final short INVOKEINTERFACE  = 185;
        public static final short NEW              = 187;
        public static final short NEWARRAY         = 188;
        public static final short ANEWARRAY        = 189;
        public static final short ARRAYLENGTH      = 190;
        public static final short ATHROW           = 191;
        public static final short CHECKCAST        = 192;
        public static final short INSTANCEOF       = 193;
        public static final short MONITORENTER     = 194;
        public static final short MONITOREXIT      = 195;
        public static final short WIDE             = 196;
        public static final short MULTIANEWARRAY   = 197;
        public static final short IFNULL           = 198;
        public static final short IFNONNULL        = 199;
        public static final short GOTO_W           = 200;
        public static final short JSR_W            = 201;

        /**
         * Non-legal opcodes, may be used by JVM internally.
         */
        public static final short BREAKPOINT                = 202;
        public static final short LDC_QUICK                 = 203;
        public static final short LDC_W_QUICK               = 204;
        public static final short LDC2_W_QUICK              = 205;
        public static final short GETFIELD_QUICK            = 206;
        public static final short PUTFIELD_QUICK            = 207;
        public static final short GETFIELD2_QUICK           = 208;
        public static final short PUTFIELD2_QUICK           = 209;
        public static final short GETSTATIC_QUICK           = 210;
        public static final short PUTSTATIC_QUICK           = 211;
        public static final short GETSTATIC2_QUICK          = 212;
        public static final short PUTSTATIC2_QUICK          = 213;
        public static final short INVOKEVIRTUAL_QUICK       = 214;
        public static final short INVOKENONVIRTUAL_QUICK    = 215;
        public static final short INVOKESUPER_QUICK         = 216;
        public static final short INVOKESTATIC_QUICK        = 217;
        public static final short INVOKEINTERFACE_QUICK     = 218;
        public static final short INVOKEVIRTUALOBJECT_QUICK = 219;
        public static final short NEW_QUICK                 = 221;
        public static final short ANEWARRAY_QUICK           = 222;
        public static final short MULTIANEWARRAY_QUICK      = 223;
        public static final short CHECKCAST_QUICK           = 224;
        public static final short INSTANCEOF_QUICK          = 225;
        public static final short INVOKEVIRTUAL_QUICK_W     = 226;
        public static final short GETFIELD_QUICK_W          = 227;
        public static final short PUTFIELD_QUICK_W          = 228;
        public static final short IMPDEP1                   = 254;
        public static final short IMPDEP2                   = 255;


        public static final String ILLEGAL_OPCODE = "<illegal opcode>";
        public static final String ILLEGAL_TYPE   = "<illegal type>";

        /**
         * Names of opcodes.
         */
        public static final String[] OPCODE_NAMES = {
                "nop", "aconst_null", "iconst_m1", "iconst_0", "iconst_1",
                "iconst_2", "iconst_3", "iconst_4", "iconst_5", "lconst_0",
                "lconst_1", "fconst_0", "fconst_1", "fconst_2", "dconst_0",
                "dconst_1", "bipush", "sipush", "ldc", "ldc_w", "ldc2_w", "iload",
                "lload", "fload", "dload", "aload", "iload_0", "iload_1", "iload_2",
                "iload_3", "lload_0", "lload_1", "lload_2", "lload_3", "fload_0",
                "fload_1", "fload_2", "fload_3", "dload_0", "dload_1", "dload_2",
                "dload_3", "aload_0", "aload_1", "aload_2", "aload_3", "iaload",
                "laload", "faload", "daload", "aaload", "baload", "caload", "saload",
                "istore", "lstore", "fstore", "dstore", "astore", "istore_0",
                "istore_1", "istore_2", "istore_3", "lstore_0", "lstore_1",
                "lstore_2", "lstore_3", "fstore_0", "fstore_1", "fstore_2",
                "fstore_3", "dstore_0", "dstore_1", "dstore_2", "dstore_3",
                "astore_0", "astore_1", "astore_2", "astore_3", "iastore", "lastore",
                "fastore", "dastore", "aastore", "bastore", "castore", "sastore",
                "pop", "pop2", "dup", "dup_x1", "dup_x2", "dup2", "dup2_x1",
                "dup2_x2", "swap", "iadd", "ladd", "fadd", "dadd", "isub", "lsub",
                "fsub", "dsub", "imul", "lmul", "fmul", "dmul", "idiv", "ldiv",
                "fdiv", "ddiv", "irem", "lrem", "frem", "drem", "ineg", "lneg",
                "fneg", "dneg", "ishl", "lshl", "ishr", "lshr", "iushr", "lushr",
                "iand", "land", "ior", "lor", "ixor", "lxor", "iinc", "i2l", "i2f",
                "i2d", "l2i", "l2f", "l2d", "f2i", "f2l", "f2d", "d2i", "d2l", "d2f",
                "i2b", "i2c", "i2s", "lcmp", "fcmpl", "fcmpg",
                "dcmpl", "dcmpg", "ifeq", "ifne", "iflt", "ifge", "ifgt", "ifle",
                "if_icmpeq", "if_icmpne", "if_icmplt", "if_icmpge", "if_icmpgt",
                "if_icmple", "if_acmpeq", "if_acmpne", "goto", "jsr", "ret",
                "tableswitch", "lookupswitch", "ireturn", "lreturn", "freturn",
                "dreturn", "areturn", "return", "getstatic", "putstatic", "getfield",
                "putfield", "invokevirtual", "invokespecial", "invokestatic",
                "invokeinterface", ILLEGAL_OPCODE, "new", "newarray", "anewarray",
                "arraylength", "athrow", "checkcast", "instanceof", "monitorenter",
                "monitorexit", "wide", "multianewarray", "ifnull", "ifnonnull",
                "goto_w", "jsr_w", "breakpoint", ILLEGAL_OPCODE, ILLEGAL_OPCODE,
                ILLEGAL_OPCODE, ILLEGAL_OPCODE, ILLEGAL_OPCODE, ILLEGAL_OPCODE,
                ILLEGAL_OPCODE, ILLEGAL_OPCODE, ILLEGAL_OPCODE, ILLEGAL_OPCODE,
                ILLEGAL_OPCODE, ILLEGAL_OPCODE, ILLEGAL_OPCODE, ILLEGAL_OPCODE,
                ILLEGAL_OPCODE, ILLEGAL_OPCODE, ILLEGAL_OPCODE, ILLEGAL_OPCODE,
                ILLEGAL_OPCODE, ILLEGAL_OPCODE, ILLEGAL_OPCODE, ILLEGAL_OPCODE,
                ILLEGAL_OPCODE, ILLEGAL_OPCODE, ILLEGAL_OPCODE, ILLEGAL_OPCODE,
                ILLEGAL_OPCODE, ILLEGAL_OPCODE, ILLEGAL_OPCODE, ILLEGAL_OPCODE,
                ILLEGAL_OPCODE, ILLEGAL_OPCODE, ILLEGAL_OPCODE, ILLEGAL_OPCODE,
                ILLEGAL_OPCODE, ILLEGAL_OPCODE, ILLEGAL_OPCODE, ILLEGAL_OPCODE,
                ILLEGAL_OPCODE, ILLEGAL_OPCODE, ILLEGAL_OPCODE, ILLEGAL_OPCODE,
                ILLEGAL_OPCODE, ILLEGAL_OPCODE, ILLEGAL_OPCODE, ILLEGAL_OPCODE,
                ILLEGAL_OPCODE, ILLEGAL_OPCODE, ILLEGAL_OPCODE, ILLEGAL_OPCODE,
                ILLEGAL_OPCODE, "impdep1", "impdep2"
        };

        private short[] opcodes;
        public Opcodes(short[] opcodes) {
            this.opcodes = opcodes;
        }

        public short[] getOpcodes() {
            return opcodes;
        }

        @Override
        public String toString(){
            StringJoiner joiner = new StringJoiner(",","[","]");
            for(int i=0; i<opcodes.length; i++){
                int opcode = opcodes[i];
                String name = OPCODE_NAMES[opcode];
                joiner.add("{\"index\":"+i+",\"opcode\":"+opcode+",\"name\":\""+name+"\"}");
            }
            return joiner.toString();
        }
    }


    public static void main(String[] args) throws Exception{
        //这里换成自己的class包路径
        String path = "G:\\githubs\\spring-boot-protocol\\target\\classes\\com\\github\\netty\\protocol\\servlet";
        Map<String, JavaClassFile> javaClassMap = new HashMap<>();
        for(File file : new File(path).listFiles()){
            String fileName = file.getName();
            if(fileName.endsWith(".class")){
                JavaClassFile javaClassFile = new JavaClassFile(path, fileName);
                javaClassMap.put(fileName, javaClassFile);
            }
        }
        System.out.println("end..");
    }

}
