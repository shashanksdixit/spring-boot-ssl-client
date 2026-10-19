# Spring Boot HTTPS Client — Custom Truststore Demonstration

## Overview

This project demonstrates how a Spring Boot REST application calls an external HTTPS API using either:

- **Default JVM trust:** Java's default trust configuration (typically the JDK's `cacerts`).
- **Custom trust:** A PKCS12 truststore supplied to Spring Boot through an external file path and applied only to a specific outbound `RestClient` using a named SSL Bundle.

The external service used for testing is **RESTful API** at `https://api.restful-api.dev`, with the example resource `GET /objects/7`.

The key objective is to show that a Spring Boot HTTP client can validate an external server's TLS certificate using an **application-specific truststore without importing that certificate into the JVM's default `cacerts`**.

> **Scope:** The Spring Boot application listens locally over **HTTP** on port `8080` and makes outbound **HTTPS** requests. You do **not** need to enable `server.ssl` or configure a Tomcat server keystore for this test.

## Request flow

```text
PowerShell / Browser
       |
       | HTTP GET http://localhost:8080/api/test/default
       |       or http://localhost:8080/api/test/custom
       v
Spring Boot REST Controller (port 8080, HTTP)
       |
       +-- /default --> RestClient with JVM default trust
       |
       +-- /custom  --> RestClient with Spring Boot SSL Bundle
       |                  (external PKCS12 truststore)
       |
       +---------------- HTTPS GET ---------------------->
                     https://api.restful-api.dev/objects/7
```

## Prerequisites

- **JDK 21 or 25**, matching the Java version configured in `pom.xml`.
- **Maven**, or the project's Maven Wrapper (`mvnw.cmd`).
- Internet access to `https://api.restful-api.dev`.
- A PKCS12 truststore containing certificate material that you have independently verified and chosen to trust for the external service.

Check your tools:

```powershell
java -version
mvn -version
```

The Spring Boot version is determined by the project's **`pom.xml`**. Do not assume a version from this README; use the one actually declared in the project.

## SSL configuration

The application uses a named Spring Boot SSL Bundle, **`restful-api`**, backed by an external PKCS12 truststore.

Example `src/main/resources/application.yml`:

```yaml
server:
  port: 8080
  ssl:
    enabled: false

spring:
  ssl:
    bundle:
      jks:
        restful-api:
          truststore:
            location: file:${SSL_TRUSTSTORE_PATH}
            password: ${SSL_TRUSTSTORE_PASSWORD}
            type: PKCS12
```

### Configuration parameters

| Parameter | Purpose | Example |
|---|---|---|
| `server.port` | Local HTTP port | `8080` |
| `server.ssl.enabled` | Keeps the embedded server on HTTP | `false` |
| `spring.ssl.bundle.jks.restful-api` | Name of the SSL Bundle used by the custom client | `restful-api` |
| `SSL_TRUSTSTORE_PATH` | Absolute path to the custom truststore | `<full path>/springssl/certificates/restful-api-dev-ts.p12` |
| `SSL_TRUSTSTORE_PASSWORD` | Password to open the truststore | Set locally; do not commit |
| `type` | Truststore format | `PKCS12` |

The SSL Bundle name in the configuration must match the name referenced by the custom `RestClient`:

```java
@Bean("customRestClient")
RestClient customRestClient(RestClient.Builder builder, RestClientSsl ssl) {
    return builder.clone()
            .baseUrl("https://api.restful-api.dev")
            .apply(ssl.fromBundle("restful-api"))
            .build();
}
```

The default client does **not** apply this SSL Bundle:

```java
@Bean("defaultRestClient")
RestClient defaultRestClient(RestClient.Builder builder) {
    return builder.clone()
            .baseUrl("https://api.restful-api.dev")
            .build();
}
```

Both clients request the same external path, `/objects/7`; only the trust configuration differs.

## Preparing the custom truststore

