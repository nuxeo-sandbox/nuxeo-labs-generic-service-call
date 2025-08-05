# nuxeo-labs-generic-service-call

This plugin calls a service and returns the result. Caller is in charge of using the correct HTTP verb (Support for GET/PUT/POST) and setting the correct payload when needed. As first implementation this plugin has some limitation (see below). But anyone is welcome to Pull request :-).

One of the goal of the plugin is to handle authentication token (typically for M2M communication) with an expiration time, so as long as a token is not expired, there is no need to request a new one.

The plugin is agnostic of URLs, client IDs, client secrets, etc. All these are the caller responsibility. Typically, these will be values set in nuxeo.conf, loaded before the call to the plugin. See example(s) below.

Once installed, usage of the pluygin is done via automation

### Typical Usage of Automation Operations

(See detailled examples below)

Typically, you will do the following:

1. Have nuxeo.conf parameters with your URLs, authentication info (like clientId/secret), etc.
2. Using these, call the `Services.CallRESTServiceForToken` to get a token information
3. Then, in all subsequent calls to a service requiring this token, you call `Services.CallRESTService`, passing the token UUID as a parameter. `Services.CallRESTService` wil use the token and ask for a new one if it is expired.

<br>

## Automation

* `Services.CallRESTServiceForToken`
* `Services.CallRESTService`
* `Services.DownloadFile`
* `Services.UploadFile`

### `Services.CallRESTServiceForToken`

Call a service for M2M token authentication purpose. Returns a JSON blobn with the values plus a specific `tokenUuid` property, to be used for next calls, and, so, avoid asking for a new token only when needed (when the token is expired)

* Input: `void`
* Output: `blob`, a JSON blob, result of the call (use its `getString()` method to get the JSON string)
* Parameters:
  * `httpMethod`,: String, required. The method to use. "GET", "POST" or "PUT" (case insensitive)
  * `url`: String, required. The URL to use for authentication
  * `headersJsonStr`: String, optional. A JSON string with the headers to use.
  * `bodyStr`: String, optional. The body to pass as is, if needed (for POST/PUT only)

The operation creates a Token object, stored in memory so it can be reused. The object stores the misc. info (method, url, etc.), so it can get a new token when needed. For subsequent calls, you just have to pass its `tokenUuid` field. This is what the plugin will use to load this token, check if it is expired and request a new one if needed (see examples below)

### `Services.CallRESTService`

Call a service and returns the result as JSON.

* Input: `void`
* Output: `blob`, a JSON blob, result of the call (use its `getString()` method to get the JSON string)
* Parameters:
  * `tokenUuid`: String, optional. The M2M Bearer token to use
  * `httpMethod`,: String, required. The method to use. "GET", "POST" or "PUT" (case insensitive)
  * `url`: String, required. The URL to use.
  * `headersJsonStr`: String, optional. A JSON string with the headers to use.
  * `bodyStr`: String, optional. The body to pass as is, if needed (for POST/PUT only)

The method calls the service at `url`, using the `httpMethod` and passing the headers (and optionaly the body).

If `tokenUuid` is passed, it corresponds to a token fetched in a previous call to `Services.CallRESTServiceForToken`) and it will be reused. If expired, a new token will be automatically fetched. If not passed, then either the call is unauthenticated, or you passed the expected values in the headers or the body.

When `tokenUuid` is passed, the operaiton adds the `Authentication: Bearer <the_token_value>` header.

See below the example(s) of use.

### `Services.DownloadFile`

Call a service to download a File and returns the corresponding Blob

* Input: `void`
* Output: `blob`, the downloaded blob, or `null` if an error occured
* Parameters
  * `tokenUuid`: String, optional. The M2M Bearer token to use
  * `url`: String, required. The URL to use.
  * `headersJsonStr`: String, optional. A JSON string with the headers to use.

The method calls the service at `url`, and downloadq the corresponding file, encapsulating to a regular `Blob`

### `Services.UploadFile`

Call a service to upload a File, using "POST" or "PUT"

* Input: `blob` or `document`
* Output: `blob`, a JSON blob, result of the call (use its `getString()` method to get the JSON string)
* Parameters
  * `tokenUuid`: String, optional. The M2M Bearer token to use
  * `httpMethod`,: String, required. The method to use. "POST" or "PUT" (case insensitive)
  * `url`: String, required. The URL to use.
  * `headersJsonStr`: String, optional. A JSON string with the headers to use.
  * `xpath`: String, optional. The XPATH to use when the input is `document`. Default is the main blob, at `file:content`.

The method calls the service at `url`, and uploads the file backed by the blob.


<br>

## Examples of Use

### Get a Token and Call the Service Several Times

We assume nuxeo.conf has custom parameters for the misc. required information. For example:

```
callservice.auth.url=the URL for authentication
callservice.clientId=. . .
callservice.clientSecret=. . .
callservice.baseUrl=. . .
```

