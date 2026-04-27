# Nuxeo Labs Generic Service Call — Agent Guide

## Project

Nuxeo LTS 2025 plugin (Java 21, Maven) providing Automation operations to call external REST services, handle authentication tokens (with expiration/reuse), upload blobs, and download files. Developed by the Nuxeo Solution Engineering team.

- **Parent**: `org.nuxeo:nuxeo-parent:2025.16`
- **GroupId**: `nuxeo.labs.generic.service.call`
- **Version**: `2025.1.0-SNAPSHOT`
- **Target Nuxeo version**: `2025.*`

## Modules

| Module | Purpose |
|--------|---------|
| `nuxeo-labs-generic-service-call-core` | Automation operations + HTTP utility classes |
| `nuxeo-labs-generic-service-call-package` | Nuxeo Marketplace package |

Key paths in the core module:

- Java sources: `nuxeo-labs-generic-service-call-core/src/main/java/nuxeo/labs/generic/service/call/`
- Component XMLs: `nuxeo-labs-generic-service-call-core/src/main/resources/OSGI-INF/`
- Bundle manifest: `nuxeo-labs-generic-service-call-core/src/main/resources/META-INF/MANIFEST.MF`
- Tests: `nuxeo-labs-generic-service-call-core/src/test/java/nuxeo/labs/generic/service/call/test/`

## Build & Test Commands

```bash
# Full build (all modules)
mvn clean install

# Build skipping tests
mvn clean install -DskipTests

# Run tests only in the core module
mvn test -pl nuxeo-labs-generic-service-call-core

# Run a single test class
mvn test -pl nuxeo-labs-generic-service-call-core -Dtest=TestOperations
```

No CI workflows, no linter, no formatter configured in this repo. `nuxeo.skip.enforcer=true` is set.

## Current Code

### `nuxeo-labs-generic-service-call-core` (`Bundle-SymbolicName: nuxeo.labs.generic.service.call.nuxeo-labs-generic-service-call-core`)

**Operations:**

