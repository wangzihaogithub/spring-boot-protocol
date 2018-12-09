package com.github.netty.core.metrics;

import javax.management.ObjectName;

public interface ObjectNameFactory {

	ObjectName createName(String type, String domain, String name);
}