```javascript
function run(input, params) {
  
  var authUrl, baseUrl, clientId, clientSecret, authStr, base64auth, headersJson,
      resultBlob, resultJson, tokenInfoJson, tokenId, bodyJson;
  
  // ====================> Read values from nuxeo.conf
  authUrl = Env["callservice.auth.url"];
  clientId = Env["callservice.clientId"];
  clientSecret = Env["callservice.clientSecret"];
  baseUrl = Env["callservice.baseUrl"];
  
  // Using Java in JS for Base64 encoding
  // (Automation Scripting uses ECMAScript 5, nobtoa() or atob())
  // See https://doc.nuxeo.com/nxdoc/automation-scripting/#java-objects-in-automation-scripting
  // In this example, we added an XML Extension:
  /* <extension target="org.nuxeo.automation.scripting.internals.AutomationScriptingComponent" point="classFilter">
        <classFilter>
          <allow>java.util.Base64</allow>
        </classFilter>
      </extension>
  */
  authStr = clientId + ":" + clientSecret;
  base64auth = java.util.Base64.getEncoder().encodeToString(authStr.getBytes());
  headersJson = {
    "Content-Type": "application/json",
    "Authorization": base64auth
  };
  
  // ====================> Get a token
  resultBlob = Services.CallRESTServiceForToken(
    null, {
      "httpMethod": "GET",
      "url": authUrl,
      "headersJsonStr": JSON.stringify(headersJson)
      // No bodyStr in this example with GET
    });
  
  tokenInfoJson = JSON.parse(resultBlob.getString());
  tokenId = tokenInfoJson.tokenUuid;
  
  // ====================> Call service #1
  headersJson = {
    "Content-Type": "application/json"
  };
  resultBlob = Services.CallRESTService(
    null, {
      "tokenUuid": tokenId,
      "httpMethod": "GET",
      "url": baseUrl + "/someEndpointWithGET",
      "headersJsonStr": JSON.stringify(headersJson)
      // No bodyStr in this example with GET
    });
  resultJson = JSON.parse(resultBlob.getString());
  if(resultJson.responseCode === 200) {
    // . . . do something with the result . . .
  }
  
  // ====================> Call service #2, POST
  headersJson = {
    "Content-Type": "application/json"
  };
  bodyJson = {
    "title": input.title,
    "created": input["dc:created"]
  };
  resultBlob = Services.CallRESTService(
    null, {
      "tokenUuid": tokenId,
      "httpMethod": "POST",
      "url": baseUrl + "/someEndpointWithPOST",
      "headersJsonStr": JSON.stringify(headersJson),
      "bodyStr": JSON.stringify(bodyJson)
    });
  resultJson = JSON.parse(resultBlob.getString());
  if(resultJson.responseCode === 200) {
    // . . . do something with the result . . .
  }
  
  // ====================> Download a file
  var downloaded = Services.DownloadFile(
    null, {
      "tokenUuid": tokenId,
      "url": baseUrl + "/someEndpoint/For/A/File/T/DoDownload"
      // "headersJsonStr": JSON.stringify(headersJson) no headers here. Plugin will had whats relevant
    });
  // . . . do something with the downloaded blob . . .
  
  // ====================> Upload a file, with POST, from custom field myschema:myfile of the input
  resultBlob = Services.UploadFile(
    null, {
      "tokenUuid": tokenId,
      "httpMethod": "POST",
      "url": baseUrl + "/someEndpointWithPOST",
      "headersJsonStr": JSON.stringify(headersJson),
      "xpath": "myschema:myfile"
    });
  resultJson = JSON.parse(resultBlob.getString());
  if(resultJson.responseCode === 200) {
    // . . . continue . . .
  }
  
  // ====================> ...etc...

}
```


<br>

## Installation/Deployment
The plug is available in the [Public Nuxeo MarketPlace](https://connect.nuxeo.com/nuxeo/site/marketplace/package/nuxeo-labs-generic-service-call) and can be added as a dependency to a Nuxeo Studio project, or installed with Docker (added to `NUXEO_PACKAGES`), or installed via:

```
nuxeoctl mp-install nuxeo-labs-generic-service-call
```

<br>

## How to build
```bash
git clone https://github.com/nuxeo-sandbox/nuxeo-labs-generic-service-call
cd nuxeo-labs-generic-service-call
# Example of build with no unit test
mvn clean install -DskipTests
```

<br>

## How to UnitTest

Most Unit Tests use `okhttp3.mockwebserver.MockWebServer` to mock a webserver.

A couple others can call a real service if you want to also test your service. Fior these, plese see comment in `TestCallService.java`. Basically, it is all about having environement variables set (for the authenticaiton URL, headers, etc.). If these variables are not set, the test is ignored.

## Support
**These features are not part of the Nuxeo Production platform.**

These solutions are provided for inspiration and we encourage customers to use them as code samples and learning
resources.

This is a moving project (no API maintenance, no deprecation process, etc.) If any of these solutions are found to be
useful for the Nuxeo Platform in general, they will be integrated directly into platform, not maintained here.

<br>

## License
[Apache License, Version 2.0](http://www.apache.org/licenses/LICENSE-2.0.html)

<br>

## About Nuxeo
[Nuxeo Platform](https://www.hyland.com/solutions/products/nuxeo-platform) is an open source Content Services platform, written in Java. Data can be stored in both NoSQL & SQL
databases.

The development of the Nuxeo Platform is mostly done by Nuxeo employees with an open development model.

The source code, documentation, roadmap, issue tracker, testing, benchmarks are all public.

Typically, Nuxeo users build different types of information management solutions
for [document management](https://www.hyland.com/platform/content-management), 
and [digital asset management](https://www.hyland.com/platform/digital-asset-management), ....

It uses
schema-flexible metadata & content models that allows content to be repurposed to fulfill future use cases.

More information is available at [www.nuxeo.com](https://www.hyland.com/solutions/products/nuxeo-platform).


