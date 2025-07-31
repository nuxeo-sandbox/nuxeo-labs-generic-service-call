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

import java.util.HashMap;
import java.util.Map;

/**
 * Class to use so we try to reuse token instead of always asking for a new one.
 * <br>
 * NOTE: As a token is a mix of url + headers + body, we don't expect hundreds of them. Not even dozens
 * => The HasMap is never cleaned up. When a caller references a token, if it is expired it will fetch a new value.
 * 
 * @since LTS2023
 */
public class AuthenticationTokens {

    protected static HashMap<String, AuthenticationToken> tokens = new HashMap<String, AuthenticationToken>();

    protected static final AuthenticationTokens instance = new AuthenticationTokens();

    private AuthenticationTokens() {

    }

    public static AuthenticationTokens getInstance() {
        return instance;
    }

    public AuthenticationToken getToken(String tokenUUID) {

        return tokens.get(tokenUUID);
    }

    public AuthenticationToken newToken(String authFullUrl, Map<String, String> headers, String body) {

        AuthenticationToken token = new AuthenticationToken(authFullUrl, headers, body);
        tokens.put(token.getId(), token);

        return token;
    }
    
    public void removeToken(String tokenUUID) {
        tokens.remove(tokenUUID);
    }

    public int size() {
        return tokens.size();
    }

}
