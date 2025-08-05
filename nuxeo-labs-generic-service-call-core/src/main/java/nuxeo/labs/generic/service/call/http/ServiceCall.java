/*
 * (C) Copyright 2025 Hyland (http://hyland.com/)  and others.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * Contributors:
 *     Thibaud Arguillere
 */
package nuxeo.labs.generic.service.call.http;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.JSONObject;
import org.nuxeo.ecm.core.api.Blob;
import org.nuxeo.ecm.core.api.Blobs;
import org.nuxeo.ecm.core.api.CloseableFile;
import org.nuxeo.ecm.core.api.NuxeoException;
import org.nuxeo.ecm.platform.mimetype.interfaces.MimetypeRegistry;
import org.nuxeo.runtime.api.Framework;

/**
 * Utility class, centralizing the HTTP calls and returning a <code>ServiceCallResult</code>
 * 
 * @since 2023
 */
public class ServiceCall {

    private static final Logger log = LogManager.getLogger(ServiceCall.class);

    public static Map<String, String> toHeadersMap(String headersJsonStr) {

        Map<String, String> headers = new HashMap<>();
        if (StringUtils.isNotBlank(headersJsonStr)) {
            JSONObject headersJson = new JSONObject(headersJsonStr);
            Iterator<String> keys = headersJson.keys();
            while (keys.hasNext()) {
                String key = keys.next();
                headers.put(key, headersJson.getString(key));
            }
        }

        return headers;
    }

    /**
     * Query params, if any, must be handled but the caller (and appended to the url, with the correct encoding)
     * 
     * @param url
     * @param headers. Can be null.
     * @return a ServiceCallResult
     * @since 2023
     */
    public ServiceCallResult get(String url, Map<String, String> headers) {

        ServiceCallResult result = null;

        HttpURLConnection connection = null;
        try {
            // Create the URL object
            URL theUrl = new URL(url);
            connection = (HttpURLConnection) theUrl.openConnection();
            connection.setRequestMethod("GET");

            if (headers != null) {
                headers.forEach(connection::setRequestProperty);
            }

            result = readResponse(connection);

        } catch (IOException e) {
            log.error("Error: " + e.getMessage());
            result = new ServiceCallResult("{}", -1, "IOException: " + e.getMessage());
        } finally {
            if (connection != null) {
                connection.disconnect();
                connection = null;
            }
        }

        return result;
    }

    /*
     * Just to centralize the calls. For now, they are the same
     * (may change in the future, depending on the change sin the service API)
     */
    protected ServiceCallResult postOrPut(String httpMethod, String url, Map<String, String> headers, String body) {

        ServiceCallResult result = null;

        HttpURLConnection connection = null;
        try {
            // Create the URL object
            URL theUrl = new URL(url);
            connection = (HttpURLConnection) theUrl.openConnection();
            // POST or PUT
            connection.setRequestMethod(httpMethod);

            if (headers != null) {
                headers.forEach(connection::setRequestProperty);
            }

            connection.setDoOutput(true);
            if (body != null) {
                try (OutputStream os = connection.getOutputStream()) {
                    byte[] input = body.getBytes(StandardCharsets.UTF_8);
                    os.write(input, 0, input.length);
                }
            }

            result = readResponse(connection);

        } catch (IOException e) {
            log.error("Error: " + e.getMessage());
            result = new ServiceCallResult("{}", -1, "IOException: " + e.getMessage());
        } finally {
            if (connection != null) {
                connection.disconnect();
                connection = null;
            }
        }

        return result;
    }

    public ServiceCallResult post(String url, Map<String, String> headers, String body) {

        ServiceCallResult result = postOrPut("POST", url, headers, body);

        return result;
    }

    public ServiceCallResult put(String url, Map<String, String> headers, String body) {

        ServiceCallResult result = postOrPut("PUT", url, headers, body);

        return result;
    }

    public ServiceCallResult uploadBlob(String putOrPost, Blob blob, String targetUrl, Map<String, String> headers) {

        try (CloseableFile f = blob.getCloseableFile()) {
            String mimeType = blob.getMimeType();
            if (StringUtils.isBlank(mimeType)) {
                MimetypeRegistry registry = Framework.getService(MimetypeRegistry.class);
                mimeType = registry.getMimetypeFromBlob(blob);
            }
            return uploadFile(putOrPost, f.getFile(), targetUrl, mimeType, headers);
        } catch (IOException e) {
            throw new NuxeoException("IOException while uploading the blob.", e);
        }
    }

