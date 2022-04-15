package com.github.netty.protocol.servlet;

import com.github.netty.core.util.CaseInsensitiveKeyMap;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
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
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
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
        return mimeTypeMappings.get(ext);
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
        if (characterEncoding != null && characterEncoding.length() > 0) {
            response.setCharacterEncoding(characterEncoding);
        }
        if (contentType == null || contentType.isEmpty()) {
            contentType = getContentType(file);
        }
        if (contentType != null && contentType.length() > 0) {
            response.setContentType(contentType);
        }
        NettyOutputStream outputStream = (NettyOutputStream) response.getOutputStream();
        outputStream.write(file);
    }

    protected File getHomePage(HttpServletRequest request, String dir) throws MalformedURLException {
        String dirPath;
        if ("/".equals(dir)) {
            dirPath = "";
        } else {
            dirPath = dir;
        }
        for (String homePage : homePages) {
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

}
