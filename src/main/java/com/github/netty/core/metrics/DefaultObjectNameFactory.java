package com.github.netty.core.metrics;

import com.github.netty.core.util.LoggerFactoryX;
import com.github.netty.core.util.LoggerX;

import javax.management.MalformedObjectNameException;
import javax.management.ObjectName;

public class DefaultObjectNameFactory implements ObjectNameFactory {

	private static final LoggerX LOGGER = LoggerFactoryX.getLogger(JmxReporter.class);

	@Override
	public ObjectName createName(String type, String domain, String name) {
		try {
			ObjectName objectName = new ObjectName(domain, "name", name);
			if (objectName.isPattern()) {
				objectName = new ObjectName(domain, "name", ObjectName.quote(name));
			}
			return objectName;
		} catch (MalformedObjectNameException e) {
			try {
				return new ObjectName(domain, "name", ObjectName.quote(name));
			} catch (MalformedObjectNameException e1) {
				LOGGER.warn("Unable to supportPipeline {} {}", type, name, e1);
				throw new RuntimeException(e1);
			}
		}
	}

}
