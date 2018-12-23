//package com.github.netty.protocol;
//
//import com.github.netty.core.util.IOUtil;
//
//import java.io.File;
//import java.io.FileNotFoundException;
//import java.io.IOException;
//import java.lang.instrument.IllegalClassFormatException;
//import java.nio.charset.StandardCharsets;
//import java.util.Arrays;
//import java.util.HashMap;
//import java.util.Map;
//import java.util.StringJoiner;
//
//public class MyJavaClassFile {
//
//    public final static short ACC_PUBLIC       = 0x0001;
//    public final static short ACC_PRIVATE      = 0x0002;
//    public final static short ACC_PROTECTED    = 0x0004;
//    public final static short ACC_STATIC       = 0x0008;
//
//    public final static short ACC_FINAL        = 0x0010;
//    public final static short ACC_SYNCHRONIZED = 0x0020;
//    public final static short ACC_VOLATILE     = 0x0040;
//    public final static short ACC_TRANSIENT    = 0x0080;
//
//    public final static short ACC_NATIVE       = 0x0100;
//    public final static short ACC_INTERFACE    = 0x0200;
//    public final static short ACC_ABSTRACT     = 0x0400;
//    public final static short ACC_STRICT       = 0x0800;
//    public final static short ACC_SUPER        = 0x0020;
//    public final static short MAX_ACC_FLAG     = ACC_STRICT;
//    public final static String[] ACCESS_NAMES = {
//            "public", "private", "protected", "static", "final", "synchronized",
//            "volatile", "transient", "native", "interface", "abstract", "strictfp"
//    };
//
//    public static final byte ITEM_Bogus      = 0;
//    public static final byte ITEM_Integer    = 1;
//    public static final byte ITEM_Float      = 2;
//    public static final byte ITEM_Double     = 3;
//    public static final byte ITEM_Long       = 4;
//    public static final byte ITEM_Null       = 5;
//    public static final byte ITEM_InitObject = 6;
//    public static final byte ITEM_Object     = 7;
//    public static final byte ITEM_NewObject  = 8;
//
//    public static final String[] ITEM_NAMES = {
//            "Bogus", "Integer", "Float", "Double", "Long",
//            "Null", "InitObject", "Object", "NewObject"
//    };
//
//    public static String accessToString(int access_flags,boolean for_class){
//        StringBuilder buf = new StringBuilder();
//        int p = 0;
//        for(int i=0; p < MAX_ACC_FLAG; i++) { // Loop through known flags
//            p = 1 << i;
//
//            if((access_flags & p) != 0) {
//        /* Special case: Classes compiled with new compilers and with the
//         * `ACC_SUPER' flag would be said to be "synchronized". This is
//         * because SUN used the same value for the flags `ACC_SUPER' and
//         * `ACC_SYNCHRONIZED'.
//         */
//                if(for_class && ((p == ACC_SUPER) || (p == ACC_INTERFACE)))
//                    continue;
//
//                buf.append(ACCESS_NAMES[i] + " ");
//            }
//        }
//        return buf.toString().trim();
//    }
//    static final int
//            CONSTANT_Utf8               = 1,
//            CONSTANT_Integer            = 3,
//            CONSTANT_Float              = 4,
//            CONSTANT_Long               = 5,
//            CONSTANT_Double             = 6,
//            CONSTANT_Class              = 7,
//            CONSTANT_String             = 8,
//            CONSTANT_Fieldref           = 9,
//            CONSTANT_Methodref          = 10,
//            CONSTANT_InterfaceMethodref = 11,
//            CONSTANT_NameAndType        = 12,
//            CONSTANT_MethodHandle       = 15,
//            CONSTANT_MethodType         = 16,
//            CONSTANT_InvokeDynamic      = 18;
//
//    private int magic;
//    private long minorVersion;
//    private long majorVersion;
//    private int accessFlags;
//    private int thisClass;
//    private int superClass;
//    private int[] interfaces;
//    private ConstantPool constantPool;
//    private MemberInfo[] fields;
//    private MemberInfo[] methods;
//    private AttributeInfo[] attributes;
//
//    public static void main(String[] args) throws IOException, ClassNotFoundException, IllegalClassFormatException {
//        String path = "G:\\githubs\\spring-boot-protocol\\target\\classes\\com\\github\\netty\\protocol\\servlet";
//
//        Map<String,MyJavaClassFile> javaClassMap = new HashMap<>();
//        File rootFile = new File(path);
//        for(File file : rootFile.listFiles()){
//            String name = file.getName();
//            if(!name.endsWith(".class")){
//                continue;
//            }
//            String fullPath = path+"\\"+file.getName();
////            JavaClass javaClass = Repository.lookupClass(fullPath);
////            if(javaClass == null){
////                javaClass = new ClassParser(fullPath).parse();
////            }
//
//            MyJavaClassFile myJavaClassFile = new MyJavaClassFile(path,name);
//            javaClassMap.put(name, myJavaClassFile);
//        }
//
//        System.out.println("end..");
//    }
//
//    public MyJavaClassFile(String path, String name) throws ClassNotFoundException, IOException, IllegalClassFormatException {
//        try {
//            ClassReader reader = new ClassReader(IOUtil.readFileToBytes(path, name));
//            this.magic = reader.readInt32();
//            if (magic != 0xCAFEBABE){
//                throw new IllegalClassFormatException(path +"\\"+name +" is not a Java .class file");
//            }
//            this.minorVersion = reader.readUint16();
//            this.majorVersion = reader.readUint16();
//            this.constantPool = readConstantPool(reader);
//            this.accessFlags = readAccessFlags(reader);
//            this.thisClass = reader.readUint16();
//            this.superClass = reader.readUint16();
//            this.interfaces = reader.readUint16s();
//            this.fields = readMembers(reader);
//            this.methods = readMembers(reader);
//            this.attributes = readAttributes(reader, constantPool);
//        }catch (FileNotFoundException e){
//            throw new ClassNotFoundException(path+name,e);
//        }
//    }
//
//    int readAccessFlags(ClassReader reader) {
//        int accessFlags = reader.readUint16();
//        if((accessFlags & ACC_INTERFACE) != 0)
//            accessFlags |= ACC_ABSTRACT;
//        return accessFlags;
//    }
//
//    ConstantPool readConstantPool(ClassReader reader) {
//        int constant_pool_count = reader.readUint16();
//        return new ConstantPool(constant_pool_count,reader);
//    }
//
//    MemberInfo[] readMembers(ClassReader reader)  {
//        int memberCount = reader.readUint16();
//        MemberInfo[] members = new MemberInfo[memberCount];
//        for (int i =0; i<members.length; i++) {
//            members[i] = new MemberInfo();
//            members[i].constantPool = constantPool;
//            members[i].accessFlags = reader.readUint16();
//            members[i].nameIndex = reader.readUint16();
//            members[i].descriptorIndex = reader.readUint16();
//            members[i].attributes = readAttributes(reader,constantPool);
//        }
//        return members;
//    }
//
//    public static AttributeInfo[] readAttributes(ClassReader reader,ConstantPool constantPool)  {
//        int attributesCount = reader.readUint16();
//        AttributeInfo[] attributes = new AttributeInfo[attributesCount];
//        for(int i = 0; i<attributes.length;i++) {
//            int attrNameIndex = reader.readUint16();
//            attributes[i] =  new AttributeInfo(attrNameIndex, constantPool,reader.readInt32(),reader);
//        }
//        return attributes;
//    }
//
//    public AttributeInfo[] getAttributes() {
//        return attributes;
//    }
//    public ConstantPool getConstantPool() {
//        return constantPool;
//    }
//    public int getAccessFlags() {
//        return accessFlags;
//    }
//    public MemberInfo[] getFields() {
//        return fields;
//    }
//    public MemberInfo[] getMethods() {
//        return methods;
//    }
//    public String getClassName() {
//        return constantPool.getClassName(thisClass);
//    }
//    public String getSuperClassName() {
//        if (superClass != 0 ){
//            return constantPool.getClassName(superClass);
//        }
//        return "";
//    }
//    public String[] getInterfaceNames() {
//        String[] interfaceNames = new String[interfaces.length];
//        for (int i=0; i<interfaceNames.length; i++){
//            interfaceNames[i] = constantPool.getClassName(interfaces[i]);
//        }
//        return interfaceNames;
//    }
//
//    public final boolean isPublic() {
//        return (accessFlags & ACC_PUBLIC) != 0;
//    }
//    public final boolean isPrivate() {
//        return (accessFlags & ACC_PRIVATE) != 0;
//    }
//    public final boolean isProtected() {
//        return (accessFlags & ACC_PROTECTED) != 0;
//    }
//    public final boolean isStatic() {
//        return (accessFlags & ACC_STATIC) != 0;
//    }
//    public final boolean isFinal() {
//        return (accessFlags & ACC_FINAL) != 0;
//    }
//    public final boolean isSynchronized() {
//        return (accessFlags & ACC_SYNCHRONIZED) != 0;
//    }
//    public final boolean isVolatile() {
//        return (accessFlags & ACC_VOLATILE) != 0;
//    }
//    public final boolean isTransient() {
//        return (accessFlags & ACC_TRANSIENT) != 0;
//    }
//    public final boolean isNative() {
//        return (accessFlags & ACC_NATIVE) != 0;
//    }
//    public final boolean isInterface() {
//        return (accessFlags & ACC_INTERFACE) != 0;
//    }
//    public final boolean isAbstract() {
//        return (accessFlags & ACC_ABSTRACT) != 0;
//    }
//    public final boolean isStrictfp() {
//        return (accessFlags & ACC_STRICT) != 0;
//    }
//
//    @Override
//    public String toString() {
//        StringJoiner joiner = new StringJoiner("\r\n");
//        joiner.add(getClassName());
//        joiner.add("interfaces="+Arrays.toString(getInterfaceNames()));
//        joiner.add("superClassName="+ getSuperClassName());
//        joiner.add("methods="+Arrays.toString(getMethods()));
//        joiner.add("fields="+Arrays.toString(getFields()));
//        return joiner.toString();
//    }
//
//    public static class AttributeInfo extends HashMap{
//        private int attrNameIndex;
//        private String attrName;
//        private ConstantPool cp;
//        private int length;
//
//        public AttributeInfo(int attrNameIndex, ConstantPool cp, int length,ClassReader reader) {
//            this.attrNameIndex = attrNameIndex;
//            this.attrName = cp.getUtf8(attrNameIndex);
//            this.cp = cp;
//            this.length = length;
//            put("attrNameIndex",attrNameIndex);
//            put("attrName",attrName);
//            put("length",length);
//            switch (attrName){
//                case "ConstantValue" :{
//                    int constantvalue_index = reader.readUint16();
//                    put("constantvalue_index",constantvalue_index);
//                    break;
//                }
//                case "SourceFile" :{
//                    int sourcefile_index = reader.readUint16();
//                    put("sourcefile_index",sourcefile_index);
//                    break;
//                }
//                case "Code" :{
//                    put("max_stack",reader.readUint16());
//                    put("max_locals",reader.readUint16());
//                    int code_length = reader.readInt32();
//                    put("code",reader.readBytes(code_length));
//                    CodeException[] codeExceptions = new CodeException[reader.readUint16()];
//                    for(int i=0; i<codeExceptions.length; i++){
//                        codeExceptions[i] = new CodeException(reader.readUint16(),reader.readUint16(),reader.readUint16(),reader.readUint16());
//                    }
//                    put("exception_table",codeExceptions);
//                    put("attributes", MyJavaClassFile.readAttributes(reader,cp));
//                    break;
//                }
//                case "Exceptions" :{
//                    int[] exception_index_table = new int[reader.readUint16()];
//                    put("exception_index_table",exception_index_table);
//                    for(int i=0; i < exception_index_table.length; i++) {
//                        exception_index_table[i] = reader.readUint16();
//                    }
//                    break;
//                }
//                case "LineNumberTable" :{
//                    LineNumber[] line_number_table = new LineNumber[reader.readUint16()];
//                    put("line_number_table",line_number_table);
//                    for(int i=0; i < line_number_table.length; i++) {
//                        line_number_table[i] = new LineNumber(reader.readUint16(),reader.readUint16());
//                    }
//                    break;
//                }
//                case "LocalVariableTable" :{
//                    LocalVariable[] local_variable_table = new LocalVariable[reader.readUint16()];
//                    put("local_variable_table",local_variable_table);
//                    for(int i=0; i < local_variable_table.length; i++) {
//                        local_variable_table[i] = new LocalVariable(
//                                reader.readUint16(),reader.readUint16(),
//                                reader.readUint16(),reader.readUint16(),reader.readUint16(),cp);
//                    }
//                    break;
//                }
//                case "LocalVariableTypeTable" :{
//                    LocalVariable[] local_variable_table = new LocalVariable[reader.readUint16()];
//                    put("local_variable_table",local_variable_table);
//                    for(int i=0; i < local_variable_table.length; i++) {
//                        local_variable_table[i] = new LocalVariable(
//                                reader.readUint16(),reader.readUint16(),
//                                reader.readUint16(),reader.readUint16(),reader.readUint16(),cp);
//                    }
//                    break;
//                }
//                case "InnerClasses" :{
//                    InnerClass[] number_of_classes = new InnerClass[reader.readUint16()];
//                    put("number_of_classes",number_of_classes);
//                    for(int i=0; i < number_of_classes.length; i++) {
//                        number_of_classes[i] = new InnerClass(
//                                reader.readUint16(),reader.readUint16(),
//                                reader.readUint16(),reader.readUint16());
//                    }
//                    break;
//                }
//                case "Synthetic" :{
//                    if(length>0) {
//                        reader.readBytes(length);
//                        System.err.println("Synthetic attribute with length > 0");
//                    }
//                    break;
//                }
//                case "Deprecated" :{
//                    if(length>0) {
//                        reader.readBytes(length);
//                        System.err.println("Deprecated attribute with length > 0");
//                    }
//                    break;
//                }
//                case "PMGClass" :{
//                    put("pmg_class_index",reader.readUint16());
//                    put("pmg_index",reader.readUint16());
//                    break;
//                }
//                case "Signature" :{
//                    put("signature_index",reader.readUint16());
//                    break;
//                }
//                case "StackMap" :{
//                    StackMapEntry[] map = new StackMapEntry[reader.readUint16()];
//                    for(int i=0; i<map.length; i++){
//                        StackMapEntry entry = new StackMapEntry(reader.readInt16());
//                        entry.types_of_locals = new StackMapEntry.StackMapType[reader.readUint16()];
//                        for(int j=0; j<map[i].types_of_locals.length; j++){
//                            StackMapEntry.StackMapType mapType = new StackMapEntry.StackMapType();
//                            mapType.cp = cp;
//                            mapType.type = reader.readInt8();
//                            if(mapType.type == ITEM_Object || mapType.type == ITEM_NewObject){
//                                mapType.index = reader.readInt16();
//                            }
//                            entry.types_of_locals[j] = mapType;
//                        }
//                        map[i] = entry;
//                    }
//                    put("map",map);
//                    break;
//                }
//                default:{
//                    reader.readBytes(length);
//                    System.out.println("default attribute with");
//                    break;
//                }
//            }
//        }
//
//        public int getLength() {
//            return length;
//        }
//
//        public String getAttrName() {
//            return attrName;
//        }
//
//        @Override
//        public String toString() {
//            return "name="+attrName+",length="+length;
//        }
//
//
//        static class CodeException{
//            int start_pc; int end_pc;int handler_pc; int catch_type;
//
//            public CodeException(int start_pc, int end_pc, int handler_pc, int catch_type) {
//                this.start_pc = start_pc;
//                this.end_pc = end_pc;
//                this.handler_pc = handler_pc;
//                this.catch_type = catch_type;
//            }
//        }
//
//        static class LineNumber{
//            int start_pc;    // Program Counter (PC) corresponds to line
//            int line_number; // number in source file
//
//            public LineNumber(int start_pc, int line_number) {
//                this.start_pc = start_pc;
//                this.line_number = line_number;
//            }
//        }
//
//        static class LocalVariable{
//            int start_pc;        // Range in which the variable is valid
//            int length;
//            int name_index;      // Index in constant pool of variable name
//            int signature_index; // Index of variable signature
//            int index;            /* Variable is `index'th local variable on
//                                * this method's frame.
//                                */
//            private ConstantPool cp;
//
//            public LocalVariable(int start_pc, int length, int name_index, int signature_index, int index,ConstantPool cp) {
//                this.start_pc = start_pc;
//                this.length = length;
//                this.name_index = name_index;
//                this.signature_index = signature_index;
//                this.index = index;
//                this.cp = cp;
//            }
//        }
//
//        static class InnerClass{
//            int inner_class_index;
//            int outer_class_index;
//            int inner_name_index;
//            int inner_access_flags;
//
//            public InnerClass(int inner_class_index, int outer_class_index, int inner_name_index, int inner_access_flags) {
//                this.inner_class_index = inner_class_index;
//                this.outer_class_index = outer_class_index;
//                this.inner_name_index = inner_name_index;
//                this.inner_access_flags = inner_access_flags;
//            }
//        }
//
//        public static class StackMapEntry{
//            public int            byte_code_offset;
//            public StackMapType[] types_of_locals;
//
//            public StackMapEntry(int byte_code_offset) {
//                this.byte_code_offset = byte_code_offset;
//            }
//
//            public static class StackMapType{
//                byte type;
//                int index = -1;
//                ConstantPool cp;
//            }
//        }
//
//    }
//
//    public static class ConstantPool {
//        ConstantInfo[] constants;
//
//        public ConstantPool(int constant_pool_count,ClassReader reader) {
//            constants = new ConstantInfo[constant_pool_count];
//            // The constant_pool table is indexed from 1 to constant_pool_count - 1.
//            for (int i = 1; i < constant_pool_count; i++) {
//                ConstantInfo constantInfo = readConstantInfo(reader);
//                constants[i] = constantInfo;
//                // http://docs.oracle.com/javase/specs/jvms/se8/html/jvms-4.html#jvms-4.4.5
//                // All 8-byte constants take up two entries in the constant_pool table of the class file.
//                // If a CONSTANT_Long_info or CONSTANT_Double_info structure is the item in the constant_pool
//                // table at index n, then the next usable item in the pool is located at index n+2.
//                // The constant_pool index n+1 must be valid but is considered unusable.
//                if (constantInfo instanceof ConstantDoubleInfo
//                        ||constantInfo instanceof ConstantLongInfo) {
//                    i++;
//                }
//            }
//        }
//
//        ConstantInfo readConstantInfo(ClassReader reader){
//            ConstantPool cp = this;
//            int tag = reader.readUint8();
//            ConstantInfo c;
//            switch (tag) {
//                case CONSTANT_Integer:
//                    c = new ConstantIntegerInfo(reader);
//                    break;
//                case CONSTANT_Float:
//                    c = new ConstantFloatInfo(reader);
//                    break;
//                case CONSTANT_Long:
//                    c = new ConstantLongInfo(reader);
//                    break;
//                case CONSTANT_Double:
//                    c = new ConstantDoubleInfo(reader);
//                    break;
//                case CONSTANT_Utf8:
//                    c = new ConstantUtf8Info(reader);
//                    break;
//                case CONSTANT_String:
//                    c = new ConstantStringInfo(reader,cp);
//                    break;
//                case CONSTANT_Class:
//                    c = new ConstantClassInfo(reader);
//                    break;
//                case CONSTANT_Fieldref:
//                    c = new ConstantFieldrefInfo(new ConstantMemberrefInfo(reader,cp));
//                    break;
//                case CONSTANT_Methodref:
//                    c = new ConstantMethodrefInfo(new ConstantMemberrefInfo(reader,cp));
//                    break;
//                case CONSTANT_InterfaceMethodref:
//                    c = new ConstantInterfaceMethodrefInfo(new ConstantMemberrefInfo(reader,cp));
//                    break;
//                case CONSTANT_NameAndType:
//                    c = new ConstantNameAndTypeInfo(reader);
//                    break;
//                case CONSTANT_MethodHandle:
//                    c = new ConstantMethodHandleInfo(reader);
//                    break;
//                case CONSTANT_InvokeDynamic:
//                    c = new ConstantInvokeDynamicInfo(reader,cp);
//                    break;
//                case CONSTANT_MethodType:
//                    c = new ConstantMethodTypeInfo(reader);
//                    break;
//                default: {
//                    //用户可以自定义解析器
//                    System.out.println("BAD constant pool tag: " + tag);
//                    c = null;
//                }
//            }
//            return c;
//        }
//
//        public ConstantInfo getConstantInfo(int index)  {
//            ConstantInfo cpInfo = constants[index];
//            if(cpInfo == null){
//                System.out.println("Bad constant pool index: "+ index);
//            }
//            return cpInfo;
//        }
//
//        public Map<String,String> getNameAndType(int index) {
//            int nameIndex = ((ConstantNameAndTypeInfo)getConstantInfo(index)).nameIndex;
//            int descriptorIndex = ((ConstantNameAndTypeInfo)getConstantInfo(index)).descriptorIndex;
//            Map<String,String> map = new HashMap<>();
//            map.put("type",getUtf8(nameIndex));
//            map.put("name",getUtf8(descriptorIndex));
//            return map;
//        }
//
//        public String getClassName(int index) {
//            int theindex = ((ConstantClassInfo)getConstantInfo(index)).nameIndex;
//            return getUtf8(theindex);
//        }
//
//        public String getUtf8(int stringIndex) {
//            return((ConstantUtf8Info)getConstantInfo(stringIndex)).value;
//        }
//
//        public interface ConstantInfo {
//        }
//
//        class ConstantStringInfo implements ConstantInfo {
//            ConstantPool cp;
//            int stringIndex;
//            public ConstantStringInfo(ClassReader reader,ConstantPool cp) {
//                this.cp = cp;
//                this.stringIndex = reader.readUint16();
//            }
//            public String value() {
//                return cp.getUtf8(stringIndex);
//            }
//
//            @Override
//            public String toString() {
//                return value();
//            }
//        }
//
//        class ConstantDoubleInfo implements ConstantInfo {
//            long value;
//            public ConstantDoubleInfo (ClassReader reader) {
//                value = reader.readUint64();
//            }
//            public long value() {
//                return value;
//            }
//
//            @Override
//            public String toString() {
//                return value()+"";
//            }
//        }
//
//        class ConstantIntegerInfo implements ConstantInfo {
//            int value;
//            public ConstantIntegerInfo (ClassReader reader) {
//                value = reader.readInt32();
//            }
//
//            public int value() {
//                return value;
//            }
//
//            @Override
//            public String toString() {
//                return value()+"";
//            }
//        }
//
//        class ConstantFloatInfo implements ConstantInfo {
//            int value;
//            public ConstantFloatInfo (ClassReader reader) {
//                value = reader.readInt32();
//            }
//            public int value() {
//                return value;
//            }
//            @Override
//            public String toString() {
//                return value()+"";
//            }
//        }
//
//        class ConstantLongInfo implements ConstantInfo {
//            long value;
//            public ConstantLongInfo (ClassReader reader) {
//                value = reader.readUint64();
//            }
//            public long value() {
//                return value;
//            }
//            @Override
//            public String toString() {
//                return value()+"";
//            }
//        }
//
//        class ConstantUtf8Info implements ConstantInfo {
//            String value;
//            public ConstantUtf8Info (ClassReader reader) {
//                int length = reader.readUint16();
//                byte[] bytes = reader.readBytes(length);
//                value = new String(bytes, StandardCharsets.UTF_8);
//            }
//            public String value() {
//                return value;
//            }
//            @Override
//            public String toString() {
//                return value();
//            }
//        }
//
//        class ConstantClassInfo implements ConstantInfo {
//            int nameIndex;
//            public ConstantClassInfo(ClassReader reader) {
//                this.nameIndex = reader.readUint16();;
//            }
//            public String value() {
//                return getUtf8(nameIndex);
//            }
//            @Override
//            public String toString() {
//                return value();
//            }
//        }
//
//        class ConstantFieldrefInfo implements ConstantInfo {
//            ConstantMemberrefInfo memberrefInfo;
//            public ConstantFieldrefInfo(ConstantMemberrefInfo memberrefInfo) {
//                this.memberrefInfo = memberrefInfo;
//            }
//            public ConstantMemberrefInfo value() {
//                return memberrefInfo;
//            }
//            @Override
//            public String toString() {
//                return memberrefInfo.toString();
//            }
//        }
//
//        class ConstantMemberrefInfo implements ConstantInfo {
//            ConstantPool cp;
//            int classIndex;
//            int nameAndTypeIndex;
//
//            public ConstantMemberrefInfo (ClassReader reader,ConstantPool cp) {
//                this.cp = cp;
//                classIndex = reader.readUint16();
//                nameAndTypeIndex = reader.readUint16();
//            }
//
//            String className() {
//                return cp.getClassName(classIndex);
//            }
//
//            Map<String, String> nameAndDescriptor() {
//                return cp.getNameAndType(nameAndTypeIndex);
//            }
//            @Override
//            public String toString() {
//                return className()+nameAndDescriptor().toString();
//            }
//        }
//
//        class ConstantMethodrefInfo implements ConstantInfo {
//            ConstantMemberrefInfo memberrefInfo;
//            public ConstantMethodrefInfo(ConstantMemberrefInfo memberrefInfo) {
//                this.memberrefInfo = memberrefInfo;
//            }
//            @Override
//            public String toString() {
//                return memberrefInfo.toString();
//            }
//        }
//
//        class ConstantInterfaceMethodrefInfo implements ConstantInfo {
//            ConstantMemberrefInfo memberrefInfo;
//            public ConstantInterfaceMethodrefInfo(ConstantMemberrefInfo memberrefInfo) {
//                this.memberrefInfo = memberrefInfo;
//            }
//            @Override
//            public String toString() {
//                return memberrefInfo.toString();
//            }
//        }
//
//        class ConstantNameAndTypeInfo implements ConstantInfo {
//            int nameIndex;
//            int descriptorIndex;
//            public ConstantNameAndTypeInfo (ClassReader reader) {
//                nameIndex = reader.readUint16();
//                descriptorIndex = reader.readUint16();
//            }
//            @Override
//            public String toString() {
//                return getUtf8(nameIndex);
//            }
//        }
//
//        class ConstantMethodTypeInfo implements ConstantInfo {
//            int descriptorIndex;
//            public ConstantMethodTypeInfo (ClassReader reader) {
//                descriptorIndex = reader.readUint16();
//            }
//            @Override
//            public String toString() {
//                return descriptorIndex+"";
//            }
//        }
//
//        class ConstantMethodHandleInfo implements ConstantInfo {
//            int referenceKind;
//            int referenceIndex;
//            public ConstantMethodHandleInfo (ClassReader reader) {
//                referenceKind = reader.readUint8();
//                referenceIndex = reader.readUint16();
//            }
//
//            @Override
//            public String toString() {
//                return "ConstantMethodHandleInfo : {referenceKind="+referenceKind+",referenceIndex="+referenceIndex+"}";
//            }
//        }
//
//        class ConstantInvokeDynamicInfo implements ConstantInfo {
//            ConstantPool cp;
//            int bootstrapMethodAttrIndex;
//            int nameAndTypeIndex;
//            public ConstantInvokeDynamicInfo (ClassReader reader,ConstantPool cp) {
//                this.cp = cp;
//                bootstrapMethodAttrIndex = reader.readUint16();
//                nameAndTypeIndex = reader.readUint16();
//            }
//
//            public Map<String, String> nameAndType() {
//                return cp.getNameAndType(nameAndTypeIndex);
//            }
//            @Override
//            public String toString() {
//                return nameAndType().toString();
//            }
//        }
//    }
//
//    public static class MemberInfo  {
//        ConstantPool constantPool;
//        int accessFlags;
//        int nameIndex;
//        int descriptorIndex;
//        AttributeInfo[] attributes;
//
//        public String name() {
//            return constantPool.getUtf8(nameIndex);
//        }
//        public String descriptor() {
//            return constantPool.getUtf8(descriptorIndex);
//        }
//
//        @Override
//        public String toString() {
//            StringJoiner joiner = new StringJoiner("\r\n");
//            for(AttributeInfo attribute : attributes){
//                joiner.add("\t"+attribute.toString());
//            }
//            return accessToString(accessFlags,false) + " "+descriptor()+name()+joiner.toString();
//        }
//    }
//
//    public static class ClassReader{
//        byte[] codes;
//        int index;
//
//        public ClassReader(byte[] codes) {
//            this.codes = codes;
//        }
//
//        int readUint8(){
//            int value = IOUtil.getUnsignedByte(codes,index);
//            index = index + 1;
//            return value;
//        }
//
//        byte readInt8(){
//            byte value = IOUtil.getByte(codes,index);
//            index = index + 1;
//            return value;
//        }
//
//        int readUint16(){
//            int value = IOUtil.getUnsignedShort(codes,index);
//            index = index + 2;
//            return value;
//        }
//
//        int readInt16(){
//            int value = IOUtil.getShort(codes,index);
//            index = index + 2;
//            return value;
//        }
//
//        int readInt32(){
//            int value = IOUtil.getInt(codes,index);
//            index = index + 4;
//            return value;
//        }
//
//        long readUint64(){
//            long value = IOUtil.getLong(codes,index);
//            index = index + 8;
//            return value;
//        }
//
//        int[] readUint16s(){
//            int length = readUint16();
//            int[] values = new int[length];
//            for(int i=0; i<length; i++){
//                values[i] = readUint16();
//            }
//            return values;
//        }
//
//        byte[] readBytes(int length){
//            byte[] values = new byte[length];
//            for(int i=0; i<length; i++){
//                values[i] = IOUtil.getByte(codes,index);
//                index = index + 1;
//            }
//            return values;
//        }
//
//        @Override
//        public String toString() {
//            return "file="+codes.length+"b,file="+(codes.length/1024) + "kb";
//        }
//    }
//
//}