This section assumes you have already obtained the certificate for `api.restful-api.dev` or a suitable issuing CA certificate **and independently verified its authenticity**. Certificate chains and issuers may change; do not assume a fixed issuer or fingerprint.

1. Inspect the certificate:

   ```powershell
   keytool -printcert -v -file .\certificates\restful-api-dev.crt
   ```

2. If you are creating a new test truststore, import the verified certificate:

   ```powershell
   keytool -importcert `
     -alias restful-api-leaf `
     -file .\certificates\restful-api.dev.crt `
     -keystore .\certificates\restful-api-dev-ts.p12 `
     -storetype PKCS12
   ```

   `keytool` prompts for a new truststore password and certificate confirmation. Avoid using `-noprompt` until you have verified the certificate.

3. Inspect the resulting truststore:

   ```powershell
   keytool -list -v `
     -keystore .\certificates\restful-api-dev-ts.p12 `
     -storetype PKCS12
   ```

   Confirm the intended alias and `trustedCertEntry` are present.

**Leaf certificate vs CA:** Importing the current server (leaf) certificate creates direct trust in that certificate and can break when the service rotates certificates. Trusting a verified issuing CA is generally more maintainable, but broadens which certificates can chain to that trust anchor. In either case, Java must still validate the peer's hostname and applicable certificate constraints.

**Do not modify** `%JAVA_HOME%\lib\security\cacerts` for this experiment.

## Run in VS Code on Windows

1. Open the `springssl` project folder in VS Code.
2. Open **Terminal → New Terminal**, using PowerShell.
3. Navigate to the project directory:

   ```powershell
   cd springssl
   ```

4. Configure the external truststore **in the same terminal that starts Maven**:

   ```powershell
   $env:SSL_TRUSTSTORE_PATH = "<full path>/springssl/certificates/restful-api-dev-ts.p12"
   $env:SSL_TRUSTSTORE_PASSWORD = "<your-truststore-password>"
   ```

   Replace the password placeholder with your actual password. PowerShell environment variables set this way apply to the current terminal session and its child processes.

5. Confirm that the truststore exists:

   ```powershell
   Test-Path $env:SSL_TRUSTSTORE_PATH
   ```

   Expected: `True`.

6. Build the application:

   ```powershell
   .\mvnw.cmd clean package
   ```

   If the project does not have a Maven Wrapper, use `mvn clean package`.

7. Start the application:

   ```powershell
   .\mvnw.cmd spring-boot:run
   ```

   Or use `mvn spring-boot:run` when running with an installed Maven distribution.

8. Check the startup logs for an HTTP listener on port `8080`.

> If you launch the application using the VS Code **Run/Debug** button rather than this PowerShell terminal, configure the same environment variables in that launch configuration; environment variables set in an unrelated terminal may not be inherited.

## Test the endpoints

Open another PowerShell terminal while the Spring Boot application is running.

### 1. Default trust: `GET /api/test/default`

```powershell
curl.exe -i http://localhost:8080/api/test/default
```

**Behavior:** Uses the standard Java trust configuration. On a normal JDK installation, the public API's CA is often already trusted, so the HTTPS request should succeed. This path **does not use** the custom `restful-api` SSL Bundle.

