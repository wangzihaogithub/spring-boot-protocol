package com.github.netty.protocol.servlet;

import com.github.netty.core.util.CaseInsensitiveKeyMap;
import com.github.netty.core.util.RecyclableUtil;
import com.github.netty.protocol.servlet.util.HttpHeaderUtil;
import com.github.netty.protocol.servlet.util.MimeMappingsX;
import io.netty.buffer.ByteBuf;
import io.netty.handler.codec.DateFormatter;

import javax.servlet.ServletResponse;
import javax.servlet.ServletResponseWrapper;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.charset.Charset;
import java.util.*;

/**
 * a default servlet
 *
 * <pre>
 *   public class DemoApplication {
 *       public static void main(String[] args) {
 *          StartupServer server = new StartupServer(80);
 *          server.addProtocol(newHttpProtocol());
 *          server.start();
 *      }
 *
 *      private static HttpServletProtocol newHttpProtocol() {
 *          ServletContext servletContext = new ServletContext();
 *          servletContext.setDocBase("D://demo", "/webapp");
 *          servletContext.addServlet("myServlet", new DefaultServlet()).addMapping("/");
 *          return new HttpServletProtocol(servletContext);
 *      }
 *  }
 * </pre>
 *
 * @author wangzihao
 * 2018/7/15/015
 */
public class DefaultServlet extends HttpServlet {
    private static final List<Range> FULL = Collections.unmodifiableList(new ArrayList<>());
    private static final Charset ISO_8859_1 = Charset.forName("ISO-8859-1");
    private static final String BOUNDARY = "CATALINA_MIME_BOUNDARY";
    private static final byte[] MIME_BOUNDARY_BEGIN = ("\r\n--" + BOUNDARY).getBytes(ISO_8859_1);
    private static final byte[] MIME_BOUNDARY_END = ("\r\n--" + BOUNDARY + "--").getBytes(ISO_8859_1);
    public static final Properties DEFAULT_MIME_TYPE_MAPPINGS = new Properties();

    private Set<String> homePages = new LinkedHashSet<>(Arrays.asList("index.html", "index.htm", "index"));
    private String characterEncoding = "utf-8";
    private Map<String, String> mimeTypeMappings = new CaseInsensitiveKeyMap<>();

    static {
        try (InputStream is = DefaultServlet.class.getResourceAsStream
                ("/MimeTypeMappings.properties")) {
            DEFAULT_MIME_TYPE_MAPPINGS.load(is);
        } catch (Throwable t) {
            //
        }
    }

    public DefaultServlet() {
        DEFAULT_MIME_TYPE_MAPPINGS.forEach((k, v) -> mimeTypeMappings.put(k.toString(), v.toString()));
    }

    @Override
    public void init() {
        ServletContext servletContext = (ServletContext) getServletContext();
        for (MimeMappingsX.MappingX mapping : servletContext.getMimeMappings()) {
            mimeTypeMappings.put(mapping.getExtension(), mapping.getMimeType());
        }
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        String requestURI = request.getRequestURI();
        File resource = getFile(requestURI);
        if (resource == null) {
            sendNotFound(request, response);
        } else if (resource.isFile()) {
            sendFile(request, response, resource, null);
        } else {
            File homePage = getHomePage(request, requestURI);
            if (homePage != null) {
                sendFile(request, response, homePage, "text/html");
            } else {
                sendDir(request, response, resource, requestURI);
            }
        }
    }

    public Map<String, String> getMimeTypeMappings() {
        return mimeTypeMappings;
    }

    public String getCharacterEncoding() {
        return characterEncoding;
    }

    public void setCharacterEncoding(String characterEncoding) {
        this.characterEncoding = characterEncoding;
    }

    public String getContentType(File file) {
        String name = file.getName();
        int dotIndex = name.lastIndexOf('.');
        if (dotIndex == -1) {
            return null;
        }
        String ext = name.substring(dotIndex + 1);
        return getMimeTypeMappings().get(ext);
    }

    public Set<String> getHomePages() {
        return homePages;
    }

    protected void sendNotFound(HttpServletRequest request, HttpServletResponse response) throws IOException {
        response.sendError(HttpServletResponse.SC_NOT_FOUND);
    }

    protected void sendDir(HttpServletRequest request, HttpServletResponse response, File dir, String requestDir) throws IOException {
        // security hidden dir. send not found file.
        sendNotFound(request, response);
    }

