package com.github.netty.protocol.dubbo.serialization;

import java.util.Collections;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class SerializeSecurityManager {
    //        private static final ErrorTypeAwareLogger logger =
    //                LoggerFactory.getErrorTypeAwareLogger(SerializeSecurityManager.class);

    public static final SerializeSecurityManager INSTANCE = new SerializeSecurityManager();
    private final Set<String> allowedPrefix = Collections.newSetFromMap(new ConcurrentHashMap<>());

    private final Set<String> alwaysAllowedPrefix = Collections.newSetFromMap(new ConcurrentHashMap<>());

    private final Set<String> disAllowedPrefix = Collections.newSetFromMap(new ConcurrentHashMap<>());

    private final Set<AllowClassNotifyListener> listeners =
            Collections.newSetFromMap(new ConcurrentHashMap<>());

    private final Set<String> warnedClasses = Collections.newSetFromMap(new ConcurrentHashMap<>());

    private volatile SerializeCheckStatus checkStatus = null;

    private volatile SerializeCheckStatus defaultCheckStatus = AllowClassNotifyListener.DEFAULT_STATUS;

    private volatile Boolean checkSerializable = null;

    public void addToAlwaysAllowed(String className) {
        boolean modified = alwaysAllowedPrefix.add(className);

        if (modified) {
            notifyPrefix();
        }
    }

    public void addToAllowed(String className) {
        if (disAllowedPrefix.stream()
                .anyMatch(className::startsWith)) {
            return;
        }

        boolean modified = allowedPrefix.add(className);

        if (modified) {
            notifyPrefix();
        }
    }

    public void addToDisAllowed(String className) {
        boolean modified = disAllowedPrefix.add(className);
        modified = allowedPrefix.removeIf(allow -> allow.startsWith(className)) || modified;

        if (modified) {
            notifyPrefix();
        }

        String lowerCase = className.toLowerCase(Locale.ROOT);
        if (!Objects.equals(lowerCase, className)) {
            addToDisAllowed(lowerCase);
        }
    }

    public void setDefaultCheckStatus(SerializeCheckStatus checkStatus) {
        this.defaultCheckStatus = checkStatus;
        //            logger.info("Serialize check default level: " + checkStatus.name());
        notifyCheckStatus();
    }

    public void registerListener(AllowClassNotifyListener listener) {
        listeners.add(listener);
        listener.notifyPrefix(getAllowedPrefix(), getDisAllowedPrefix());
        listener.notifyCheckSerializable(isCheckSerializable());
        listener.notifyCheckStatus(getCheckStatus());
    }

    private void notifyPrefix() {
        for (AllowClassNotifyListener listener : listeners) {
            listener.notifyPrefix(getAllowedPrefix(), getDisAllowedPrefix());
        }
    }

    private void notifyCheckStatus() {
        for (AllowClassNotifyListener listener : listeners) {
            listener.notifyCheckStatus(getCheckStatus());
        }
    }

    private void notifyCheckSerializable() {
        for (AllowClassNotifyListener listener : listeners) {
            listener.notifyCheckSerializable(isCheckSerializable());
        }
    }

    protected SerializeCheckStatus getCheckStatus() {
        return checkStatus == null ? defaultCheckStatus : checkStatus;
    }

    public void setCheckStatus(SerializeCheckStatus checkStatus) {
        if (this.checkStatus == null) {
            this.checkStatus = checkStatus;
            //                logger.info("Serialize check level: " + checkStatus.name());
            notifyCheckStatus();
            return;
        }

        // If has been set to WARN, ignore STRICT
        if (this.checkStatus.level() <= checkStatus.level()) {
            return;
        }

        this.checkStatus = checkStatus;
        //            logger.info("Serialize check level: " + checkStatus.name());
        notifyCheckStatus();
    }

    protected Set<String> getAllowedPrefix() {
        Set<String> set = Collections.newSetFromMap(new ConcurrentHashMap<>());
        set.addAll(allowedPrefix);
        set.addAll(alwaysAllowedPrefix);
        return set;
    }

    protected Set<String> getDisAllowedPrefix() {
        Set<String> set = Collections.newSetFromMap(new ConcurrentHashMap<>());
        set.addAll(disAllowedPrefix);
        return set;
    }

    protected boolean isCheckSerializable() {
        return checkSerializable == null || checkSerializable;
    }

    public void setCheckSerializable(boolean checkSerializable) {
        if (this.checkSerializable == null || (Boolean.TRUE.equals(this.checkSerializable) && !checkSerializable)) {
            this.checkSerializable = checkSerializable;
            //                logger.info("Serialize check serializable: " + checkSerializable);
            notifyCheckSerializable();
        }
    }

    public Set<String> getWarnedClasses() {
        return warnedClasses;
    }
}