    /**
     * Upload a file with PUT or POST. If POST, ahdnel big files and sending chunks.
     * 
     * @param putOrPost
     * @param file
     * @param targetUrl
     * @param contentType
     * @param headers
     * @return
     * @throws IOException
     * @since TODO
     */
    public ServiceCallResult uploadFile(String putOrPost, File file, String targetUrl, String contentType,
            Map<String, String> headers) {
        
        putOrPost = putOrPost.toUpperCase();
        switch (putOrPost) {
        case "POST":
        case "PUT":
            break;

        default:
            throw new NuxeoException("Oonly PUT or POST handled.");
        }
        
        ServiceCallResult result = null;

        if (StringUtils.isBlank(contentType)) {
            try {
                contentType = Files.probeContentType(file.toPath());
            } catch (IOException e) {
                contentType = "application/octet-stream";
            }
        }
        
        try {
            HttpClient client = HttpClient.newHttpClient();
    
            HttpRequest.Builder builder = HttpRequest.newBuilder()
                                                     .uri(URI.create(targetUrl))
                                                     .header("Content-Type", contentType);
            // Add custom headers
            if (headers != null && !headers.isEmpty()) {
                headers.forEach(builder::header);
            }
            
            // Choose method
            HttpRequest.BodyPublisher body = HttpRequest.BodyPublishers.ofFile(file.toPath());
            switch (putOrPost) {
                case "POST" -> builder.POST(body);
                case "PUT" -> builder.PUT(body);
            }
            
            // Build
            HttpRequest request = builder.build();
    
            // Call
            HttpResponse<String> response;
            try {
                response = client.send(request, HttpResponse.BodyHandlers.ofString());
                // Remember nothing to close, this is handled by BodyHandlers.ofString()
                result = new ServiceCallResult("{}", response.statusCode(), response.body());
            } catch (IOException | InterruptedException e) {
                result = new ServiceCallResult("{}", -1, "Error uploading the file: " + e.getMessage());
            }
        } catch (Exception e) {
            throw new NuxeoException("Exception while uploading the blob.", e);
        }

        return result;
        
    }


    /**
     * @param targetUrl
     * @param headers
     * @return
     * @since TODO
     */
    public ServiceCallResult downloadFile(String targetUrl, Map<String, String> headers) {

        ServiceCallResult result = null;
        Blob blob = null;
        HttpURLConnection connection = null;

        try {
            URL url = new URL(targetUrl);
            connection = (HttpURLConnection) url.openConnection();

            connection.setRequestMethod("GET");
            connection.setDoInput(true);

            // Add custom headers
            if (headers != null) {
                headers.forEach(connection::setRequestProperty);
            }

            int status = connection.getResponseCode();

            if (status < 200 || status >= 300) {
                String error = "";
                try (InputStream errorStream = connection.getErrorStream()) {
                    if (errorStream != null) {
                        error = new String(errorStream.readAllBytes());
                    }
                } catch (IOException e) {
                    // Ignore
                }
                Blob nullBlob = null;
                result = new ServiceCallResult(nullBlob, status, error);

            } else {

                String filename = extractFileName(connection, url);
                String mimeType = connection.getContentType();
                blob = Blobs.createBlobWithExtension(".tmp");
                blob.setFilename(filename);
                blob.setMimeType(mimeType);
                File destinationFile = blob.getFile();

                // Stream content to file
                try (InputStream in = connection.getInputStream();
                        OutputStream out = new FileOutputStream(destinationFile)) {
                    in.transferTo(out);
                }

                result = new ServiceCallResult(blob, status, connection.getResponseMessage());
            }

        } catch (IOException e) {

            throw new NuxeoException("Error downloading a file", e);

        } finally {
            if (connection != null) {
                connection.disconnect();
                connection = null;
            }
        }

        return result;
    }

    // Extract filename from Content-Disposition header or URL
    public static String extractFileName(HttpURLConnection connection, URL url) {
        String contentDisposition = connection.getHeaderField("Content-Disposition");

        if (contentDisposition != null && contentDisposition.contains("filename=")) {
            String[] parts = contentDisposition.split("filename=");
            if (parts.length > 1) {
                return parts[1].replaceAll("\"", "").trim();
            }
        }

        // fallback: get filename from URL
        String path = url.getPath();
        return path.substring(path.lastIndexOf('/') + 1);
    }

    /**
     * Utility, used by other methods (get, post, put), cone the call returns a status >= 200 < 300.
     * The "response" field of <code>Response</code> is always an empty JSON object, "{}".
     * 
     * @param connection
     * @return
     * @throws IOException
     * @since 2023
     */
    public ServiceCallResult readResponse(HttpURLConnection connection) throws IOException {

        ServiceCallResult result = null;

        int responseCode = connection.getResponseCode();
        if (ServiceCallResult.isHttpSuccess(responseCode)) {
            try (BufferedReader br = new BufferedReader(
                    new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8))) {
                StringBuilder responseStr = new StringBuilder();
                String line;
                while ((line = br.readLine()) != null) {
                    responseStr.append(line.trim());
                }
                result = new ServiceCallResult(responseStr.toString(), responseCode, connection.getResponseMessage());
            }
        } else {
            result = new ServiceCallResult("{}", responseCode, connection.getResponseMessage());
        }

        return result;
    }

}