    protected void sendFile(HttpServletRequest request, HttpServletResponse response, File file, String contentType) throws IOException {
        String characterEncoding = getCharacterEncoding();
        if (characterEncoding != null && characterEncoding.length() > 0) {
            response.setCharacterEncoding(characterEncoding);
        }
        if (contentType == null || contentType.isEmpty()) {
            contentType = getContentType(file);
        }
        if (contentType != null && contentType.length() > 0) {
            response.setContentType(contentType);
        }
        sendRange(request, response, file, contentType);
    }

    protected void sendRange(HttpServletRequest request, HttpServletResponse response, File file, String contentType) throws IOException {
        WebResource resource = new WebResource(file);

        List<Range> ranges = parseRange(request, response, resource);
        if (ranges == null) {
            return;
        }

        response.setHeader("Accept-Ranges", "bytes");
        String eTag = resource.getETag();
        if (eTag != null) {
            response.setHeader("ETag", eTag);
        }
        String lastModifiedHttp = resource.getLastModifiedHttp();
        if (lastModifiedHttp != null) {
            response.setHeader("Last-Modified", lastModifiedHttp);
        }

        ServletResponse r = response;
        while (r instanceof ServletResponseWrapper) {
            r = ((ServletResponseWrapper) r).getResponse();
        }

        NettyOutputStream ostream = (NettyOutputStream) r.getOutputStream();
        if (ranges.isEmpty()) {
            ostream.write(file);
        } else {
            response.setStatus(HttpServletResponse.SC_PARTIAL_CONTENT);
            if (ranges.size() == 1) {
                Range range = ranges.get(0);
                long contentLength = range.getContentLength();
                response.addHeader("Content-Range", range.getContentRangeValue());
                response.setContentLengthLong(contentLength);
                ostream.write(file, range.start, contentLength);
            } else {
                response.setContentType("multipart/byteranges; boundary=" + BOUNDARY);
                sendMultipartByteRanges(file, ostream, ranges, contentType);
            }
        }
    }

    protected void sendMultipartByteRanges(File file, NettyOutputStream ostream, List<Range> ranges, String contentType) throws IOException {
        ByteBuf contentTypeByteBuf = null;
        if (contentType != null && contentType.length() > 0) {
            contentTypeByteBuf = RecyclableUtil.newReadOnlyBuffer(("Content-Type: " + contentType).getBytes(ISO_8859_1));
            contentTypeByteBuf.release();
        }

        for (Range range : ranges) {
            // Writing MIME header.
            ostream.write(RecyclableUtil.newReadOnlyBuffer(MIME_BOUNDARY_BEGIN));
            if (contentTypeByteBuf != null) {
                contentTypeByteBuf.retain();
                ostream.write(contentTypeByteBuf);
            }
            ostream.write(RecyclableUtil.newReadOnlyBuffer(range.getContentRange().getBytes(ISO_8859_1)));
            ostream.write(file, range.start, range.getContentLength());
        }
        ostream.write(RecyclableUtil.newReadOnlyBuffer(MIME_BOUNDARY_END));
    }

    protected File getHomePage(HttpServletRequest request, String dir) throws MalformedURLException {
        String dirPath = "/".equals(dir) ? "" : dir;
        for (String homePage : getHomePages()) {
            File file = getFile(dirPath + "/" + homePage);
            if (file != null && file.isFile()) {
                return file;
            }
        }
        return null;
    }

    protected File getFile(String path) throws MalformedURLException {
        URL resource = getServletContext().getResource(path);
        if (resource == null) {
            return null;
        }
        return new File(resource.getFile());
    }

