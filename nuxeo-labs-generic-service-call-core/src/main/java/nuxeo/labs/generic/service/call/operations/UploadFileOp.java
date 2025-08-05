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
import org.nuxeo.ecm.core.api.DocumentModel;

import nuxeo.labs.generic.service.call.AuthenticationToken;
import nuxeo.labs.generic.service.call.AuthenticationTokens;
import nuxeo.labs.generic.service.call.http.ServiceCall;
import nuxeo.labs.generic.service.call.http.ServiceCallResult;

/**
 *
 */
@Operation(id = UploadFileOp.ID, category = Constants.CAT_SERVICES, label = "Upload a file to a REST service", description = "Upload a file to a REST service."
        + " If input is a document, xpath parameter is used to get the blob (default file:content). input can also be a Blob."
        + " httpMethod must be either POST or PUT. If POST, multipart/chincks upload is handled."
        + " If tokenUuid is passed, it corresponds to a token fetched in a previous call (to Service.CallRESTServiceForToken) and it will be reused. If"
        + " expired, a new token will be automatically fetched. The 'Authentication: Bearer <the token>' header will then be added to the headers."
        + " If tokenUuid is not passed, then either the call is unauthenticated or you passed all the necessary info in the headers.")
public class UploadFileOp {

    public static final String ID = "Services.UploadFile";

    @Context
    protected CoreSession session;
    
    @Param(name = "tokenUuid", required = false)
    protected String tokenUuid;

    @Param(name = "httpMethod", required = true)
    protected String httpMethod;

    @Param(name = "url", required = true)
    protected String url;

    @Param(name = "headersJsonStr", required = false)
    protected String headersJsonStr;
    
    @Param(name = "xpath", required = false)
    protected String xpath = "file:content";

    @OperationMethod
    public Blob run(DocumentModel doc) {
        
        Blob blob = (Blob) doc.getPropertyValue(xpath);
        
        return run(blob);

    }
    
    @OperationMethod
    public Blob run(Blob blob) {
        
        ServiceCallResult result = null;
        
        ServiceCall serviceCall = new ServiceCall();
        Map<String, String> headers = ServiceCall.toHeadersMap(headersJsonStr);
        
        if(StringUtils.isNotBlank(tokenUuid)) {
            AuthenticationToken token = AuthenticationTokens.getInstance().getToken(tokenUuid);
            String tokenStr = token.getToken();
            headers.put("Authentication", "Bearer " + tokenStr);
        }
        
        result = serviceCall.uploadBlob(httpMethod, blob, url, headers);
        
        return Blobs.createJSONBlob(result.toJsonString());
    }
}
