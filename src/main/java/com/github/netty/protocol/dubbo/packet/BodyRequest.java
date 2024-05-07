package com.github.netty.protocol.dubbo.packet;

import com.github.netty.protocol.dubbo.Body;

import java.util.Map;

public class BodyRequest extends Body {
    private final String dubboVersion;
    private final String path;
    private final String version;
    private final String methodName;
    private final String parameterTypesDesc;
    private final Object[] parameterValues;
    private final Map<String, Object> attachments;

    public BodyRequest(String dubboVersion, String path, String version,
                       String methodName, String parameterTypesDesc,
                       Map<String, Object> attachments,
                       Object[] parameterValues) {
        this.dubboVersion = dubboVersion;
        this.path = path;
        this.version = version;
        this.methodName = methodName;
        this.parameterTypesDesc = parameterTypesDesc;
        this.attachments = attachments;
        this.parameterValues = parameterValues;
    }

    public String getDubboVersion() {
        return dubboVersion;
    }

    public String getPath() {
        return path;
    }

    public String getVersion() {
        return version;
    }

    public String getMethodName() {
        return methodName;
    }

    public String getParameterTypesDesc() {
        return parameterTypesDesc;
    }

    public Object[] getParameterValues() {
        return parameterValues;
    }

    public Map<String, Object> getAttachments() {
        return attachments;
    }

    @Override
    public String toString() {
        return "BodyRequest{" +
                "\n\tpath='" + path + '\'' +
                ",\n\tmethodName='" + methodName + '\'' +
                ",\n\tattachments=" + attachments +
                "\n}";
    }
}