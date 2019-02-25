package com.github.netty.protocol.servlet.util;

import java.util.*;

/**
 * Simple container-independent abstraction for servlet mime mappings. Roughly equivalent
 * to the {@literal &lt;mime-mapping&gt;} element traditionally found in web.xml.
 *
 * @author Phillip Webb
 */
public class MimeMappingsX implements Iterable<MimeMappingsX.MappingX> {
    private final Map<String, MappingX> map;

    /**
     * Create a new empty {@link MimeMappingsX} instance.
     */
    public MimeMappingsX() {
        this.map = new LinkedHashMap<String, MappingX>();
    }

    @Override
    public Iterator<MappingX> iterator() {
        return getAll().iterator();
    }

    /**
     * Returns all defined mappings.
     * @return the mappings.
     */
    public Collection<MappingX> getAll() {
        return this.map.values();
    }

    /**
     * Add a new mime mapping.
     * @param extension the file extension (excluding '.')
     * @param mimeType the mime type to map
     * @return any previous mapping or {@code null}
     */
    public String add(String extension, String mimeType) {
        MappingX previous = this.map.put(extension, new MappingX(extension, mimeType));
        return (previous == null ? null : previous.getMimeType());
    }

    /**
     * Get a mime mapping for the given extension.
     * @param extension the file extension (excluding '.')
     * @return a mime mapping or {@code null}
     */
    public String get(String extension) {
        MappingX mapping = this.map.get(extension);
        return (mapping == null ? null : mapping.getMimeType());
    }

    /**
     * Remove an existing mapping.
     * @param extension the file extension (excluding '.')
     * @return the removed mime mapping or {@code null} if no item was removed
     */
    public String remove(String extension) {
        MappingX previous = this.map.remove(extension);
        return (previous == null ? null : previous.getMimeType());
    }

    @Override
    public int hashCode() {
        return this.map.hashCode();
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == null) {
            return false;
        }
        if (obj == this) {
            return true;
        }
        if (obj instanceof MimeMappingsX) {
            MimeMappingsX other = (MimeMappingsX) obj;
            return this.map.equals(other.map);
        }
        return false;
    }

    /**
     * A single mime mapping.
     */
    public  final class MappingX {

        private final String extension;

        private final String mimeType;

        public MappingX(String extension, String mimeType) {
            Objects.requireNonNull(extension, "Extension must not be null");
            Objects.requireNonNull(mimeType, "MimeType must not be null");
            this.extension = extension;
            this.mimeType = mimeType;
        }

        public String getExtension() {
            return this.extension;
        }

        public String getMimeType() {
            return this.mimeType;
        }

        @Override
        public int hashCode() {
            return this.extension.hashCode();
        }

        @Override
        public boolean equals(Object obj) {
            if (obj == null) {
                return false;
            }
            if (obj == this) {
                return true;
            }
            if (obj instanceof MappingX) {
                MappingX other = (MappingX) obj;
                return this.extension.equals(other.extension)
                        && this.mimeType.equals(other.mimeType);
            }
            return false;
        }

        @Override
        public String toString() {
            return "Mapping [extension=" + this.extension + ", mimeType=" + this.mimeType
                    + "]";
        }

    }

}