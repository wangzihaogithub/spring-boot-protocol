package com.github.netty.http.example;

import org.apache.commons.fileupload.FileItemIterator;
import org.apache.commons.fileupload.FileItemStream;
import org.apache.commons.fileupload.FileUploadException;
import org.apache.commons.fileupload.servlet.ServletFileUpload;
import org.apache.commons.fileupload.util.Streams;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.util.Assert;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.multipart.MultipartHttpServletRequest;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.Part;
import java.io.IOException;
import java.io.InputStream;
import java.util.*;

@EnableScheduling
@RestController
@RequestMapping
public class HttpController {
    private final Logger logger = LoggerFactory.getLogger(getClass());

    /**
     * 访问地址： http://localhost:8080/test/hello
     * @param name name
     * @return hi! 小明
     */
    @RequestMapping("/hello")
    public String hello(String name, @RequestParam Map query,
//                        @RequestBody(required = false) Map body,
                        HttpServletRequest request, HttpServletResponse response) {
        return "hi! " + name;
    }

    /**
     * Servlet原生的上传测试
     * @param request
     * @param response
     * @return
     * @throws IOException
     */
    @RequestMapping("/uploadForServlet")
    public ResponseEntity<String> uploadForServlet(HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException {
        Collection<Part> parts = request.getParts();

        for (Part part : parts) {
            InputStream inputStream = part.getInputStream();
            int available = inputStream.available();
            inputStream.close();
            String fileNameOrFieldName = Objects.toString(part.getSubmittedFileName(), part.getName());
            Assert.isTrue(available != -1, fileNameOrFieldName);
            logger.info("uploadForServlet -> file = {}, length = {}", fileNameOrFieldName,available);
        }

        for (Map.Entry<String, String[]> entry : request.getParameterMap().entrySet()) {
            Assert.isTrue(entry.getKey().length() > 0, Arrays.toString(entry.getValue()));
            logger.info("uploadForServlet -> field = {}, value = {}",entry.getKey(),entry.getValue());
        }
        return new ResponseEntity<>("success", HttpStatus.OK);
    }

    /**
     * spring的上传测试
     * @param params 文本参数
     * @param request MultipartHttpServletRequest
     * @return
     * @throws IOException
     */
    @RequestMapping("/uploadForSpring")
    public ResponseEntity<String> uploadForSpring(@RequestParam Map<String,String> params, MultipartHttpServletRequest request) throws IOException {
        for (List<MultipartFile> files : request.getMultiFileMap().values()) {
            for (MultipartFile file : files) {
                InputStream inputStream = file.getInputStream();
                int available = inputStream.available();
                inputStream.close();
                String fileNameOrFieldName = Objects.toString(file.getOriginalFilename(), file.getName());
                Assert.isTrue(available != -1, fileNameOrFieldName);
                logger.info("uploadForSpring -> file = {}, length = {}", fileNameOrFieldName,available);
            }
        }

        for (Map.Entry<String, String> entry : params.entrySet()) {
            Assert.isTrue(entry.getKey().length() > 0, entry.getValue());
            logger.info("uploadForSpring -> field = {}, value = {}",entry.getKey(),entry.getValue());
        }
        return new ResponseEntity<>("success", HttpStatus.OK);
    }

    /**
     * apache common-fileupload的上传测试
     * @param request
     * @param response
     * @return
     * @throws IOException
     * @throws FileUploadException
     */
    @RequestMapping("/uploadForApache")
    public ResponseEntity<String> uploadForApache(HttpServletRequest request, HttpServletResponse response) throws IOException, FileUploadException {
        boolean isMultipart = ServletFileUpload.isMultipartContent(request);
        if (isMultipart) {
            ServletFileUpload upload = new ServletFileUpload();
            Map<String, String> params = new HashMap<>();
            FileItemIterator iter = upload.getItemIterator(request);
            while (iter.hasNext()) {
                FileItemStream item = iter.next();
                if (item.isFormField()) {
                    String fieldName = item.getFieldName();
                    String value = Streams.asString(item.openStream());
                    params.put(fieldName, value);
                    Assert.isTrue(fieldName.length() > 0, value);
                    logger.info("uploadForApache -> field = {}, value = {}",fieldName,value);
                } else {
                    try (InputStream is = item.openStream()) {
                        int available = is.available();
                        Assert.isTrue(available != -1, item.getName());
                        logger.info("uploadForApache -> file = {}, length = {}",item.getName(),available);
                    }
                }
            }
        }
        return new ResponseEntity<>("success", HttpStatus.OK);
    }

}