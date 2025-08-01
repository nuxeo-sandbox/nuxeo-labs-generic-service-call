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
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.util.HashMap;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.json.JSONObject;
import org.junit.Assume;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.nuxeo.ecm.core.test.DefaultRepositoryInit;
import org.nuxeo.ecm.core.test.annotations.Granularity;
import org.nuxeo.ecm.core.test.annotations.RepositoryConfig;
import org.nuxeo.ecm.platform.test.PlatformFeature;
import org.nuxeo.runtime.test.runner.Deploy;
import org.nuxeo.runtime.test.runner.Features;
import org.nuxeo.runtime.test.runner.FeaturesRunner;

import nuxeo.labs.generic.service.call.AuthenticationToken;
import nuxeo.labs.generic.service.call.AuthenticationTokens;
import nuxeo.labs.generic.service.call.http.ServiceCall;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;

/**
 * The tests expect some environment variables to be set:
 * TEST_CallService_AUTH_URL
 * TEST_CallService_AUTH_HEADERS_JSON
 * For different tunings or expected parameters. They can be empty if you don't need them, but must be set.
 * TEST_CallService_AUTH_BODY_1
 * TEST_CallService_AUTH_BODY_2
 * 
 * @since 2023
 */
@RunWith(FeaturesRunner.class)
@Features(PlatformFeature.class)
@RepositoryConfig(init = DefaultRepositoryInit.class, cleanup = Granularity.METHOD)
@Deploy("nuxeo.labs.generic.service.call.nuxeo-labs-generic-service-call-core")
public class TestCallService {

    protected String authUrl;

    protected Map<String, String> headers;

    protected String body1;

    protected String body2;

    protected boolean checkEnvironmentVariables() {

        authUrl = System.getenv("TEST_CallService_AUTH_URL");
        String headersJsonStr = System.getenv("TEST_CallService_AUTH_HEADERS_JSON");

        if (StringUtils.isAnyEmpty(authUrl, headersJsonStr)) {
            return false;
        }
        body1 = System.getenv("TEST_CallService_AUTH_BODY_1");
        body2 = System.getenv("TEST_CallService_AUTH_BODY_2");

        headers = ServiceCall.toHeadersMap(headersJsonStr);

        return true;
    }

    @Test
    public void testTokensMap() throws Exception {

        AuthenticationTokens tokens = AuthenticationTokens.getInstance();

        Map<String, String> noHeaders = new HashMap<String, String>();
        AuthenticationToken token1 = tokens.newToken("GET", "1", noHeaders, "1");
        token1.setToken("1");
        token1.setTokenExpiration(5);

        AuthenticationToken token2 = tokens.newToken("GET", "2", noHeaders, "2");
        token2.setToken("2");
        token2.setTokenExpiration(30);

        AuthenticationToken token3 = tokens.newToken("GET", "3", noHeaders, "3");
        token3.setToken("3");
        token3.setTokenExpiration(5);

        assertEquals(3, tokens.size());

    }

    @Test
    public void shouldGetATokenWithMockServer() throws Exception {

        try (MockWebServer server = new MockWebServer()) {
            // Enqueue a mock response
            server.enqueue(new MockResponse().setBody("{\"access_token\":\"123\", \"expires_in\": 3, \"token_type\":\"Bearer\"}")
                                             .addHeader("Content-Type", "application/json"));

            // Start server
            server.start();

            String fakeUrl = server.url("/auth").toString();

            // "Authenticate"
            Map<String, String> headers = Map.of("Authorization", "Basic EncodedClientId:ClientSecret", "Content-Type",
                    "application/json");
            AuthenticationToken token = AuthenticationTokens.getInstance().newToken("GET", fakeUrl, headers, null);
            assertNotNull(token);

            String tokenValue = token.getToken();
            assertEquals("123", tokenValue);
            
            JSONObject tokenJson = token.tokenToJSONObject();
            assertTrue(tokenJson.has("tokenUuid"));
            assertTrue(tokenJson.has("access_token"));
            assertTrue(tokenJson.has("expires_in"));
            assertTrue(tokenJson.has("token_type"));

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
    public void testExpiredTokenWithMockServer() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            // Enqueue a mock response for the token
            server.enqueue(new MockResponse().setBody("{\"access_token\":\"123\", \"expires_in\": 3}")
                                             .addHeader("Content-Type", "application/json"));
            // Start server
            server.start();

            String fakeUrl = server.url("/auth").toString();

            // "Authenticate"
            Map<String, String> headers = Map.of("Authorization", "Basic EncodedClientId:ClientSecret", "Content-Type",
                    "application/json");
            AuthenticationToken token = AuthenticationTokens.getInstance().newToken("GET", fakeUrl, headers, "");
            assertNotNull(token);

            String tokenValue = token.getToken();
            assertEquals("123", tokenValue);

            Thread.sleep(3000);
            assertTrue(token.isExpired());

            // Need to add a custom dispatcher to the mock server to handle an expired token
            // But it's a bit long to do :-)

        }
    }

    @Ignore
    @Test
    public void shouldGetAToken() throws Exception {

        Assume.assumeTrue("Missing env. variables for the unit test => not testing.", checkEnvironmentVariables());

        // AuthenticationToken token = new AuthenticationToken(authUrl, headers, body);
        AuthenticationToken token = AuthenticationTokens.getInstance().newToken("POST", authUrl, headers, body1);
        assertNotNull(token);

        String tokenStr = token.getToken();
        assertNotNull(tokenStr);

        // Wait a bit
        Thread.sleep(10000);

        // After 10secs, they should be no new call to the service
        assertFalse(token.isExpired());
        
        String newTokenStr = token.getToken();
        assertEquals(tokenStr, newTokenStr);

    }

}
