package com.github.netty.protocol.dubbo.serialization;

import java.util.Set;

public interface AllowClassNotifyListener {

    //    SerializeCheckStatus DEFAULT_STATUS = SerializeCheckStatus.STRICT;
    SerializeCheckStatus DEFAULT_STATUS = SerializeCheckStatus.DISABLE;

    void notifyPrefix(Set<String> allowedList, Set<String> disAllowedList);

    void notifyCheckStatus(SerializeCheckStatus status);

    void notifyCheckSerializable(boolean checkSerializable);

}
