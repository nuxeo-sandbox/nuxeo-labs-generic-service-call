package nuxeo.labs.generic.service.call.operations;

import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.nuxeo.ecm.automation.core.Constants;
import org.nuxeo.ecm.automation.core.annotations.Context;
import org.nuxeo.ecm.automation.core.annotations.Operation;
import org.nuxeo.ecm.automation.core.annotations.OperationMethod;
import org.nuxeo.ecm.automation.core.annotations.Param;
import org.nuxeo.ecm.core.api.Blob;
import org.nuxeo.ecm.core.api.Blobs;
import org.nuxeo.ecm.core.api.CoreSession;

import nuxeo.labs.generic.service.call.AuthenticationToken;
import nuxeo.labs.generic.service.call.AuthenticationTokens;
import nuxeo.labs.generic.service.call.http.ServiceCall;

/**
 * Notice the same can be achieved with CallServiceOp, but here we explictely now it's a bout getting a token, so
 * we return the token ID.
 */
@Operation(id = CallServiceForTokenOp.ID, category = Constants.CAT_SERVICES, label = "Call a REST Service", description = "Call a service, returns the raw JSON result."
        + " The method is required and you pass in headersJsonStr all the required headers, and in bodyStr the raw body (for POST/PUT calls)."
        + " The operation calls the service and, so, gets a token."
        + " The operation returns a JSON blob (call its getString() method) containing the tokenUuid property in addition to the usual JSON return "
        + " containing the token (with access_token, expires_in, etc.) and the responseCode and responseMessage from the service."
        + " Other operations expect only the tokenUuid.")
public class CallServiceForTokenOp {

    public static final String ID = "Services.CallRESTServiceForToken";

    @Context
    protected CoreSession session;

    @Param(name = "httpMethod", required = true)
    protected String httpMethod;

    @Param(name = "url", required = true)
    protected String url;

    @Param(name = "headersJsonStr", required = false)
    protected String headersJsonStr;

    @Param(name = "bodyStr", required = false)
    protected String bodyStr;

    @OperationMethod
    public Blob run() {
        
        Map<String, String> headers = ServiceCall.toHeadersMap(headersJsonStr);
        
        AuthenticationToken token = AuthenticationTokens.getInstance().newToken(httpMethod, url, headers, bodyStr);
        // Call the service so we see if things work
        @SuppressWarnings("unused")
        String tokenValue = token.getToken();
        
        // If the call failed, no need to store it in memory
        if(StringUtils.isBlank(tokenValue)) {
            AuthenticationTokens.getInstance().removeToken(token.getId());
        }
        
        return Blobs.createJSONBlob(token.tokenToJSONObject().toString());

    }
}
