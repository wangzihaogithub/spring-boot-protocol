package com.github.netty.protocol.rtsp;

import com.github.netty.core.util.IOUtil;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public class TestJvm {


    
    public static void main(String[] args) throws IOException {
        byte[] bytes = IOUtil.readFileToBytes("D:\\wangzihao\\githubs\\spring-boot-protocol\\target\\classes\\com\\github\\netty\\core","AbstractNettyServer.class");
        ClassReader reader = new ClassReader(bytes);

        ClassFile self = new ClassFile(reader);
        self.readAndCheckMagic(reader)
        self.readVersions(reader)
        self.readConstantPool(reader)
        self.accessFlags = reader.readUint16()
        self.thisClass = reader.readUint16()
        self.superClass = reader.readUint16()
        self.interfaces = reader.readUint16s()
        self.fields = readMembers(reader, self.constantPool)
        self.methods = readMembers(reader, self.constantPool)
        self.attributes = readAttributes(reader, self.constantPool)
    }

    static class ClassFile  {
        //magic           uint32
        long minorVersion;
        long majorVersion;
        Map constantPool;
        int accessFlags;
        int thisClass;
        int superClass;
        int[] interfaces;
        MemberInfo[] fields;
        MemberInfo[] methods;
        Map attributeTable;
        ClassReader reader;

        public ClassFile(ClassReader reader) {
            this.reader = reader;
        }

        void readAndCheckMagic() {
            int magic = reader.readUint32();
//             readUint32()
            if (magic != 0xCAFEBABE){
                System.out.println("Bad magic!");
            }
        }

        void readVersions() {
            minorVersion = reader.readUint16();
            majorVersion = reader.readUint16();
            // todo check versions
        }

        func (self *ClassFile) readConstantPool(reader *ClassReader) {
            self.constantPool = &ConstantPool{cf: self}
            self.constantPool.read(reader)
        }

        func (self *ClassFile) ConstantPool() *ConstantPool {
            return self.constantPool
        }
        func (self *ClassFile) AccessFlags() uint16 {
            return self.accessFlags
        }

        func (self *ClassFile) Fields() []*MemberInfo {
            return self.fields
        }
        func (self *ClassFile) Methods() []*MemberInfo {
            return self.methods
        }

        func (self *ClassFile) ClassName() string {
            return self.constantPool.getClassName(self.thisClass)
        }

        func (self *ClassFile) SuperClassName() string {
            if self.superClass != 0 {
                return self.constantPool.getClassName(self.superClass)
            }
            return ""
        }

        func (self *ClassFile) InterfaceNames() []string {
            interfaceNames := make([]string, len(self.interfaces))
            for i, cpIndex := range self.interfaces {
                interfaceNames[i] = self.constantPool.getClassName(cpIndex)
            }
            return interfaceNames
        }
    }

    static class ConstantPool{
        ClassFile cf;
        ConstantInfo[] cpInfos;

        static final int
                CONSTANT_Class              = 7,
                CONSTANT_Fieldref           = 9,
                CONSTANT_Methodref          = 10,
                CONSTANT_InterfaceMethodref = 11,
                CONSTANT_String             = 8,
                CONSTANT_Integer            = 3,
                CONSTANT_Float              = 4,
                CONSTANT_Long               = 5,
                CONSTANT_Double             = 6,
                CONSTANT_NameAndType        = 12,
                CONSTANT_Utf8               = 1,
                CONSTANT_MethodHandle       = 15,
                CONSTANT_MethodType         = 16,
                CONSTANT_InvokeDynamic      = 18;

        void read(ClassReader reader){
            int cpCount = reader.readUint16();
            cpInfos = new ConstantInfo[cpCount];

            // The constant_pool table is indexed from 1 to constant_pool_count - 1.
            for (int i = 0; i < cpCount; i++) {
                cpInfos[i] = readConstantInfo(reader, this);
                // http://docs.oracle.com/javase/specs/jvms/se8/html/jvms-4.html#jvms-4.4.5
                // All 8-byte constants take up two entries in the constant_pool table of the class file.
                // If a CONSTANT_Long_info or CONSTANT_Double_info structure is the item in the constant_pool
                // table at index n, then the next usable item in the pool is located at index n+2.
                // The constant_pool index n+1 must be valid but is considered unusable.
                if (cpInfos[i] instanceof ConstantInfo.ConstantDoubleInfo) {
                    i++;
                }
            }
        }


        ConstantInfo readConstantInfo(ClassReader reader,ConstantPool cp){
            int tag = reader.readUint8();
            ConstantInfo c = newConstantInfo(tag, cp);
            c.readInfo(reader);
            return c;
        }

        // todo ugly code
        ConstantInfo newConstantInfo( int tag, ConstantPool cp)  {
            switch (tag) {
                case CONSTANT_Integer:
                    return new ConstantInfo.ConstantIntegerInfo();
                case CONSTANT_Float:
                    return new ConstantInfo.ConstantFloatInfo();
                case CONSTANT_Long:
                    return new ConstantInfo.ConstantLongInfo();
                case CONSTANT_Double:
                    return new ConstantInfo.ConstantDoubleInfo();
                case CONSTANT_Utf8:
                    return new ConstantInfo.ConstantUtf8Info();
                case CONSTANT_String:
                    return new ConstantInfo.ConstantStringInfo();
                case CONSTANT_Class:
                    return new ConstantInfo.ConstantClassInfo();
                case CONSTANT_Fieldref:
                    return new ConstantInfo.ConstantFieldrefInfo();
                case CONSTANT_Methodref:
                    return new ConstantInfo.ConstantMethodrefInfo();
                case CONSTANT_InterfaceMethodref:
                    return new ConstantInfo.ConstantInterfaceMethodrefInfo();
                case CONSTANT_NameAndType:
                    return new ConstantInfo.ConstantNameAndTypeInfo();
                case CONSTANT_MethodType:
                    return new ConstantInfo.ConstantMethodTypeInfo();
                case CONSTANT_MethodHandle:
                    return new ConstantInfo.ConstantMethodHandleInfo();
                case CONSTANT_InvokeDynamic:
                    return new ConstantInfo.ConstantInvokeDynamicInfo();
                default: // todo
                    System.out.println("BAD constant pool tag: "+tag );
            }
            return null;
        }


        ConstantInfo[] getConstantInfo()  {
            return cpInfos;
        }

        ConstantInfo getConstantInfo(int index)  {
            ConstantInfo cpInfo = cpInfos[index];
            if(cpInfo == null){
                System.out.println("Bad constant pool index: "+ index);
            }
            return cpInfo;
        }

        public Map getNameAndType(int index) {
            int nameIndex = ((ConstantInfo.ConstantNameAndTypeInfo)getConstantInfo(index)).nameIndex;
            int descriptorIndex = ((ConstantInfo.ConstantNameAndTypeInfo)getConstantInfo(index)).descriptorIndex;
            Map map = new HashMap();
            map.put("type",getUtf8(nameIndex));
            map.put("name",getUtf8(descriptorIndex));
            return map;
        }

        public String getClassName(int index) {
            int theindex = ((ConstantInfo.ConstantClassInfo)getConstantInfo(index)).nameIndex;
            return getUtf8(theindex);
        }

        public String getUtf8(int stringIndex) {
            return((ConstantInfo.ConstantUtf8Info)getConstantInfo(stringIndex)).str;
        }

    }

    static class MemberInfo  {
        Map constantPool;
        int accessFlags;
        int nameIndex;
        int descriptorIndex;
        Map attributeTable;
    }

    static class ClassReader{
         byte[] codes;

        public ClassReader(byte[] codes) {
            this.codes = codes;
        }

        int index;

        byte readUint8(){
            byte value = IOUtil.getByte(codes,index);
            index = index + 1;
            return value;
        }

        short readUint16(){
            short value = IOUtil.getShort(codes,index);
            index = index + 2;
            return value;
        }

        int readUint32(){
            int value = IOUtil.getInt(codes,index);
            index = index + 4;
            return value;
        }

        long readUint64(){
            long value = IOUtil.getLong(codes,index);
            index = index + 8;
            return value;
        }

        short[] readUint16s(){
            short length = readUint16();
            short[] values = new short[length];
            for(int i=0; i<length; i++){
                values[i] = readUint16();
            }
            return values;
        }

        byte[] readBytes(int length){
            byte[] values = new byte[length];
            for(int i=0; i<length; i++){
                values[i] = readUint8();
            }
            return values;
        }
    }
}
