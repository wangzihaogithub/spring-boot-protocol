package com.github.netty.protocol.mqtt;

import java.util.Objects;

public class MqttClientDescriptor {

    private final String clientID;
    private final String address;
    private final int port;

    MqttClientDescriptor(String clientID, String address, int port) {
        this.clientID = clientID;
        this.address = address;
        this.port = port;
    }

    public String getClientID() {
        return clientID;
    }

    public String getAddress() {
        return address;
    }

    public int getPort() {
        return port;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        MqttClientDescriptor that = (MqttClientDescriptor) o;
        return port == that.port &&
                Objects.equals(clientID, that.clientID) &&
                Objects.equals(address, that.address);
    }

    @Override
    public int hashCode() {
        return Objects.hash(clientID, address, port);
    }

    @Override
    public String toString() {
        return "MqttClientDescriptor{" +
                "clientID='" + clientID + '\'' +
                ", address='" + address + '\'' +
                ", port=" + port +
                '}';
    }
}
