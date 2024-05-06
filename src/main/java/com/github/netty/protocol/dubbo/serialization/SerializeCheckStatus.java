package com.github.netty.protocol.dubbo.serialization;

public enum SerializeCheckStatus {
    /**
     * Disable serialize check for all classes
     */
    DISABLE(0),

    /**
     * Only deny danger classes, warn if other classes are not in allow list
     */
    WARN(1),

    /**
     * Only allow classes in allow list, deny if other classes are not in allow list
     */
    STRICT(2);

    private final int level;

    SerializeCheckStatus(int level) {
        this.level = level;
    }

    public int level() {
        return level;
    }
}
