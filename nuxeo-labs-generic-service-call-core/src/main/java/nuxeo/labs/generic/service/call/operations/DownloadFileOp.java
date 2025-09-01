package nuxeo.labs.generic.service.call.operations;

import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.nuxeo.ecm.automation.core.Constants;
import org.nuxeo.ecm.automation.core.annotations.Context;
import org.nuxeo.ecm.automation.core.annotations.Operation;
import org.nuxeo.ecm.automation.core.annotations.OperationMethod;
import org.nuxeo.ecm.automation.core.annotations.Param;
import org.nuxeo.ecm.core.api.Blob;
import org.nuxeo.ecm.core.api.CoreSession;

import nuxeo.labs.generic.service.call.AuthenticationToken;
import nuxeo.labs.generic.service.call.AuthenticationTokens;
import nuxeo.labs.generic.service.call.http.ServiceCall;
import nuxeo.labs.generic.service.call.http.ServiceCallResult;

/**
 *
 */
@Operation(id = DownloadFileOp.ID, category = Constants.CAT_SERVICES, label = "Download a file from a REST service", description = "Download a file to a REST service."
        + " httpMethod must be either POST or PUT. If POST, multipart/chincks upload is handled."
        + " If tokenUuid is passed, it corresponds to a token fetched in a previous call (to Service.CallRESTServiceForToken) and it will be reused. If"
        + " expired, a new token will be automatically fetched. The 'Authentication: Bearer <the token>' header will then be added to the headers."
        + " If tokenUuid is not passed, then either the call is unauthenticated or you passed all the necessary info in the headers.")
public class DownloadFileOp {

    public static final String ID = "Services.DownloadFile";
    
    private static final Logger log = LogManager.getLogger(DownloadFileOp.class);

    @Context
    protected CoreSession session;
    
    @Param(name = "tokenUuid", required = false)
    protected String tokenUuid;

    @Param(name = "url", required = true)
    protected String url;

    @Param(name = "headersJsonStr", required = false)
    protected String headersJsonStr;
    
    @OperationMethod
    public Blob run() {
        
        ServiceCallResult result = null;
        
        ServiceCall serviceCall = new ServiceCall();
        Map<String, String> headers = ServiceCall.toHeadersMap(headersJsonStr);
        
        if(StringUtils.isNotBlank(tokenUuid)) {
            AuthenticationToken token = AuthenticationTokens.getInstance().getToken(tokenUuid);
            String tokenStr = token.getToken();
            headers.put("Authorization", "Bearer " + tokenStr);
        }
        
        result = serviceCall.downloadFile(url, headers);
        if(result.getResponseBlob() == null) {
            try {
                log.error("Error downloading the file: " + result.toJsonString(0));
            } catch(Exception e) {
                // Ignore
            }
        }
        
        return result.getResponseBlob();
    }
}
