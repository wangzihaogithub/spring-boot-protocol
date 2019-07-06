package com.github.netty.core.util;

import java.io.*;
import java.lang.instrument.IllegalClassFormatException;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.util.*;

public class JavaClassFile {
    private static final Attribute.CodeException[] EMPTY_CODE_EXCEPTIONS = {};
    private static final Attribute.LineNumber[] EMPTY_LINE_NUMBER_TABLE = {};
    private static final Attribute.LocalVariable[] EMPTY_LOCAL_VARIABLE_TABLE = {};
    private static final Attribute.InnerClass[] EMPTY_INNER_CLASSES = {};
    private static final Attribute.StackMapEntry[] EMPTY_STACK_MAP_ENTRY = {};
    private static final Attribute.StackMapEntry.StackMapType[] EMPTY_STACK_MAP_TYPE = {};
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
            members[i].constantPool = constantPool;
            members[i].accessFlags = reader.readUint16();
            members[i].nameIndex = reader.readUint16();
            members[i].descriptorIndex = reader.readUint16();
            members[i].attributes = readAttributes(reader);
        }
        return members;
    }

    private Attribute[] readAttributes(ClassReader reader)  {
        int attributesCount = reader.readUint16();
        Attribute[] attributes = new Attribute[attributesCount];
        for(int i = 0; i<attributes.length;i++) {
            int attrNameIndex = reader.readUint16();
            attributes[i] =  new Attribute(attrNameIndex,reader.readInt32(),reader);
        }
        return attributes;
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
        return new StringJoiner(",","{","}")
                .add("\"majorVersion\":\""+majorVersion+"\"")
                .add("\"accessFlags\":\""+Modifier.toString(accessFlags)+"\"")
                .add("\"thisClassIndex\":"+ thisClassIndex)
                .add("\"className\":\""+ getThisClassName()+"\"")
                .add("\"superClassIndex\":"+ superClassIndex)
                .add("\"superClassName\":\""+ getSuperClassName()+"\"")
                .add("\"interfaces\":"+toJsonArray(getInterfaceNames()))
                .add("\"fields\":"+ toJsonArray(getFields()))
                .add("\"methods\":"+toJsonArray(getMethods()))
                .add("\"constantPool\":"+toJsonArray(constantPool.constants))
                .add("\"constantPoolDataLength\":"+
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

        if(array instanceof Object[]){
            StringJoiner joiner = new StringJoiner(",", "[", "]");
            for(Object e : (Object[]) array){
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
        if(array instanceof byte[]){
            return Arrays.toString((byte[]) array);
        }
        if(array instanceof int[]){
            return Arrays.toString((int[]) array);
        }
        if(array instanceof short[]){
            return Arrays.toString((short[]) array);
        }
        if(array instanceof long[]){
            return Arrays.toString((long[]) array);
        }
        if(array instanceof double[]){
            return Arrays.toString((long[]) array);
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
                ConstantInfo constantInfo = readConstantInfo(reader);
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

        private ConstantInfo readConstantInfo(ClassReader reader){
            int tag = reader.readUint8();
            ConstantInfo constantInfo;
            switch (tag) {
                case ConstantInfo.CONSTANT_INTEGER:
                    constantInfo = new ConstantIntegerInfo(reader);
                    break;
                case ConstantInfo.CONSTANT_FLOAT:
                    constantInfo = new ConstantFloatInfo(reader);
                    break;
                case ConstantInfo.CONSTANT_LONG:
                    constantInfo = new ConstantLongInfo(reader);
                    break;
                case ConstantInfo.CONSTANT_DOUBLE:
                    constantInfo = new ConstantDoubleInfo(reader);
                    break;
                case ConstantInfo.CONSTANT_UTF8:
                    constantInfo = new ConstantUtf8Info(reader);
                    break;
                case ConstantInfo.CONSTANT_STRING:
                    constantInfo = new ConstantStringInfo(reader);
                    break;
                case ConstantInfo.CONSTANT_CLASS:
                    constantInfo = new ConstantClassInfo(reader);
                    break;
                case ConstantInfo.CONSTANT_FIELD_REF:
                    constantInfo = new ConstantFieldRefInfo(new ConstantMemberRefInfo(reader));
                    break;
                case ConstantInfo.CONSTANT_METHOD_REF:
                    constantInfo = new ConstantMethodRefInfo(new ConstantMemberRefInfo(reader));
                    break;
                case ConstantInfo.CONSTANT_INTERFACE_METHOD_REF:
                    constantInfo = new ConstantInterfaceMethodRefInfo(new ConstantMemberRefInfo(reader));
                    break;
                case ConstantInfo.CONSTANT_NAME_AND_TYPE:
                    constantInfo = new ConstantNameAndTypeInfo(reader);
                    break;
                case ConstantInfo.CONSTANT_METHOD_HANDLE:
                    constantInfo = new ConstantMethodHandleInfo(reader);
                    break;
                case ConstantInfo.CONSTANT_INVOKE_DYNAMIC:
                    constantInfo = new ConstantInvokeDynamicInfo(reader);
                    break;
                case ConstantInfo.CONSTANT_METHOD_TYPE:
                    constantInfo = new ConstantMethodTypeInfo(reader);
                    break;
                default: {
                    //用户可以自定义解析器
                    System.out.println("Unkown constant pool tag: " + tag);
                    constantInfo = new ConstantUnkownInfo(tag);
                    break;
                }
            }
            return constantInfo;
        }

        public ConstantInfo getConstantInfo(int index)  {
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
            int theindex = ((ConstantClassInfo)getConstantInfo(index)).nameIndex;
            return getUtf8(theindex);
        }

        public String getUtf8(int stringIndex) {
            return((ConstantUtf8Info)getConstantInfo(stringIndex)).value;
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
            public ConstantStringInfo(ClassReader reader) {
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
                String replaceValue = value().replace("\"", "\\\\\"");

                return new StringJoiner(",","{","}")
                        .add("\"constant\":\""+name()+"\"")
                        .add("\"stringIndex\":"+stringIndex)
                        .add("\"value\":\""+replaceValue+"\"")
                        .toString();
            }
        }

        public class ConstantDoubleInfo implements ConstantInfo {
            private long value;
            public ConstantDoubleInfo (ClassReader reader) {
                value = reader.readUint64();
            }
            public long value() {
                return value;
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
                        .add("\"constant\":\""+name()+"\"")
                        .add("\"length\":"+length())
                        .add("\"value\":"+value)
                        .toString();
            }
        }

        public class ConstantIntegerInfo implements ConstantInfo {
            private int value;
            public ConstantIntegerInfo (ClassReader reader) {
                value = reader.readInt32();
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
                        .add("\"constant\":\""+name()+"\"")
                        .add("\"length\":"+length())
                        .add("\"value\":"+value)
                        .toString();
            }

        }

        public class ConstantFloatInfo implements ConstantInfo {
            private int value;
            public ConstantFloatInfo (ClassReader reader) {
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
                        .add("\"constant\":\""+name()+"\"")
                        .add("\"length\":"+length())
                        .add("\"value\":"+value)
                        .toString();
            }
        }

        public class ConstantLongInfo implements ConstantInfo {
            private long value;
            public ConstantLongInfo (ClassReader reader) {
                value = reader.readUint64();
            }
            public long value() {
                return value;
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
                        .add("\"constant\":\""+name()+"\"")
                        .add("\"length\":"+length())
                        .add("\"value\":"+value)
                        .toString();
            }
        }

        public class ConstantUtf8Info implements ConstantInfo {
            private String value;
            private int length;
            public ConstantUtf8Info (ClassReader reader) {
                length = reader.readUint16();
                byte[] bytes = reader.readInt8s(length);
                value = new String(bytes, StandardCharsets.UTF_8);
            }
            public String value() {
                return value;
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
                        .add("\"constant\":\""+name()+"\"")
                        .add("\"length\":"+length())
                        .add("\"value\":\""+value+"\"")
                        .toString();
            }
        }

        public class ConstantClassInfo implements ConstantInfo {
            private int nameIndex;
            public ConstantClassInfo(ClassReader reader) {
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
                        .add("\"constant\":\""+name()+"\"")
                        .add("\"length\":"+length())
                        .add("\"nameIndex\":"+nameIndex)
                        .add("\"name\":\""+getUtf8(nameIndex)+"\"")
                        .toString();
            }
        }

        public class ConstantFieldRefInfo implements ConstantInfo {
            private ConstantMemberRefInfo memberRefInfo;
            public ConstantFieldRefInfo(ConstantMemberRefInfo memberRefInfo) {
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
            public ConstantMethodRefInfo(ConstantMemberRefInfo memberRefInfo) {
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
                        .add("\"constant\":\""+name()+"\"")
                        .add("\"length\":"+length())
                        .add("\"memberRef\":"+ memberRefInfo)
                        .toString();
            }
        }

        public class ConstantInterfaceMethodRefInfo implements ConstantInfo {
            private ConstantMemberRefInfo memberRefInfo;
            public ConstantInterfaceMethodRefInfo(ConstantMemberRefInfo memberRefInfo) {
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
                        .add("\"constant\":\""+name()+"\"")
                        .add("\"length\":"+length())
                        .add("\"memberRef\":"+ memberRefInfo)
                        .toString();
            }
        }

        public class ConstantNameAndTypeInfo implements ConstantInfo {
            private int nameIndex;
            private int descriptorIndex;
            public ConstantNameAndTypeInfo (ClassReader reader) {
                nameIndex = reader.readUint16();
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
                        .add("\"constant\":\""+name()+"\"")
                        .add("\"length\":"+length())
                        .add("\"nameIndex\":"+nameIndex)
                        .add("\"descriptorIndex\":"+descriptorIndex)
                        .add("\"name\":\""+getUtf8(nameIndex)+"\"")
                        .add("\"descriptor\":\""+getUtf8(descriptorIndex)+"\"")
                        .toString();
            }
        }

        public class ConstantMethodTypeInfo implements ConstantInfo {
            private int descriptorIndex;
            public ConstantMethodTypeInfo (ClassReader reader) {
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
                        .add("\"constant\":\""+name()+"\"")
                        .add("\"length\":"+length())
                        .add("\"descriptorIndex\":"+descriptorIndex)
                        .add("\"descriptor\":\""+getUtf8(descriptorIndex)+"\"")
                        .toString();
            }
        }

        public class ConstantMethodHandleInfo implements ConstantInfo {
            private int referenceKind;
            private int referenceIndex;
            public ConstantMethodHandleInfo (ClassReader reader) {
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
            public ConstantInvokeDynamicInfo (ClassReader reader) {
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

            public ConstantUnkownInfo(int tag) {
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
                        .add("\"constant\":\""+name()+"\"")
                        .add("\"length\":"+length())
                        .add("\"tag\":"+tag)
                        .toString();
            }
        }
    }

    public static class Member {
        public static final Attribute.LocalVariable[] EMPTY_LOCAL_VARIABLE = new Attribute.LocalVariable[0];

        private ConstantPool constantPool;
        private int accessFlags;
        private int nameIndex;
        private int descriptorIndex;
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
            return constantPool.getUtf8(nameIndex);
        }

        public String descriptorName() {
            return constantPool.getUtf8(descriptorIndex);
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
                    return EMPTY_LOCAL_VARIABLE;
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

    public class Attribute extends HashMap<String,Object>{

        public Attribute(int attrNameIndex, int length, ClassReader reader) {
            String attrName = constantPool.getUtf8(attrNameIndex);
            put("attrNameIndex",attrNameIndex);
            put("attrName",attrName);
            put("length",length);
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
                    put("code",reader.readInt8s(codeLength));

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
                    }
                    for(int i=0; i<stackMaps.length; i++){
                        int byteCodeOffset = reader.readInt16();
                        int typesOfLocalsSize = reader.readUint16();
                        stackMaps[i] = new StackMapEntry(byteCodeOffset,typesOfLocalsSize);
                        for (StackMapEntry.StackMapType mapType : stackMaps[i].typesOfLocals) {
                            mapType.type = reader.readInt8();
                            if (mapType.type == Opcodes.ITEM_OBJECT || mapType.type == Opcodes.ITEM_UNINITIALIZED) {
                                mapType.index = reader.readInt16();
                            }
                        }
                    }
                    put("map",stackMaps);
                    break;
                }
                default:{
                    byte[] unkownBytes = reader.readInt8s(length);
                    put("unkownBytes",unkownBytes);
//                    System.out.println("unkownBytes");
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
            Iterator<Entry<String,Object>> i = entrySet().iterator();
            while (i.hasNext()) {
                Entry<String,Object> e = i.next();
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
                        .add("\"name\":\"" + name()+"\"")
                        .add("\"signatureName\":\"" + signatureName()+"\"")
                        .add("\"nameIndex\":" + nameIndex)
                        .add("\"signatureIndex\":" + signatureIndex)
                        .add("\"startPc\":" + startPc)
                        .add("\"length\":" + startPc)
                        .add("\"index\":" + index)
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
                return constantPool.getUtf8(innerNameIndex);
            }
            public String innerClassName() {
                return constantPool.getClassName(innerClassIndex);
            }
            public String outerClassName() {
                return constantPool.getClassName(outerClassIndex);
            }
            @Override
            public String toString() {
                return new StringJoiner(",", "{", "}")
                        .add("\"innerAccessFlags\":\"" + Modifier.toString(innerAccessFlags)+"\"")
                        .add("\"innerName\":\"" + innerName()+"\"")
                        .add("\"innerClassName\":\"" + innerClassName()+"\"")
                        .add("\"outerClassName\":\"" + outerClassName()+"\"")
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
                    this.typesOfLocals = new StackMapEntry.StackMapType[typesOfLocalsSize];
                    for (int i = 0; i < typesOfLocals.length; i++) {
                        typesOfLocals[i] = new StackMapType();
                    }
                }
            }

            @Override
            public String toString() {
                return new StringJoiner(",", "{", "}")
                        .add("\"byteCodeOffset\":" + byteCodeOffset)
                        .add("\"typesOfLocals\":\"" + toJsonArray(typesOfLocals))
                        .toString();
            }

            public class StackMapType{
                private byte type;
                private int index = -1;
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
                    return new StringJoiner(",", "{", "}")
                            .add("\"type\":\"" + type)
                            .add("\"typeName\":\"" + getTypeName()+"\"")
                            .add("\"index\":" + index)
                            .toString();
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

        /**
         * 读取8位-无符号
         * @return 8位-无符号
         */
        public int readUint8(){
            return readInt8() & 0x0FF;
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

        @Override
        public void close(){
            this.codes = null;
        }

        @Override
        public String toString() {
            return "file="+length+"b,file="+(length/1024) + "kb";
        }
    }

    public interface Opcodes {

        // Possible values for the type operand of the NEWARRAY instruction.
        // See https://docs.oracle.com/javase/specs/jvms/se9/html/jvms-6.html#jvms-6.5.newarray.

        int T_BOOLEAN = 4;
        int T_CHAR = 5;
        int T_FLOAT = 6;
        int T_DOUBLE = 7;
        int T_BYTE = 8;
        int T_SHORT = 9;
        int T_INT = 10;
        int T_LONG = 11;

        // Possible values for the reference_kind field of CONSTANT_MethodHandle_info structures.
        // See https://docs.oracle.com/javase/specs/jvms/se9/html/jvms-4.html#jvms-4.4.8.

        int H_GETFIELD = 1;
        int H_GETSTATIC = 2;
        int H_PUTFIELD = 3;
        int H_PUTSTATIC = 4;
        int H_INVOKEVIRTUAL = 5;
        int H_INVOKESTATIC = 6;
        int H_INVOKESPECIAL = 7;
        int H_NEWINVOKESPECIAL = 8;
        int H_INVOKEINTERFACE = 9;


        /** An expanded frame.
         *
         * A flag to expand the stack map frames. By default stack map frames are visited in their
         * original format (i.e. "expanded" for classes whose version is less than V1_6, and "compressed"
         * for the other classes). If this flag is set, stack map frames are always visited in expanded
         * format (this option adds a decompression/compression step in ClassReader and ClassWriter which
         * degrades performance quite a lot).
         */
        int F_NEW = -1;

        /** A compressed frame with complete frame data. */
        int F_FULL = 0;

        /**
         * A compressed frame where locals are the same as the locals in the previous frame, except that
         * additional 1-3 locals are defined, and with an empty stack.
         */
        int F_APPEND = 1;

        /**
         * A compressed frame where locals are the same as the locals in the previous frame, except that
         * the last 1-3 locals are absent and with an empty stack.
         */
        int F_CHOP = 2;

        /**
         * A compressed frame with exactly the same locals as the previous frame and with an empty stack.
         */
        int F_SAME = 3;

        /**
         * A compressed frame with exactly the same locals as the previous frame and with a single value
         * on the stack.
         */
        int F_SAME1 = 4;

        // Standard stack map frame element types, .

        byte ITEM_TOP = 0;
        byte ITEM_INTEGER = 1;
        byte ITEM_FLOAT = 2;
        byte ITEM_DOUBLE = 3;
        byte ITEM_LONG = 4;
        byte ITEM_NULL = 5;
        byte ITEM_UNINITIALIZED_THIS = 6;
        byte ITEM_OBJECT = 7;
        byte ITEM_UNINITIALIZED = 8;

        // The JVM opcode values (with the MethodVisitor method name used to visit them in comment, and
        // where '-' means 'same method name as on the previous line').
        // See https://docs.oracle.com/javase/specs/jvms/se9/html/jvms-6.html.

        int NOP = 0; // visitInsn
        int ACONST_NULL = 1; // -
        int ICONST_M1 = 2; // -
        int ICONST_0 = 3; // -
        int ICONST_1 = 4; // -
        int ICONST_2 = 5; // -
        int ICONST_3 = 6; // -
        int ICONST_4 = 7; // -
        int ICONST_5 = 8; // -
        int LCONST_0 = 9; // -
        int LCONST_1 = 10; // -
        int FCONST_0 = 11; // -
        int FCONST_1 = 12; // -
        int FCONST_2 = 13; // -
        int DCONST_0 = 14; // -
        int DCONST_1 = 15; // -
        int BIPUSH = 16; // visitIntInsn
        int SIPUSH = 17; // -
        int LDC = 18; // visitLdcInsn
        int ILOAD = 21; // visitVarInsn
        int LLOAD = 22; // -
        int FLOAD = 23; // -
        int DLOAD = 24; // -
        int ALOAD = 25; // -
        int IALOAD = 46; // visitInsn
        int LALOAD = 47; // -
        int FALOAD = 48; // -
        int DALOAD = 49; // -
        int AALOAD = 50; // -
        int BALOAD = 51; // -
        int CALOAD = 52; // -
        int SALOAD = 53; // -
        int ISTORE = 54; // visitVarInsn
        int LSTORE = 55; // -
        int FSTORE = 56; // -
        int DSTORE = 57; // -
        int ASTORE = 58; // -
        int IASTORE = 79; // visitInsn
        int LASTORE = 80; // -
        int FASTORE = 81; // -
        int DASTORE = 82; // -
        int AASTORE = 83; // -
        int BASTORE = 84; // -
        int CASTORE = 85; // -
        int SASTORE = 86; // -
        int POP = 87; // -
        int POP2 = 88; // -
        int DUP = 89; // -
        int DUP_X1 = 90; // -
        int DUP_X2 = 91; // -
        int DUP2 = 92; // -
        int DUP2_X1 = 93; // -
        int DUP2_X2 = 94; // -
        int SWAP = 95; // -
        int IADD = 96; // -
        int LADD = 97; // -
        int FADD = 98; // -
        int DADD = 99; // -
        int ISUB = 100; // -
        int LSUB = 101; // -
        int FSUB = 102; // -
        int DSUB = 103; // -
        int IMUL = 104; // -
        int LMUL = 105; // -
        int FMUL = 106; // -
        int DMUL = 107; // -
        int IDIV = 108; // -
        int LDIV = 109; // -
        int FDIV = 110; // -
        int DDIV = 111; // -
        int IREM = 112; // -
        int LREM = 113; // -
        int FREM = 114; // -
        int DREM = 115; // -
        int INEG = 116; // -
        int LNEG = 117; // -
        int FNEG = 118; // -
        int DNEG = 119; // -
        int ISHL = 120; // -
        int LSHL = 121; // -
        int ISHR = 122; // -
        int LSHR = 123; // -
        int IUSHR = 124; // -
        int LUSHR = 125; // -
        int IAND = 126; // -
        int LAND = 127; // -
        int IOR = 128; // -
        int LOR = 129; // -
        int IXOR = 130; // -
        int LXOR = 131; // -
        int IINC = 132; // visitIincInsn
        int I2L = 133; // visitInsn
        int I2F = 134; // -
        int I2D = 135; // -
        int L2I = 136; // -
        int L2F = 137; // -
        int L2D = 138; // -
        int F2I = 139; // -
        int F2L = 140; // -
        int F2D = 141; // -
        int D2I = 142; // -
        int D2L = 143; // -
        int D2F = 144; // -
        int I2B = 145; // -
        int I2C = 146; // -
        int I2S = 147; // -
        int LCMP = 148; // -
        int FCMPL = 149; // -
        int FCMPG = 150; // -
        int DCMPL = 151; // -
        int DCMPG = 152; // -
        int IFEQ = 153; // visitJumpInsn
        int IFNE = 154; // -
        int IFLT = 155; // -
        int IFGE = 156; // -
        int IFGT = 157; // -
        int IFLE = 158; // -
        int IF_ICMPEQ = 159; // -
        int IF_ICMPNE = 160; // -
        int IF_ICMPLT = 161; // -
        int IF_ICMPGE = 162; // -
        int IF_ICMPGT = 163; // -
        int IF_ICMPLE = 164; // -
        int IF_ACMPEQ = 165; // -
        int IF_ACMPNE = 166; // -
        int GOTO = 167; // -
        int JSR = 168; // -
        int RET = 169; // visitVarInsn
        int TABLESWITCH = 170; // visiTableSwitchInsn
        int LOOKUPSWITCH = 171; // visitLookupSwitch
        int IRETURN = 172; // visitInsn
        int LRETURN = 173; // -
        int FRETURN = 174; // -
        int DRETURN = 175; // -
        int ARETURN = 176; // -
        int RETURN = 177; // -
        int GETSTATIC = 178; // visitFieldInsn
        int PUTSTATIC = 179; // -
        int GETFIELD = 180; // -
        int PUTFIELD = 181; // -
        int INVOKEVIRTUAL = 182; // visitMethodInsn
        int INVOKESPECIAL = 183; // -
        int INVOKESTATIC = 184; // -
        int INVOKEINTERFACE = 185; // -
        int INVOKEDYNAMIC = 186; // visitInvokeDynamicInsn
        int NEW = 187; // visitTypeInsn
        int NEWARRAY = 188; // visitIntInsn
        int ANEWARRAY = 189; // visitTypeInsn
        int ARRAYLENGTH = 190; // visitInsn
        int ATHROW = 191; // -
        int CHECKCAST = 192; // visitTypeInsn
        int INSTANCEOF = 193; // -
        int MONITORENTER = 194; // visitInsn
        int MONITOREXIT = 195; // -
        int MULTIANEWARRAY = 197; // visitMultiANewArrayInsn
        int IFNULL = 198; // visitJumpInsn
        int IFNONNULL = 199; // -
    }


    public static void main(String[] args) throws Exception{
        //这里换成自己的class包路径
        String path = "G:\\githubs\\spring-boot-protocol\\target\\classes\\com\\github\\netty\\protocol\\servlet";
        Map<String, JavaClassFile> javaClassMap = new HashMap<>();
        for(File file : new File(path).listFiles()){
            String fileName = file.getName();
            if(fileName.endsWith(".class")){
                JavaClassFile javaClassFile = new JavaClassFile(path,fileName);
                javaClassMap.put(fileName, javaClassFile);
            }
        }
        System.out.println("end..");
    }

}
