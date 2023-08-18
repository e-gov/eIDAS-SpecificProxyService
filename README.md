<img src='doc/img/eu_regional_development_fund_horizontal.jpg'>

# EE specific eIDAS proxy service

- [1. Building the SpecifcProxyService webapp](#build)
- [2. Integration with EidasNode webapp](#integrate_with_eidasnode)
  * [2.1. Configuring communication with EidasNode](#integrate_eidasnode)
  * [2.2. Ignite configuration](#ignite_conf)
- [3. Integration with the IDP (OpenID Connect server)](#integrate_with_idp)
  * [3.1. Requirements for IDP](#idp_requirements)
  * [3.2. Requesting eIDAS attributes using OpenID Connect scopes](#idp_request)
  * [3.3. Fetching eIDAS attributes from OpenID Connect ID Token](#idp_response)
- [4. Logging](#logging)
  * [4.1. Log configuration](#log_conf)
  * [4.2. Log file and format](#log_file)
- [6. Monitoring](#heartbeat)
- [7. Appendix 1 - service configuration parameters](#configuration_parameters)
  * [7.1 Identity provider (OpenID Connect provider)](#configuration_parameters_idp)
  * [7.2 Integration with the EidasNode webapp](#configuration_parameters_eidas)
  * [7.3 User consent](#configuration_parameters_consent)
  * [7.4 Supported service provider types](#configuration_parameters_sptype)
  * [7.5 Mapping eIDAS attributes to OpenID Connect authentication request scopes](#configuration_parameters_oidc)
  * [7.6 Mapping eIDAS attributes to OpenID Connect ID-token claims](#configuration_claims_oidc)
  * [7.8 Postprocessing OpenID Connect ID-token claim values](#configuration_claims_postprocessing)
  * [7.9 HTTPS truststore](#truststore)
  * [7.9 HTTP security](#http_security)

<a name="build"></a>
## 1. Building the SpecifcProxyService webapp

First, make sure you have built [eIDAS-Node](https://ec.europa.eu/digital-building-blocks/wikis/display/DIGITAL/eIDAS-Node+version+2.6) artifacts and installed these to local Maven repository:
```
cd EIDAS-Parent && mvn -DskipTests clean install -P NodeOnly,DemoToolsOnly -PnodeJcacheIgnite,specificCommunicationJcacheIgnite
```

Then execute the following command:
````
./mvnw clean package
````

<a name="integrate_with_eidasnode"></a>
## 2. Integration with EidasNode webapp

In order to enable communication between `EidasNode` and `SpecificProxyService` webapps, both must be able to access the same `Ignite` cluster and have the same communication configuration (shared secret, etc).

**NB!** It is assumed that the `SpecificProxyService` webapp is installed in the same web server instance as `EidasNode` and that both have access to the same configuration files.

<a name="integrate_eidasnode"></a>
### 2.1 Configuring communication with EidasNode

To set the same communication definitions, it is required that the `SpecificProxyService` has access to communication definitions provided in the following `EidasNode` configuration file:
`$SPECIFIC_PROXY_SERVICE_CONFIG_REPOSITORY/specificCommunicationDefinitionProxyservice.xml`

<a name="ignite_conf"></a>
### 2.2 Ignite configuration

By default it is assumed that `EidasNode` and `SpecificProxyService` will share the same xml configuration file and that the Ignite configuration can be found at `$EIDAS_CONFIG_REPOSITORY/igniteSpecificCommunication.xml`. The configuration location can be overridden (see to [configuration parameters](#configuration_parameters_eidas) for further details).

The `SpecificProxyService` webapp starts an Ignite node in client mode using EidasNode webapp's Ignite configuration. The ignite client is started lazily (initialized on the first query).

Note that `SpecificProxyService` requires access to four predefined maps in the cluster - see Table 1 for details.

| Map name        |  Description |
| :---------------- | :---------- |
| `nodeSpecificProxyserviceRequestCache` | Holds pending LightRequests from EidasNode webapp. |
| `specificNodeProxyserviceResponseCache` | Holds LightResponses for EidasNode webapp. |
| `specificMSIdpRequestCorrelationMap` | Holds pending IDP authentication requests. |
| `specificMSIdpConsentCorrelationMap` | Holds pending user consent requests. |

Table 1 - Required shared map's in SpecificProxyService webapp.

An example of a configuration file is provided [here](src/test/resources/mock_eidasnode/igniteSpecificCommunication.xml).

<a name="integrate_with_idp"></a>
## 3. Integration with the IDP (OpenID Connect server)

<a name="idp_requirements"></a>
### 3.1 Requirements for IDP

The `SpecificProxyService` webapp delegates the eIDAS authentication request to a OIDC server.

The IDP integration requires the following OpenID Connect protocol features:
* The IDP must support the authrozation code flow from the [OpenID Connect Core](https://openid.net/specs/openid-connect-core-1_0.html#CodeFlowSteps) specification.
* The IDP must support the required OpenID Provider configuration as described in the [OpenID Connect Discovery])(https://openid.net/specs/openid-connect-discovery-1_0.html#ProviderMetadata) specification.

The following optional claims are mandatory in the ID token (in addition to other required claims listed in the [OpenID Connect Core](https://openid.net/specs/openid-connect-core-1_0.html#IDToken) specification): 
* `jti` - A unique identifier for the token
* `amr` - Authentication Method References
* `acr` - Authentication Context Class Reference

<a name="idp_request"></a>
### 3.2 Request eIDAS attributes using OpenID Connect scopes

`SpecificProxyService` webapp allows to map the eIDAS attributes in the incoming authentication request to custom OpenID Connect scopes (see [configruation reference](#configuration_parameters_oidc) for more details). These custom scopes are automatically appended to the list of requested scopes in the OpenID Connect authentication request. 

<a name="idp_response"></a>
### 3.3 Find eIDAS attribute values from the OpenID Connect ID Token

Information about the authenticated person is retrieved from ID-token issued by the IDP (retuned in the response of [OIDC token request](https://openid.net/specs/openid-connect-core-1_0.html#TokenRequest)).
Claims in the ID-token are mapped to their respective eIDAS attributes (see configuration reference for further details).

In some cases, ID-token claim values might need further processing to extract the attributed value. 

<a name="logging"></a>
## 4. Logging

Logging in SpecificProxyService is handled by [Logback framework](http://logback.qos.ch/documentation.html) through the [SLF4J facade](http://www.slf4j.org/).

<a name="log_conf"></a>
### 4.1 Log configuration

Logging can be configured by using an xml configuration file (logback-spring.xml). By default the SpecificProxyService webapp uses an [example configuration](src/main/resources/logback-spring.xml) embedded in the service application, that logs into a file - `/var/log/SpecificProxyService-yyyy-mm-dd.log` and rotates active file daily. Console logging is disabled by default.

Logging behavior can be customized in the following ways:

1. By overriding the specific parameter values in the default logback-spring.xml configuration file with environment variables (see table 4.1.1)

    Table 4.1.1 - properties in the default log confguration file

    | Parameter        | Mandatory | Description, example |
    | :---------------- | :---------- | :----------------|
    | `LOG_HOME` | No | Directory for log files. Defaults to `/var/log`, if not specified. |
    | `LOG_CONSOLE_LEVEL` | No | Level of detail for console logger. Valid values are: `OFF`, `FATAL`, `ERROR`, `WARN`, `INFO`, `DEBUG`, `TRACE`. Defaults to `OFF`, if not specified. |
    | `LOG_CONSOLE_PATTERN` | No | Log row pattern for console logs.  |
    | `LOG_FILE_LEVEL` | No | Level of detail for file logger. Valid values are: `OFF`, `FATAL`, `ERROR`, `WARN`, `INFO`, `DEBUG`, `TRACE`. Defaults to `INFO`, if not specified. |
    | `LOG_FILES_MAX_COUNT` | No | The number days rotated log files are kept locally. Defaults to `31`, if not specified. |
    | `APP_INSTANCE_ID` | No | A unique application identifier. Defaults to a unique UUID  (generated by the webapp at startup) if not specified. |

2. Custom logging configuration file can be provided for more detailed logging control. Log file location can be specified by using the environment variable `LOGGING_CONFIG`, Java system property `logging.config` or property providing the property `logging.config` in the application.properties file.

   Example 1: overriding the default log conf with environment variable:
    
    ````
    LOGGING_CONFIG=/etc/eidas/config/logback.xml
    ````
   
   Example 2: overriding the default log conf with Java system property:
       
   ````
   -Dlogging.config=/etc/eidas/config/logback.xml
   ````      

    Example 3: overriding the default log conf in the application.properties:
    
    ````
    logging.config=file:/etc/eidas/config/logback.xml
    ````   

<a name="log_file"></a>
### 4.2 Log file and format

By default the SpecificProxyService webapp uses an [example configuration](src/main/resources/logback-spring.xml) embedded in the service application, that logs into a file - `/var/log/SpecificProxyService-yyyy-mm-dd.log`. 

JSON format is used for a log row. The JSON field set for a single log record follows the [ECS Field reference](https://www.elastic.co/guide/en/ecs/current/ecs-field-reference.html).  

The following log record fields are supported:

| Parameter        | Mandatory | Description, example |
| :---------------- | :---------- | :----------------|
| `@timestamp` | Yes | Date/time when the event originated. |
| `log.level` | Yes | Original log level of the log event. Possible values: `ERROR`, `WARN`, `INFO`, `DEBUG`, `TRACE` |
| `log.logger` | Yes | The name of the logger inside an application. |
| `process.pid` | Yes | Process ID. |
| `process.thread.name` | Yes | Thread name. |
| `service.name` | Yes | Name of the service data is collected from. Constant value: `ee-eidas-proxy`. |
| `service.type` | Yes | The type of the service data is collected from. Constant value: `specific`. |
| `service.node.name` | Yes | Unique name of a service node. This allows for two nodes of the same service running on the same host to be differentiated. |
| `service.version` | No | Version of the service. |
| `session.id` | No | Unique identifier of the session. Cookie based identifier that enables log correlation between `EidasNode` and `SpecificProxyService` webapps. |
| `trace.id` | No | Unique identifier of the session. Groups multiple events like transactions that belong together. For example, a user request handled by multiple inter-connected services. |
| `transaction.id` | No | Unique identifier of the transaction. A transaction is the highest level of work measured within a service, such as a request to a server. |
| `message` | Yes | The log message. |
| `error.type` | No | The type of the error - the class name of the exception. |
| `error.stack_trace` | No | The stack trace of this error in plain text. |



Example log message:

````
{
	"@timestamp": "2020-06-26T17:38:09,388Z",
	"log.level": "INFO",
	"log.logger": "e.r.e.p.s.s.SpecificProxyServiceCommunication",
	"process.pid": 2447,
	"process.thread.name": "https-openssl-nio-8083-exec-4",
    "service.name": "ee-eidas-proxy",
    "service.type": "specific",
	"service.node.name": "specificproxy-8ie7665",
    "session.id": "43CB9681C492423DFA5DBF892ABA693C",
	"trace.id": "49eb6edf9621cea5",
	"transaction.id": "49eb6edf9621cea5",
	"message": "Request with ID: 'e1b4f4a9-f59e-44b0-aa17-6acc76ad0412' received"
}
````

<a name="heartbeat"></a>
## 6. Monitoring

`SpecificProxyService` webapp uses `Spring Boot Actuator` for monitoring. To customize Monitoring, Metrics, Auditing, and more see [Spring Boot Actuator documentation](https://docs.spring.io/spring-boot/docs/current/reference/html/production-ready-features.html#production-ready).    

### 6.1 Disable all monitoring endpoints configuration

| Parameter        | Mandatory | Description, example |
| :---------------- | :---------- | :----------------|
| `management.endpoints.jmx.exposure.exclude` | No | Endpoint IDs that should be excluded to be exposed over JMX or `*` for all. Recommended value `*` |
| `management.endpoints.web.exposure.exclude` | No | Endpoint IDs that should be excluded to be exposed over HTTP or `*` for all. Recommended value `*` |

### 6.2 Custom application health endpoint configuration

`SpecificProxyService` webapp implements [custom health endpoint](https://docs.spring.io/spring-boot/docs/current/reference/html/production-ready-features.html#production-ready-endpoints-custom) with id `heartbeat` and [custom health indicators](https://docs.spring.io/spring-boot/docs/current/reference/html/production-ready-features.html#writing-custom-healthindicators) with id's `igniteCluster`, `authenticationService`, `proxyServiceMetadata`, `truststore`. This endpoint is disabled by default.

Request:

````
curl -X GET https://ee-eidas-proxy:8083/SpecificProxyService/heartbeat
````

Response:
````
{
    "currentTime": "2020-07-01T09:42:46.307Z",
    "upTime": "PT1M25S",
    "buildTime": "2020-07-01T09:35:57.257Z",
    "name": "ee-specific-proxy",
    "startTime": "2020-07-01T09:41:33.411Z",
    "commitId": "7fb1482da3c091c361a7a9f4dbaf1e19817bc76f",
    "version": "0.0.1-SNAPSHOT",
    "commitBranch": "develop",
    "status": "UP",
    "dependencies": [
        {
            "name": "authenticationService",
            "status": "UP"
        },
        {
            "name": "igniteCluster",
            "status": "UP"
        },
        {
            "name": "proxyServiceMetadata",
            "status": "UP"
        },
        {
            "name": "truststore",
            "status": "UP"
        }
    ]
}
````

#### 6.2.1 Minimal recommended configuration to enable only `heartbeat` endpoint:

| Parameter        | Mandatory | Description, example |
| :---------------- | :---------- | :----------------|
| `management.endpoints.jmx.exposure.exclude` | No | Endpoint IDs that should be excluded to be exposed over JMX or `*` for all. Recommended value `*` |
| `management.endpoints.web.exposure.include` | No | Endpoint IDs that should be included to be exposed over HTTP or `*` for all. Recommended value `heartbeat` |
| `management.endpoints.web.base-path` | No |  Base path for Web endpoints. Relative to server.servlet.context-path or management.server.servlet.context-path if management.server.port is configured. Recommended value `/` |
| `management.health.defaults.enabled` | No | Whether to enable default Spring Boot Actuator health indicators. Recommended value `false` |
| `management.info.git.mode` | No | Mode to use to expose git information. Recommended value `full` |
| `eidas.proxy.health.dependencies.connect-timeout` | No | Timeout for `authenticationService` and `proxyServiceMetadata` health indicators. Defaults to `3s` |
| `eidas.proxy.health.trust-store-expiration-warning` | No | Certificate expiration warning period for `truststore` health indicator. Default value `30d` |

<a name="configuration_parameters"></a>
## APPENDIX 1 - Configuration parameters

<a name="configuration_parameters_idp"></a>
### Identity provider (OpenID Connect provider)

| Parameter        | Mandatory | Description, example |
| :---------------- | :---------- | :----------------|
| `eidas.proxy.oidc.client-id` | Yes | OpenID Connect client ID. |
| `eidas.proxy.oidc.client-secret` | Yes | OpenID Connect client secret. |
| `eidas.proxy.oidc.redirect-uri` | Yes | OpenID Connect client redirect URI. |
| `eidas.proxy.oidc.issuer-url` | Yes | OpenID Connect issuer URL. |
| `eidas.proxy.oidc.scope` | No | Comma separated list of additional scopes. Sets the value of `scope` parameter in the OpenID Connect authentication request. Defaults to `openid idcard mid` if not specified. |
| `eidas.proxy.oidc.accepted-amr-values` | No | Comma separated list of allowed values for the `amr` claim in the OpenID Connect ID-Token (Authentication Method Reference). Defaults to `idcard mID` if not specified. |
| `eidas.proxy.oidc.default-ui-language` | No | Sets the `ui_locales` parameter value in OpenID Connect authentication request. Defaults to `et` if not specified. |
| `eidas.proxy.oidc.connect-timeout-in-milliseconds` | No | Maximum period in milliseconds to establish a connection to the OpenID Connect token endpoint. Defaults to 5000 milliseconds if not specified. |
| `eidas.proxy.oidc.read-timeout-in-milliseconds` | No | Maximum period in milliseconds to wait for the OpenID Connect token endpoint response. Defaults to 5000 milliseconds if not specified. |
| `eidas.proxy.oidc.max-clock-skew-in-seconds` | No | Sets the maximum allowed clock differences when validating the time ID-token was issued. Defaults to 30 seconds if not specified. |
| `eidas.proxy.oidc.error-code-user-cancel` | No | <p>The expected error code returned in the OpenID Connect authentication [error response](https://openid.net/specs/openid-connect-core-1_0.html#AuthError) when user cancel's the authentication process at the IDP. </p> <p>Defaults to `user_cancel` when not specified.</p>    |
| `eidas.proxy.oidc.metadata.update-schedule` | No | Metadata update cron schedule. Defaults to `0 0 0/24 * * ?` if not specified. |
| `eidas.proxy.oidc.metadata.max-attempts` | No | Metadata update retry attempts in case of exception. Defaults to `3` if not specified. |
| `eidas.proxy.oidc.metadata.backoff-delay-in-milliseconds` | No | Metadata update retry backoff delay in milliseconds. Defaults to `60000` if not specified. |

<a name="configuration_parameters_eidas"></a>
### Integration with the `EidasNode` webapp

EidasNode communication

| Parameter        | Mandatory | Description, example |
| :---------------- | :---------- | :----------------|
| `eidas.proxy.node-specific-response-url` | Yes | The URL in the `EidasNode` webapp, that accepts the lighttoken that references the member state specific authentication response. |

Ignite configuration

| Parameter        | Mandatory | Description, example |
| :---------------- | :---------- | :----------------|
| `eidas.proxy.communication-cache.ignite-configuration-file-location` | Yes | File path that references Ignite Spring context configuration. Defaults to `file:${EIDAS_CONFIG_REPOSITORY}/igniteSpecificCommunication.xml`, if not specified. |
| `eidas.proxy.communication-cache.ignite-configuration-bean-name` | No | Ignite configuration ID (Spring bean ID). Defaults to `igniteSpecificCommunication.cfg`, if not specified. |


<a name="configuration_parameters_consent"></a>
### User consent

| Parameter        | Mandatory | Description, example |
| :---------------- | :---------- | :----------------|
| `eidas.proxy.ask-consent` | No | Whether the `SpecificProxyService` webapp should display a consent page to the user. Defaults to `true`. |
| `eidas.proxy.consent-binary-light-token.issuer` | Yes <sup>1</sup> | Whether the `SpecificProxyService` webapp should display a consent page to the user. Defaults to true. |
| `eidas.proxy.consent-binary-light-token.secret` | Yes <sup>1</sup> | Whether the `SpecificProxyService` webapp should display a consent page to the user. Defaults to true. |
| `eidas.proxy.consent-binary-light-token.algorithm` | Yes <sup>1</sup> | Whether the `SpecificProxyService` webapp should display a consent page to the user. Defaults to true. |

<sup>1</sup> Required when `eidas.proxy.ask-consent` is set to `true`

<a name="configuration_parameters_sptype"></a> 
### Supported service provider types

| Parameter        | Mandatory | Description, example |
| :---------------- | :---------- | :----------------|
| `eidas.proxy.supported-sp-types` | No | <p>A comma separated list of supported service provider types. Defaults to `public` when not specified.</p><p> An error response (AccessDenied) is sent to the EidasNode Connector service when the service provider type in the authentication request is not listed as one of the configuration parameter values.</p> |

<a name="configuration_parameters_oidc"></a> 
### Mapping eIDAS attributes to OpenID Connect authentication request scopes

Allows to map the requested eIDAS attributes to their respective OpenID Connect scopes in the IDP authentication request

| Parameter        | Mandatory | Description, example |
| :---------------- | :---------- | :----------------|
| `eidas.proxy.oidc.attribute-scope-mapping.<eidas attribute>=<scope value>` | No | Where `<eidas attribute>` is the "Friendly Name" (as specified in [the eIDAS attribute profile](https://ec.europa.eu/cefdigital/wiki/display/CEFDIGITAL/eIDAS+eID+Profile?preview=/82773108/82796977/eIDAS%20SAML%20Attribute%20Profile%20v1.1_2.pdf) ) of the attribute passed from EidasNode webapp and `<scope value>` is the corresponding OpenID Connect scope. Default mapping configuration applies if no explicit configuration is provided (see Table 1 for further details) |


Example: The following configuration maps four requested natural person eIDAS attributes to their respective scopes:  

````
eidas.proxy.oidc.attribute-scope-mapping.FirstName=eidas:attribute:first_name
eidas.proxy.oidc.attribute-scope-mapping.FamilyName=eidas:attribute:family_name
eidas.proxy.oidc.attribute-scope-mapping.DateOfBirth=eidas:attribute:date_of_birth
eidas.proxy.oidc.attribute-scope-mapping.PersonIdentifier=eidas:attribute:person_identifier
````

Table 1 - default configuration of eidas attributes to oidc scopes

| eIDAS attribute name | OIDC scope |
| :------------------- | :---- |
| FirstName | eidas:attribute:first_name |
| FamilyName | eidas:attribute:family_name |
| DateOfBirth | eidas:attribute:date_of_birth |
| PersonIdentifier | eidas:attribute:person_identifier |
| BirthName | eidas:attribute:birth_name |
| PlaceOfBirth | eidas:attribute:place_of_birth |
| CurrentAddress | eidas:attribute:current_address |
| Gender | eidas:attribute:gender
| LegalName | eidas:attribute:legal_name |
| LegalPersonIdentifier | eidas:attribute:legal_person_identifier |
| LegalAddress | eidas:attribute:legal_address |
| VATRegistration | eidas:attribute:vat_registration |
| TaxReference | eidas:attribute:tax_reference |
| D-2012-17-EUIdentifier | eidas:attribute:business_codes |
| LEI | eidas:attribute:lei |
| EORI | eidas:attribute:eori |
| SEED | eidas:attribute:seed |
| SIC | eidas:attribute:sic |


<a name="configuration_claims_oidc"></a>
### Mapping eIDAS attributes to OpenID Connect ID-token claims

Configuring claims extraction from the OIDC id_token

| Parameter        | Mandatory | Description, example |
| :---------------- | :---------- | :----------------|
| `eidas.proxy.oidc.response-claim-mapping.attributes.<eidas attribute>=<jsonpath to claim>` | No | Where `<eidas attribute>` is the "Friendly Name" (as specified in [the eIDAS attribute profile](https://ec.europa.eu/cefdigital/wiki/display/CEFDIGITAL/eIDAS+eID+Profile?preview=/82773108/82796977/eIDAS%20SAML%20Attribute%20Profile%20v1.1_2.pdf) ) of the attribute in the light response and `<jsonpath to claim>` is a [jsonpath expression](https://goessner.net/articles/JsonPath/) to extract the corresponding OpenID Connect claim value from the ID-token.  |

Example: The following configuration maps four natural person eIDAS attributes to OIDC id_token claims:

````
eidas.proxy.oidc.response-claim-mapping.attributes.FirstName=$.profile_attributes.given_name
eidas.proxy.oidc.response-claim-mapping.attributes.FamilyName=$.profile_attributes.family_name
eidas.proxy.oidc.response-claim-mapping.attributes.DateOfBirth=$.profile_attributes.date_of_birth
eidas.proxy.oidc.response-claim-mapping.attributes.PersonIdentifier=$.sub
````

Table 1 - default configuration of mapping OIDC id-token claims to eIDAS response attributes

| eIDAS attribute name | Claim in OIDC id-token |
| :------------------- | :---- |
| FirstName | $.profile_attributes.given_name |
| FamilyName | $.profile_attributes.family_name |
| eidas.proxy.oidc.response-claim-mapping.attributes.DateOfBirth | $.profile_attributes.date_of_birth |
| eidas.proxy.oidc.response-claim-mapping.attributes.PersonIdentifier | $.sub |

### Postprocessing OpenID Connect ID-token claim values

Configuring attribute value extraction from the OIDC id_token claim value

| Parameter        | Mandatory | Description, example |
| :---------------- | :---------- | :----------------|
| `eidas.proxy.oidc.response-claim-mapping.attributes-post-processing.<eidas attribute>=<regexp>` | No | <p>Allows further processing of the claim value associated with the eIDAS attribute.</p> <p>`<eidas attribute>` is the (Friendly) Name of the attribute passed from EidasNode webapp.</p><p>`<regexp>` is the regular expression that defines the rules to extract the attribute value from the claim. Note that a [named regex group](https://www.regular-expressions.info/refext.html) with the name `attributeValue` must be used to mark the valid extractable value.</p>  | 


Example: The following configuration extracts the Estonian ID code `60001019906` from the claim value `EE60001019906`
````
eidas.proxy.oidc.response-claim-mapping.attributes-post-processing.PersonIdentifier=^EE(?<value>[\\d]{11,11})$
````

<a name="truststore"></a>
### HTTPS truststore

The `SpecificProxyService` webapp uses the default Java truststore to trust external HTTPS endpoints.

To override the default truststore, use the following system properties:

| Parameter        | Mandatory | Description, example |
| :---------------- | :---------- | :----------------|
| javax.net.ssl.trustStore | No | Path to truststore file. |
| javax.net.ssl.trustStorePassword | No | The secret to access truststore |
| javax.net.ssl.trustStoreType | No | The trust store type (`PKCS12`, `JKS`, etc) |

Example: Sample Tomcat setenv.sh file that specifies custom truststore at startup
````
export JAVA_OPTS="$JAVA_OPTS -Djavax.net.ssl.trustStore=/etc/eidas/secrets/tls/truststore.p12 -Djavax.net.ssl.trustStorePassword=secret -Djavax.net.ssl.trustStoreType=pkcs12"
````

<a name="http_security"></a>
### HTTPS security

| Parameter        | Mandatory | Description, example |
| :---------------- | :---------- | :----------------|
| `eidas.proxy.webapp.allowed-http-methods` | No | Allowed HTTP methods for all service endpoints. Default value `GET, POST` |
| `eidas.proxy.webapp.session-id-cookie-name` | No | Session id cookie to be used for log correlation. Default value `JSESSIONID` |
| `eidas.proxy.webapp.content-security-policy` | No | Content security policy. Default value `block-all-mixed-content; default-src 'self'; object-src: 'none'; frame-ancestors 'none';` |
