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
package nuxeo.labs.generic.service.call.test;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Map;

import javax.inject.Inject;

import org.apache.commons.lang3.StringUtils;
import org.json.JSONObject;
import org.junit.Assume;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.nuxeo.ecm.automation.AutomationService;
import org.nuxeo.ecm.automation.OperationContext;
import org.nuxeo.ecm.automation.test.AutomationFeature;
import org.nuxeo.ecm.core.api.Blob;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.impl.blob.FileBlob;
import org.nuxeo.ecm.core.test.DefaultRepositoryInit;
import org.nuxeo.ecm.core.test.annotations.Granularity;
import org.nuxeo.ecm.core.test.annotations.RepositoryConfig;
import org.nuxeo.runtime.test.runner.Deploy;
import org.nuxeo.runtime.test.runner.Features;
import org.nuxeo.runtime.test.runner.FeaturesRunner;

import nuxeo.labs.generic.service.call.http.ServiceCallResult;
import nuxeo.labs.generic.service.call.operations.CallServiceForTokenOp;
import nuxeo.labs.generic.service.call.operations.DownloadFileOp;
import nuxeo.labs.generic.service.call.operations.UploadFileOp;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;

/**
 * For quick download test, Aassume some env. variables:
 * SERVICECALL_TEST_DOWNLOAD_URL
 * SERVICECALL_TEST_DOWNLOAD_FILENAME
 * SERVICECALL_TEST_DOWNLOAD_MIMETYPE
 * SERVICECALL_TEST_DOWNLOAD_AUTH_HEADER
 * 
 * @since TODO
 */
@RunWith(FeaturesRunner.class)
@Features(AutomationFeature.class)
@RepositoryConfig(init = DefaultRepositoryInit.class, cleanup = Granularity.METHOD)
@Deploy("nuxeo.labs.generic.service.call.nuxeo-labs-generic-service-call-core")
public class TestOperations {

    @Inject
    protected CoreSession session;

    @Inject
    protected AutomationService automationService;

    @Test
    public void testCallServiceForTokenOpWithMockServer() throws Exception {
        
        // Using a Mockserver
        try (MockWebServer server = new MockWebServer()) {
            // Enqueue a mock response
            server.enqueue(new MockResponse().setResponseCode(200)
                                             .setBody("{\"access_token\":\"123\", \"expires_in\": 3, \"token_type\":\"Bearer\"}")
                                             .addHeader("Content-Type", "application/json"));
            // Start server
            server.start();

            String fakeUrl = server.url("/auth").toString();

            OperationContext ctx = new OperationContext(session);
            Map<String, Object> params = new HashMap<>();
            params.put("httpMethod", "post");
            params.put("url", fakeUrl);
            Map<String, String> headers = Map.of("Authorization", "Basic EncodedClientId:ClientSecret", "Content-Type",
                    "application/json");
            JSONObject headersJson = new JSONObject(headers);
            params.put("headersJsonStr", headersJson.toString());
            params.put("bodyStr", "{\"param\":\"we don't actually care\"}");
            
            Blob resultBlob = (Blob) automationService.run(ctx, CallServiceForTokenOp.ID, params);
            assertNotNull(resultBlob);
            
            String resultJsonStr = resultBlob.getString();
            assertNotNull(resultJsonStr);
            
            JSONObject resultJson = new JSONObject(resultJsonStr);
            assertEquals(resultJson.getInt("responseCode"), 200);
            assertNotNull(resultJson.get("tokenUuid"));
            assertEquals("123", resultJson.get("access_token"));
            assertEquals(3, resultJson.get("expires_in"));

            // Assert server received correct values
            /*
             * var request = server.takeRequest();
             * assertEquals("GET", request.getMethod());
             * assertEquals("/auth", request.getPath());
             * assertEquals("Basic EncodedClientId:ClientSecret", request.getHeader("Authorization"));
             */

        }
    }
    
