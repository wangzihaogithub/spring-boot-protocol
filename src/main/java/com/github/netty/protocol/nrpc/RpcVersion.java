package com.github.netty.protocol.nrpc;

/**
 * version
 * @author wangzihao
 */
public enum RpcVersion {
    V2_0_0(0),
    V2_0_1(1);

    private byte index;

    RpcVersion(int index) {
        this.index = (byte) index;
    }

    public byte index() {
        return index;
    }

    public RpcVersion indexOf(byte index) {
        for(RpcVersion rpcVersion : values()){
            if(rpcVersion.index == index){
                return rpcVersion;
            }
        }
        return null;
    }

}