    protected static List<Range> parseRange(HttpServletRequest request,
                                            HttpServletResponse response,
                                            WebResource resource) throws IOException {
        // Checking If-Range
        String ifRangeStr = request.getHeader("If-Range");
        if (ifRangeStr != null && ifRangeStr.length() > 0) {
            long headerValueTime = -1L;
            try {
                Date ifRangeDate = DateFormatter.parseHttpDate(ifRangeStr);
                if (ifRangeDate != null) {
                    headerValueTime = ifRangeDate.getTime();
                }
            } catch (IllegalArgumentException e) {
                // Ignore
            }

            if (headerValueTime == -1L) {
                String eTag = resource.getETag();
                // If the ETag the client gave does not match the entity
                // etag, then the entire entity is returned.
                if (!eTag.equals(ifRangeStr.trim())) {
                    return FULL;
                }
            } else {
                // If the timestamp of the entity the client got differs from
                // the last modification date of the entity, the entire entity
                // is returned.
                long lastModified = resource.getLastModified();
                if (Math.abs(lastModified - headerValueTime) > 1000) {
                    return FULL;
                }
            }
        }

        long fileLength = resource.getContentLength();
        if (fileLength == 0) {
            // Range header makes no sense for a zero length resource. Tomcat
            // therefore opts to ignore it.
            return FULL;
        }

        // Retrieving the range header (if any is specified
        String rangeHeader = request.getHeader("Range");
        if (rangeHeader == null) {
            // No Range header is the same as ignoring any Range header
            return FULL;
        }

        HttpHeaderUtil.Ranges ranges = HttpHeaderUtil.Ranges.parse(new StringReader(rangeHeader));
        if (ranges == null) {
            // The Range header is present but not formatted correctly.
            // Could argue for a 400 response but 416 is more specific.
            // There is also the option to ignore the (invalid) Range header.
            // RFC7233#4.4 notes that many servers do ignore the Range header in
            // these circumstances but Tomcat has always returned a 416.
            response.addHeader("Content-Range", "bytes */" + fileLength);
            response.sendError(HttpServletResponse.SC_REQUESTED_RANGE_NOT_SATISFIABLE);
            return null;
        }

        // bytes is the only range unit supported (and I don't see the point
        // of adding new ones).
        if (!ranges.units.equals("bytes")) {
            // RFC7233#3.1 Servers must ignore range units they don't understand
            return FULL;
        }

        // Convert to internal representation
        ArrayList<Range> result = new ArrayList<>(ranges.entries.size());
        for (HttpHeaderUtil.Entry entry : ranges.entries) {
            Range currentRange = new Range();
            if (entry.start == -1) {
                currentRange.start = fileLength - entry.end;
                if (currentRange.start < 0) {
                    currentRange.start = 0;
                }
                currentRange.end = fileLength - 1;
            } else if (entry.end == -1) {
                currentRange.start = entry.start;
                currentRange.end = fileLength - 1;
            } else {
                currentRange.start = entry.start;
                currentRange.end = entry.end;
            }
            currentRange.length = fileLength;
            if (!currentRange.validate()) {
                response.addHeader("Content-Range", "bytes */" + fileLength);
                response.sendError(HttpServletResponse.SC_REQUESTED_RANGE_NOT_SATISFIABLE);
                return null;
            }
            result.add(currentRange);
        }
        return result;
    }

    protected static class Range {
        public long start;
        public long end;
        public long length;

        /**
         * Validate range.
         *
         * @return true if the range is valid, otherwise false
         */
        public boolean validate() {
            if (end >= length) {
                end = length - 1;
            }
            return (start >= 0) && (end >= 0) && (start <= end) && (length > 0);
        }

        public long getContentLength() {
            return end - start + 1;
        }

        public String getContentRangeValue() {
            return "bytes " + start + "-" + end + "/" + length;
        }

        public String getContentRange() {
            return "Content-Range: " + getContentRangeValue() + "\r\n";
        }

        @Override
        public String toString() {
            return getContentRange();
        }
    }

    public static class WebResource {
        private String weakETag;
        private final long lastModified;
        private final long length;

        public WebResource(File file) {
            this.length = file.length();
            this.lastModified = file.lastModified();
        }

        public long getLastModified() {
            return lastModified;
        }

        /**
         * @return the last modified time of this resource in the correct format for
         * the HTTP Last-Modified header as specified by RFC 2616.
         */
        public String getLastModifiedHttp() {
            if (lastModified <= 0) {
                return null;
            }
            return DateFormatter.format(new Date(lastModified));
        }

        public long getContentLength() {
            return length;
        }

        /**
         * Return the strong ETag if available (currently not supported) else return
         * the weak ETag calculated from the content length and last modified.
         *
         * @return The ETag for this resource
         */
        public String getETag() {
            if (weakETag == null) {
                long contentLength = getContentLength();
                long lastModified = getLastModified();
                if ((contentLength >= 0) || (lastModified >= 0)) {
                    weakETag = "W/\"" + contentLength + "-" +
                            lastModified + "\"";
                }
            }
            return weakETag;
        }

        @Override
        public String toString() {
            return getETag();
        }
    }

}