    @Test
    public void testGetTokenShouldFail() throws Exception {
        
        OperationContext ctx = new OperationContext(session);
        Map<String, Object> params = new HashMap<>();
        params.put("httpMethod", "post");
        params.put("url", "https://blahblah.com.uiuiuiui");
        Map<String, String> headers = Map.of("Authorization", "Basic EncodedClientId:ClientSecret", "Content-Type",
                "application/json");
        JSONObject headersJson = new JSONObject(headers);
        params.put("headersJsonStr", headersJson.toString());
        params.put("bodyStr", "{\"something\":\"we don't actually care\"}");
        
        Blob resultBlob = (Blob) automationService.run(ctx, CallServiceForTokenOp.ID, params);
        assertNotNull(resultBlob);
        
        String resultJsonStr = resultBlob.getString();
        assertNotNull(resultJsonStr);
        
        JSONObject resultJson = new JSONObject(resultJsonStr);
        assertNotNull(resultJson.get("tokenUuid"));
        
        assertTrue(resultJson.has("responseCode"));
        int responseCode = resultJson.getInt("responseCode");
        assertFalse(ServiceCallResult.isHttpSuccess(responseCode));
    }
    
    
    @Test
    public void shopuldUploadFileWithPOSTAndMockServer() throws Exception {
        
        try (MockWebServer server = new MockWebServer()) {
            server.enqueue(new MockResponse().setResponseCode(200).setBody("OK"));
            server.start();

            // Create a temporary PDF file for upload
            File testFile = File.createTempFile("my-file", ".pdf");
            try (FileOutputStream fos = new FileOutputStream(testFile)) {
                fos.write("This is a PDF content".getBytes());
            }
            Blob blob = new FileBlob(testFile);
            blob.setMimeType("application/pdf");
            blob.setFilename("my-file.pdf");

            String targetUrl = server.url("/upload").toString();

            OperationContext ctx = new OperationContext(session);
            ctx.setInput(blob);
            Map<String, Object> params = new HashMap<>();
            params.put("httpMethod", "POST");
            params.put("url", targetUrl);
            
            Blob resultBlob = (Blob) automationService.run(ctx, UploadFileOp.ID, params);
            assertNotNull(resultBlob);

            // Verify the server received the request
            RecordedRequest request = server.takeRequest();
            assertEquals("POST", request.getMethod());

            // Check Content-Type
            String contentType = request.getHeader("Content-Type");
            assertNotNull(contentType);
            assertTrue(contentType.contains("application/pdf"));

            // Check the body contains the uploaded content (for non-multipart POST)
            byte[] received = request.getBody().readByteArray();
            byte[] expected = Files.readAllBytes(testFile.toPath());
            assertArrayEquals(expected, received);

            // Clean up
            testFile.delete();
        }
    }
    
    @Test
    public void testQuickRealDownload() throws Exception {
        
        String url = System.getenv("SERVICECALL_TEST_DOWNLOAD_URL");
        String fileName = System.getenv("SERVICECALL_TEST_DOWNLOAD_FILENAME");
        String mimeType = System.getenv("SERVICECALL_TEST_DOWNLOAD_MIMETYPE");
        String authHeader = System.getenv("SERVICECALL_TEST_DOWNLOAD_AUTH_HEADER");
        
        if(!StringUtils.isNoneBlank(url, fileName, mimeType, authHeader)) {
            Assume.assumeTrue("testQuickRealDownload => Missing env. variables for the unit test => not testing.", false);
        }
        
        // (not mocked)
        OperationContext ctx = new OperationContext(session);
        Map<String, Object> params = new HashMap<>();
        params.put("url", url);
        Map<String, String> headers = Map.of("Authorization", "Basic " + authHeader);
        JSONObject headersJson = new JSONObject(headers);
        params.put("headersJsonStr", headersJson.toString());
        
        try {// We try because we can't make sure the service is always up and running, the blob is still there, pwd changed …
            
            Blob resultBlob = (Blob) automationService.run(ctx, DownloadFileOp.ID, params);
            assertNotNull(resultBlob);
            assertEquals(fileName, resultBlob.getFilename());
            assertEquals(mimeType, resultBlob.getMimeType());
            
        } catch (Exception e) {
            Assume.assumeTrue("testQuickRealDownload => error from distant server: " + e.getMessage(), false);
        }
        
    }
}
