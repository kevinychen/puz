package com.kyc.server;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import javax.imageio.ImageIO;
import javax.xml.ws.Service;
import javax.xml.ws.ServiceMode;
import javax.xml.ws.WebServiceProvider;

import org.apache.commons.fileupload.FileItem;
import org.apache.commons.fileupload.RequestContext;
import org.apache.commons.fileupload.disk.DiskFileItemFactory;
import org.apache.commons.fileupload.servlet.ServletFileUpload;

import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableMap;
import com.google.common.io.ByteStreams;
import com.kyc.hawkeye.WordsearchSolver;
import com.kyc.hawkeye.WordsearchSolverConfiguration;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

@WebServiceProvider
@ServiceMode(value = Service.Mode.PAYLOAD)
public class PuzzleServer {

    public static void main(String[] args) throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(2915), 0);
        server.createContext("/hawkeye", new HawkeyeHandler());
        server.createContext("/hawkeye/submit", new HawkeyeSubmitHandler());
        server.start();
        System.out.println("Puzzle server running at " + server.getAddress());
    }

    static class HawkeyeHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange t) throws IOException {
            byte[] response = ByteStreams.toByteArray(ClassLoader.getSystemResource("hawkeye.html").openStream());
            t.sendResponseHeaders(200, response.length);
            OutputStream os = t.getResponseBody();
            os.write(response);
            os.close();
        }
    }

    static class HawkeyeSubmitHandler implements HttpHandler {
        @Override
        public void handle(final HttpExchange t) throws IOException {
            try {
                List<FileItem> fileItems = new ServletFileUpload(new DiskFileItemFactory()).parseRequest(new RequestContext() {
                    @Override
                    public String getCharacterEncoding() {
                        return "UTF-8";
                    }
                    @Override
                    public int getContentLength() {
                        return 0;
                    }
                    @Override
                    public String getContentType() {
                        return t.getRequestHeaders().getFirst("Content-type");
                    }
                    @Override
                    public InputStream getInputStream() throws IOException {
                        return t.getRequestBody();
                    }
                });
                BufferedImage image = null;
                WordsearchSolverConfiguration config = new WordsearchSolverConfiguration();
                for (FileItem fileItem : fileItems) {
                    String field = fileItem.getFieldName();
                    if (field.equals("image"))
                        image = ImageIO.read(fileItem.getInputStream());
                    else if (field.equals("dark-cutoff"))
                        config.darkCutoff = Integer.parseInt(fileItem.getString());
                    else if (field.equals("min-blob-separation"))
                        config.minBlobSeparation = Integer.parseInt(fileItem.getString());
                    else if (field.equals("min-blob-size-relative"))
                        config.minBlobSizeRelative = Double.parseDouble(fileItem.getString());
                    else if (field.equals("max-blob-separation-ratio"))
                        config.maxBlobSeparationRatio = Double.parseDouble(fileItem.getString());
                    else if (field.equals("blob-border-for-OCR"))
                        config.blobBorderForOCR = Integer.parseInt(fileItem.getString());
                    else if (field.equals("allowed-chars-for-OCR"))
                        config.allowedCharsForOCR = fileItem.getString();
                    else if (field.equals("max-batch-for-OCR"))
                        config.maxBatchForOCR = Integer.parseInt(fileItem.getString());
                    else if (field.equals("max-word-bend-angle"))
                        config.maxWordBendAngle = Double.parseDouble(fileItem.getString());
                    else if (field.equals("min-word-length"))
                        config.minWordLength = Integer.parseInt(fileItem.getString());
                }
                Map<String, List<Integer>> search;
                if (image == null) {
                    search = ImmutableMap.of();
                } else {
                    search = new WordsearchSolver(image, config).search();
                }
                t.getResponseHeaders().add("Content-type", "application/json");
                t.sendResponseHeaders(200, 0);
                OutputStream os = t.getResponseBody();
                String json = "{" + Joiner.on(", ").withKeyValueSeparator(": ").join(search.entrySet().stream()
                        .collect(Collectors.toMap(entry -> "\"" + entry.getKey() + "\"", entry -> entry.getValue()))) + "}";
                os.write(json.getBytes());
                os.close();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }
}
