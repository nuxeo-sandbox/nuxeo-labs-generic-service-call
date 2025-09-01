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
import org.nuxeo.ecm.core.api.NuxeoException;

import nuxeo.labs.generic.service.call.AuthenticationToken;
import nuxeo.labs.generic.service.call.AuthenticationTokens;
import nuxeo.labs.generic.service.call.http.ServiceCall;
import nuxeo.labs.generic.service.call.http.ServiceCallResult;

/**
 *
 */
@Operation(id = CallServiceOp.ID, category = Constants.CAT_SERVICES, label = "Call a REST Service", description = "Call a service, returns the raw result."
        + " If tokenUuid is passed, it corresponds to a token fetched in a previous call (to Service.CallRESTServiceForToken) and it will be reused. If"
        + " expired, a new token will be automatically fetched. The 'Authentication: Bearer <the token>' header will then be added to the headers."
        + " If tokenUuid is not passed, then either the call is unauthenticated or you passed all the necessary info in the headers.")
public class CallServiceOp {

    public static final String ID = "Services.CallRESTService";

    @Context
    protected CoreSession session;
    
    @Param(name = "tokenUuid", required = false)
    protected String tokenUuid;

    // If not passed, we assume the headers have the authentication info
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
        
        ServiceCallResult result = null;
        
        ServiceCall serviceCall = new ServiceCall();
        Map<String, String> headers = ServiceCall.toHeadersMap(headersJsonStr);
        
        if(StringUtils.isNotBlank(tokenUuid)) {
            AuthenticationToken token = AuthenticationTokens.getInstance().getToken(tokenUuid);
            if(token == null) {
                // Token was not stored, most likely because it failed
                result = new ServiceCallResult("Invalid token. tokenUuid is valid, but the previous call failed.", -1, "Wring token");
                return Blobs.createJSONBlob(result.toJsonString());
            }
            String tokenStr = token.getToken();
            headers.put("Authorization", "Bearer " + tokenStr);
        }
        
        switch (httpMethod.toUpperCase()) {
        case "GET":
            result = serviceCall.get(url, headers);
            break;
            
        case "POST":
            result = serviceCall.post(url, headers, bodyStr);
            break;
            
        case "PUT":
            result = serviceCall.put(url, headers, bodyStr);
            break;
            
        default:
            throw new NuxeoException("Operation supports only GET/PUT or POST. Received <" + httpMethod + ">");
        }
        
        
        return Blobs.createJSONBlob(result.toJsonString());

    }
}
