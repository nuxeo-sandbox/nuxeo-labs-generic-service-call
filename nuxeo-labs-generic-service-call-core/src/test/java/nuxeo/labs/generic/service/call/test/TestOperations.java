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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.util.HashMap;
import java.util.Map;

import javax.inject.Inject;

import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.nuxeo.ecm.automation.AutomationService;
import org.nuxeo.ecm.automation.OperationContext;
import org.nuxeo.ecm.automation.OperationException;
import org.nuxeo.ecm.automation.core.annotations.Param;
import org.nuxeo.ecm.automation.test.AutomationFeature;
import org.nuxeo.ecm.core.api.Blob;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.test.DefaultRepositoryInit;
import org.nuxeo.ecm.core.test.annotations.Granularity;
import org.nuxeo.ecm.core.test.annotations.RepositoryConfig;
import org.nuxeo.runtime.test.runner.Deploy;
import org.nuxeo.runtime.test.runner.Features;
import org.nuxeo.runtime.test.runner.FeaturesRunner;

import nuxeo.labs.generic.service.call.AuthenticationToken;
import nuxeo.labs.generic.service.call.AuthenticationTokens;
import nuxeo.labs.generic.service.call.operations.CallServiceForTokenOp;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;

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
            server.enqueue(new MockResponse().setBody("{\"access_token\":\"123\", \"expires_in\": 3, \"token_type\":\"Bearer\"}")
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
}
