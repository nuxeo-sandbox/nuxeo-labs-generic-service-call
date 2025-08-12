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
package nuxeo.labs.generic.service.call;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.JSONObject;
import org.nuxeo.ecm.core.api.NuxeoException;

import nuxeo.labs.generic.service.call.http.ServiceCall;
import nuxeo.labs.generic.service.call.http.ServiceCallResult;

/**
 * This class handles authentication tokens and their lifespan. If a token was requested before expiration, it is
 * returned as is. Else, a new token is fetched.
 * 
 * @since 2023
 */
public class AuthenticationToken {

    private static final Logger log = LogManager.getLogger(AuthenticationToken.class);

    protected String id;

    protected String httpMethod;

    protected String token = null;

    protected Instant tokenExpiration = null;

    protected String authFullUrl;

    protected Map<String, String> headers;

    protected String body;
    
    protected JSONObject serviceResponse;

    protected ServiceCall serviceCall = new ServiceCall();
    
    protected ServiceCallResult lastServiceCallresult = null;

    public AuthenticationToken(String httpMethod, String authFullUrl, Map<String, String> headers, String body) {

        id = UUID.randomUUID().toString();

        this.httpMethod = httpMethod.toUpperCase();
        this.authFullUrl = authFullUrl;
        this.headers = headers;
        this.body = body;

    }

    public String getId() {
        return id;
    }

    /**
     * Will fetch a new token only if the current token is null or expired.
     * 
     * @param url, the full authentication URL
     * @param clientId
     * @param clientSecret
     * @return the authentication token
     * @since 2023
     */
    public String getToken() {

        if (!isExpired()) {
            return token;
        }

        switch (httpMethod) {
        case "GET":
            lastServiceCallresult = serviceCall.get(authFullUrl, headers);
            break;
            
        case "POST":
            lastServiceCallresult = serviceCall.post(authFullUrl, headers, body);
            break;

        case "PUT":
            lastServiceCallresult = serviceCall.put(authFullUrl, headers, body);
            break;

        default:
            throw new NuxeoException("Invalid HTTP method (<" + httpMethod + ">: We support only GET/POST/PUSH.");
        }

        if (lastServiceCallresult.callWasSuccesful()) {
            serviceResponse = lastServiceCallresult.getResponseAsJSONObject();
            // {"error":"invalid_grant","error_description":"Caller not authorized for requested resource"}
            if (serviceResponse.has("error")) {
                String msg = "Getting a token failed with error " + serviceResponse.getString("error") + ".";
                if (serviceResponse.has("error_description")) {
                    msg += " " + serviceResponse.getString("error_description");
                }
                log.error(msg);
                token = null;
                serviceResponse = null;
            } else {
                token = serviceResponse.getString("access_token");
                int expiresIn = serviceResponse.getInt("expires_in");
                tokenExpiration = Instant.now().plusSeconds(expiresIn - 15);
            }
        } else {
            log.error("Error getting an auth token:\n" + lastServiceCallresult.toJsonString(2));
            token = null;
            serviceResponse = null;
        }

        return token;

    }

    public boolean isExpired() {
        if (StringUtils.isNotBlank(token) && !Instant.now().isAfter(tokenExpiration)) {
            return false;
        }

        return true;
    }

    // Mainly used for moking in unit tests
    public void setToken(String value) {
        token = value;
    }

    public void setTokenExpiration(int inNSeconds) {
        tokenExpiration = Instant.now().plusSeconds(inNSeconds);
    }
    
    public JSONObject tokenToJSONObject() {
        
        JSONObject obj = null;
        if(serviceResponse != null) {
            obj = new JSONObject(serviceResponse.toString());
            obj.put("responseCode", lastServiceCallresult.getResponseCode());
            obj.put("responseMessage", lastServiceCallresult.getResponseMessage());
        } else {
            //obj = new JSONObject();
            obj = lastServiceCallresult.toJsonObject();
        }
        obj.put("tokenUuid", id);
        
        return obj;
    }

}