- **`CallServiceOp`** (`@Operation id = "Services.CallRESTService"`) — Calls an external REST service via GET, POST, or PUT. Optionally uses a bearer token (via `tokenUuid`). Accepts headers as a JSON string, returns a JSON blob with `responseCode`, `responseMessage`, and `response`.
- **`CallServiceForTokenOp`** (`@Operation id = "Services.CallRESTServiceForToken"`) — Authenticates against a token endpoint, stores the resulting `AuthenticationToken` in the in-memory `AuthenticationTokens` map, and returns the JSON result including a `tokenUuid` for reuse in subsequent calls.
- **`UploadFileOp`** (`@Operation id = "Services.UploadFile"`) — Uploads a blob (from direct input or from a document's xpath property) to a remote URL via PUT or POST. Optionally authenticates with a bearer token.
- **`DownloadFileOp`** (`@Operation id = "Services.DownloadFile"`) — Downloads a remote file via HTTP GET. Optionally authenticates with a bearer token. Returns a `Blob`.

**Utility classes:**

- **`ServiceCall`** — Central HTTP utility class. Methods: `get`, `post`, `put`, `uploadBlob`, `uploadFile`, `downloadFile`, `readResponse`, `extractFileName`. Uses `HttpURLConnection` for simple calls and `java.net.http.HttpClient` for file uploads.
- **`ServiceCallResult`** — Wraps an HTTP response: `responseCode`, `responseMessage`, `response` (String body), `responseBlob` (for downloads). Provides `toJsonObject()`, `getResponseAsJSONObject()`, `getResponseAsJSONArray()`, etc.
- **`AuthenticationToken`** — Manages a single authentication token with expiration. Automatically refreshes if expired when `getToken()` is called.
- **`AuthenticationTokens`** — Singleton in-memory map of `AuthenticationToken` instances keyed by UUID. Shared across requests to enable token reuse.

**OSGI-INF contributions:**

- `operations-contrib.xml` — Registers all 4 operations against `org.nuxeo.ecm.core.operation.OperationServiceComponent`.

## Adding New Code

### New Automation Operation

1. Create the Java class annotated with `@Operation(id = "...")` in `nuxeo.labs.generic.service.call.operations`, containing `@OperationMethod`
2. Register it in `OSGI-INF/operations-contrib.xml` via `<extension target="org.nuxeo.ecm.core.operation.OperationServiceComponent" point="operations">`
3. `MANIFEST.MF` **must end with a trailing newline** or the last `Nuxeo-Component` entry is silently ignored

### Dependencies

The core POM declares dependencies **without `<version>` tags** — versions are managed by `nuxeo-parent`. Key compile dependencies:

- `nuxeo-automation-core` — `@Operation`, `@OperationMethod`, `@Param`, etc.
- `nuxeo-automation-test` (test scope) — `AutomationFeature`, `FeaturesRunner`
- `com.squareup.okhttp3:mockwebserver:4.12.0` (test scope) — mock HTTP server for unit tests

## Critical Pitfalls

These will cause silent failures or build errors if ignored:

- **NOT Spring**: No `@Autowired`, `@Component`, `@Service`. Use Nuxeo's component model (`DefaultComponent`, `Framework.getService()`)
- **Jakarta, not javax**: This is LTS 2025 (Java 21 era) — EE imports use `jakarta.*`, **not** `javax.*`
- **JUnit 4 only**: Use `@RunWith(FeaturesRunner.class)` + `@Features(...)` + `@Deploy(...)`. No JUnit 5
- **Log4j2 only**: `LogManager.getLogger(MyClass.class)`. No SLF4J, no JCL, no `System.out.println`
- **No raw Jackson for REST**: Use Nuxeo's `MarshallerRegistry` framework
- **No JPA**: Document storage uses `CoreSession` / `DocumentModel` API
- **MANIFEST.MF trailing newline**: The file must end with a newline or the last `Nuxeo-Component` entry is silently ignored
- **Token map is never cleaned**: `AuthenticationTokens` holds tokens for the JVM lifetime. Expired tokens are refreshed on next access, but removed entries are only those explicitly invalidated via `removeToken()`.

## Testing Patterns

```java
@RunWith(FeaturesRunner.class)
@Features(AutomationFeature.class)
@Deploy("nuxeo.labs.generic.service.call.nuxeo-labs-generic-service-call-core")
public class TestOperations {

    @Inject
    protected CoreSession session;

    @Inject
    protected AutomationService automationService;

    @Test
    public void testSomething() throws Exception {
        // Use MockWebServer to simulate HTTP endpoints
        MockWebServer server = new MockWebServer();
        server.enqueue(new MockResponse().setBody("{\"access_token\":\"abc\"}").setResponseCode(200));
        server.start();
        // ...
        server.shutdown();
    }
}
```

- `@Deploy("bundle.symbolic.name")` — the symbolic name is in `MANIFEST.MF` (`Bundle-SymbolicName`)
- `MockWebServer` (OkHttp) is used throughout to avoid real network calls in tests
- Some tests (`shouldGetAToken`) are `@Ignore`'d and require real service env vars:
  - `TEST_CallService_AUTH_URL`, `TEST_CallService_AUTH_HEADERS_JSON`, `TEST_CallService_AUTH_BODY_1`, `TEST_CallService_AUTH_BODY_2`
- Download tests (`testQuickRealDownload`) require: `SERVICECALL_TEST_DOWNLOAD_URL`, `SERVICECALL_TEST_DOWNLOAD_FILENAME`, `SERVICECALL_TEST_DOWNLOAD_MIMETYPE`, `SERVICECALL_TEST_DOWNLOAD_AUTH_HEADER`

## Local References

The Nuxeo LTS 2025 source code is available locally at `~/GitHub/nuxeo/nuxeo-sources-lts-2025`. Prefer reading local files over network fetches.

## Code Style

### Java

- 4-space indent, **no tabs**, no trailing spaces, K&R braces, ~120 char lines
- Use modern Java: `var`, records, pattern matching `instanceof`, switch expressions, text blocks, `String.formatted()`
- **No wildcard imports**. Import order: static, `java.*`, `jakarta.*`, `org.*`, `com.*`
- Always use braces for `if`/`else` blocks (even single-line)
- No `final` on method parameters or local variables
- No `private` methods/fields (use `protected`); exceptions: `log` and `serialVersionUID`
- No `final` classes or methods (hinders extensibility)
- Use `i++` (not `++i`) for simple increments
- Use `Objects.requireNonNull` for null checks
- Logging: parameterized messages `log.debug("Processing: {}", docId)`, use `if (log.isDebugEnabled())` for non-constant messages
- Javadoc first sentence: 3rd person verb phrase ending with period (*Gets the foobar.* not *Get the foobar*)
- `@since 2025.XX` on new public API. No `@author` tag
- License header: Apache 2.0 with current year and `Contributors:` section

### XML (OSGI-INF, POMs)

- **2-space indent**, no tabs, 120 char line width
- Self-closing tags: add space before `/>` (e.g., `<property name="foo" />`)

### Markdown (README, docs)

- Use GitHub alert syntax for notes, warnings, tips, etc.:
  ```
  > [!NOTE]
  > Content here

  > [!WARNING]
  > Content here
  ```

## Release Process

> [!WARNING]
> Check the repository is clean before starting. Alert and stop if there are uncommitted changes.

> [!IMPORTANT]
> The version numbers below are examples only. Always read the actual current version from the POM before running any command, and derive the release and next snapshot versions from it (e.g. `2025.1.0-SNAPSHOT` → release `2025.1.0` → next snapshot `2025.2.0-SNAPSHOT`).

1. Remove `-SNAPSHOT` from the current version: `mvn versions:set -DnewVersion=<current-version-without-SNAPSHOT> -DgenerateBackupPoms=false`
2. Build: `mvn clean install -DskipTests`
3. Copy `nuxeo-labs-generic-service-call-package/target/nuxeo-labs-generic-service-call-package-<version>.zip` to `~/Downloads/`
4. Bump to next snapshot (increment minor, add `-SNAPSHOT`): `mvn versions:set -DnewVersion=<next-version>-SNAPSHOT -DgenerateBackupPoms=false`
5. Verify: `mvn clean install -DskipTests`
6. Commit and push:
   ```bash
   git add .
   git commit -m "Post <version> release"
   git push
   ```

> [!NOTE]
> No git tag or GitHub release is created. The ZIP copied to `~/Downloads` is the deliverable.
