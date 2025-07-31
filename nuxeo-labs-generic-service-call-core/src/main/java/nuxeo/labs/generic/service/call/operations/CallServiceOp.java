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
@Operation(id = CallServiceOp.ID, category = Constants.CAT_DOCUMENT, label = "Call a REST Service", description = "Call a service, returns the raw result.")
public class CallServiceOp {

    public static final String ID = "Service.CallRESTService";

    @Context
    protected CoreSession session;
    @Param(name = "tokenUuid", required = false)
    protected String tokenUuid;

    // If not passed, we assume the headers have the authentication info
    @Param(name = "method", required = true)
    protected String method;

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
            String tokenStr = token.getToken();
            headers.put("Authentication", "Bearer " + tokenStr);
        }
        
        switch (method.toLowerCase()) {
        case "get":
            result = serviceCall.get(url, headers);
            break;
            
        case "post":
            result = serviceCall.post(url, headers, bodyStr);
            break;
            
        case "put":
            result = serviceCall.put(url, headers, bodyStr);
            break;
            
        default:
            throw new NuxeoException("Operatoon supports only GET/PUT or POST. Received <" + method + ">");
        }
        
        
        return Blobs.createJSONBlob(result.toJsonString());

    }
}
