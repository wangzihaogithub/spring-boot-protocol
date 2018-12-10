package com.github.netty.protocol.mqtt.exception;

public class MqttException extends RuntimeException {
    private static final long serialVersionUID = 5848069213104389412L;

    public MqttException() {
        super();
    }

    public MqttException(String message) {
        super(message);
    }

    public MqttException(String message, Throwable cause) {
        super(message, cause);
    }

    public MqttException(Throwable cause) {
        super(cause);
    }

    public MqttException(String message, Throwable cause, boolean enableSuppression, boolean writableStackTrace) {
        super(message, cause, enableSuppression, writableStackTrace);
    }
}