Illustrative successful response (the API's content may change):

```json
{
  "status": "SUCCESS",
  "trustMode": "DEFAULT",
  "response": "{\"id\":\"7\",\"name\":\"Apple MacBook Pro 16\",...}"
}
```

### 2. Custom trust: `GET /api/test/custom`

```powershell
curl.exe -i http://localhost:8080/api/test/custom
```

**Behavior:** Uses the `RestClient` configured with the `restful-api` SSL Bundle and the external PKCS12 truststore. The call succeeds when Java can establish a valid TLS connection using that trust configuration, including a valid chain to trusted material and a matching hostname.

Illustrative successful response:

```json
{
  "status": "SUCCESS",
  "trustMode": "CUSTOM",
  "response": "{\"id\":\"7\",\"name\":\"Apple MacBook Pro 16\",...}"
}
```

The `response` field above is a **string containing the external API's JSON**, because the sample service calls `body(String.class)`.

### Default vs custom behavior

| Aspect | `/api/test/default` | `/api/test/custom` |
|---|---|---|
| Outbound URL | `https://api.restful-api.dev/objects/7` | Same |
| Client trust | JVM default | Named SSL Bundle `restful-api` |
| Reads external PKCS12 truststore | No | Yes |
| Requires modifying JVM `cacerts` | No | No |
| If the custom truststore has an unrelated CA | Usually unaffected | TLS validation should fail |
| If the public CA is already in JVM `cacerts` | Typically succeeds | Depends on custom truststore contents |

**Important:** The default call succeeding does not prove that the custom truststore is used. The comparison test below does.

## Prove that custom trust is isolated

1. First verify that `/api/test/custom` succeeds with the correctly populated truststore.
2. Stop the Spring Boot application.
3. Create a **separate test truststore** with an unrelated CA, or temporarily point `SSL_TRUSTSTORE_PATH` to an existing PKCS12 truststore that does not trust the API's certificate chain. Do not overwrite your working truststore.
4. Restart Spring Boot using the unrelated truststore.
5. Call both endpoints again.

Expected outcome, assuming the JVM default trust accepts the public API certificate:

| Endpoint | Expected |
|---|---|
| `/api/test/default` | `SUCCESS` |
| `/api/test/custom` | `FAILED` (typically HTTP 502 in the sample controller) |

The custom call commonly fails with a nested `SSLHandshakeException` / `PKIX path building failed` error. Restore the original truststore path and restart to recover.

This test demonstrates that the named SSL Bundle determines trust for the custom client independently of the default client.

## Troubleshooting

| Symptom | Likely cause / action |
|---|---|
| `Cannot obtain resource ... C:\Users\...\.keystore` | Embedded Tomcat is trying to enable inbound HTTPS. Ensure `server.ssl.enabled: false` and remove unintended server SSL settings. |
| `Test-Path` returns `False` | Fix `SSL_TRUSTSTORE_PATH`; use an absolute Windows path. |
| Truststore password / loading error | Confirm the PKCS12 password, file format, and environment variable. |
| `PKIX path building failed` | The active truststore cannot establish trust in the certificate presented by the external service. Check the certificate chain, issuer, and any TLS interception. |
| Hostname verification failure | The server certificate's SAN does not match `api.restful-api.dev`. |
| Default works, custom fails | Check the custom truststore contents, current server certificate, and SSL Bundle wiring. |
| Both fail | Check internet/proxy access, endpoint availability, DNS, TLS interception, and exception causes. |
| Port 8080 is in use | Stop the other application or change `server.port`. |

For detailed TLS diagnostics, you can temporarily start Maven with:

```powershell
$env:JAVA_TOOL_OPTIONS = "-Djavax.net.debug=ssl,handshake,trustmanager"
.\mvnw.cmd spring-boot:run
```

Remove the diagnostic setting when finished:

```powershell
Remove-Item Env:JAVA_TOOL_OPTIONS
```

TLS diagnostics can be verbose and may expose sensitive connection metadata; do not leave them enabled in production.

## Security notes

- Do not disable certificate validation or hostname verification to make HTTPS requests pass.
- Do not commit private keys, truststore passwords, or environment files containing secrets. Consider excluding `certificates/*.p12` from Git unless you have an explicit secure distribution policy.
- Verify certificates before trusting them; a certificate exported through an intercepting proxy might not be the origin server's certificate.
- Server certificates can rotate. Plan for certificate renewal and truststore maintenance.
- For production, configure HTTP timeouts, safe error handling, monitoring, and appropriate truststore distribution.

## Summary

This project demonstrates two outbound HTTPS trust models in the **same Spring Boot application**. The default client uses Java's standard trust configuration; the custom client uses an **external, application-specific PKCS12 truststore** selected through a Spring Boot SSL Bundle. The JVM's `cacerts` does not need to be modified.
