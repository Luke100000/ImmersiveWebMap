package immersive_web_map.rest;

import com.google.gson.Gson;
import immersive_web_map.Common;
import immersive_web_map.Config;
import org.apache.commons.io.IOUtils;

import java.io.ByteArrayOutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

public class API {
    public static AtomicInteger bytesSent = new AtomicInteger();
    public static AtomicInteger bodiesSent = new AtomicInteger();

    public enum HttpMethod {
        POST, GET, DELETE, PUT
    }

    public static String request(HttpMethod httpMethod, String url, Map<String, String> queryParams) {
        return request(httpMethod, url, queryParams, null);
    }

    public static String request(HttpMethod httpMethod, String url, Map<String, String> queryParams, Object body) {
        try {
            String fullUrl = Config.getInstance().url + url;

            // Append query params
            if (queryParams != null) {
                fullUrl = queryParams.keySet().stream()
                        .map(key -> key + "=" + URLEncoder.encode(queryParams.get(key), StandardCharsets.UTF_8))
                        .collect(Collectors.joining("&", fullUrl + "?", ""));
            }

            HttpURLConnection con = (HttpURLConnection) (new URL(fullUrl)).openConnection();

            // Set request method
            con.setRequestMethod(httpMethod.name());

            // Set request headers
            con.setRequestProperty("Content-Type", "application/json");
            con.setRequestProperty("Accept-Encoding", "gzip");
            con.setRequestProperty("Accept", "application/json");

            // Set request body
            if (body != null) {
                con.setDoOutput(true);
                Gson gson = new Gson();
                String jsonBody = gson.toJson(body);

                ByteArrayOutputStream byteStream = new ByteArrayOutputStream();
                try (GZIPOutputStream gzipStream = new GZIPOutputStream(byteStream)) {
                    gzipStream.write(jsonBody.getBytes(StandardCharsets.UTF_8));
                }
                byte[] compressedData = byteStream.toByteArray();

                con.setRequestProperty("Content-Encoding", "gzip");
                con.getOutputStream().write(compressedData);

                bytesSent.addAndGet(compressedData.length);
                bodiesSent.incrementAndGet();
            }

            // Send the request and read response
            if (con.getErrorStream() != null) {
                return IOUtils.toString(con.getErrorStream(), StandardCharsets.UTF_8);
            }

            // Parse answer
            if ("gzip".equals(con.getContentEncoding())) {
                return IOUtils.toString(new GZIPInputStream(con.getInputStream()), StandardCharsets.UTF_8);
            } else {
                return IOUtils.toString(con.getInputStream(), StandardCharsets.UTF_8);
            }
        } catch (Exception e) {
            Common.LOGGER.error(e);
        }
        return null;
    }
}