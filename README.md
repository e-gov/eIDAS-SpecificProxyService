# EE specific eIDAS proxy service

- [1. Building the SpecifcProxyService webapp](#build)
- [2. Integration with EidasNode webapp](#integrate_with_eidasnode)
  * [2.1. Configuring communication with EidasNode](#integrate_eidasnode)
  * [2.2. Supported eIDAS attributes](#eidas_attributes)
- [3. Integration with the IDP (OpenID Connect server)](#integrate_with_idp)
  * [3.1. Requirements for IDP](#idp_requirements)
  * [3.2. Requesting eIDAS attributes using OpenID Connect scopes](#idp_request)
  * [3.3. Fetching eIDAS attributes from OpenID Connect ID Token](#idp_response)  
- [4. Logging](#logging)
- [5. HTTPS support](#https)    
- [6. Monitoring](#heartbeat)
- [7. Appendix 1 - Configuration parameters reference](#configuration_parameters)
  * [7.1 Identity provider (OpenID Connect provider)](#configuration_parameters_idp)
  * [7.2 Integration with the EidasNode webapp](#configuration_parameters_eidas)
  * [7.3 User consent](#configuration_parameters_consent)
  * [7.4 Supported service provider types](#configuration_parameters_sptype)
  * [7.5 Mapping eIDAS attributes to OpenID Connect authentication request scopes](#configuration_parameters_oidc)
  * [7.6 Mapping eIDAS attributes to OpenID Connect ID-token claims](#configuration_claims_oidc)


<a name="build"></a>
## 1. Building the SpecifcProxyService webapp

````
./mvnw clean package
````

<a name="integrate_with_eidasnode"></a>
## 2. Integration with EidasNode webapp

**NB!** It is assumed that the `SpecificProxyService` webapp is installed in the same web server instance as `EidasNode` and that both have access to the same configuration files.

<a name="integrate_eidasnode"></a>  
### 2.1 Configuring communication with EidasNode

In order to enable communication between `EidasNode` and `SpecificProxyService` webapps, both must be able to access the same `Ignite` cluster and have the same communication configuration (shared secret, etc).

* To set the same communication definitions, it is required that the `SpecificProxyService` has access to communication definitions provided in the following file `EidasNode` configuration file:  
`$SPECIFIC_PROXY_SERVICE_CONFIG_REPOSITORY/specificCommunicationDefinitionProxyservice.xml`

* To access the same Ignite cluster, it is required that the `SpecificProxyService` has access to Ignite configuration settings in the following `EidasNode` configuration file: `$EIDAS_CONFIG_REPOSITORY/igniteSpecificCommunication.xml` (a Spring configuration file that specifies the Ignite cache configuration details. Needed for shared request/response communication configuration).

Note that new map definitions are necessary for `SpecificProxyService` webapp
    
````
<bean class="org.apache.ignite.configuration.CacheConfiguration">
    <property name="name" value="specificMSIdpRequestCorrelationMap"/>
    <property name="atomicityMode" value="ATOMIC"/>
    <property name="backups" value="1"/>
    <property name="expiryPolicyFactory" ref="7_minutes_duration"/>
</bean>

<bean class="org.apache.ignite.configuration.CacheConfiguration">
    <property name="name" value="specificMSIdpConsentCorrelationMap"/>
    <property name="atomicityMode" value="ATOMIC"/>
    <property name="backups" value="1"/>
    <property name="expiryPolicyFactory" ref="7_minutes_duration"/>
</bean>    
````

<a name="eidas_attributes"></a> 
### 2.2 Supported eIDAS attributes     

Supported eIDAS attributes list is loaded from
`$SPECIFIC_PROXY_SERVICE_CONFIG_REPOSITORY/eidas-attributes.xml` - defines a set of eIDAS attributes supported by the ProxyService

<a name="integrate_with_idp"></a>
## 3. Integration with the IDP (OpenID Connect server)

<a name="idp_requirements"></a>  
### 3.1 Requirements for IDP

The `SpecifciProxyService` webapp delegates the eIDAS authentication request to a OIDC server.

The IDP integration requires the following OpenID Connect protocol features:
* The IDP must support the authrozation code flow from the [OpenID Connect Core](https://openid.net/specs/openid-connect-core-1_0.html#CodeFlowSteps) specification.
* The IDP must support the required OpenID Provider configuration as described in the [OpenID Connect Discovery])(https://openid.net/specs/openid-connect-discovery-1_0.html#ProviderMetadata) specification.

<a name="idp_request"></a>
### 3.2 Request eIDAS attributes using OpenID Connect scopes

`SpecificProxyService` webapp allows to map the eIDAS attributes in the incoming authentication request to custom OpenID Connect scopes (see [configruation reference](#configuration_parameters_oidc) for more details). These custom scopes are automatically appended to the list of requested scopes in the OpenID Connect authentication request. 

<a name="idp_response"></a>
### 3.3 Find eIDAS attribute values from the OpenID Connect ID Token

Information about the authenticated person is retrieved from ID-token issued by the IDP (retuned in the response of [OIDC token request](https://openid.net/specs/openid-connect-core-1_0.html#TokenRequest)).
Claims in the ID-token are mapped to their respective eIDAS attributes (see configuration reference for further details).

<a name="logging"></a>
## 4. Logging

TBD - log files

TBD - log format

TBD - overriding default log conf

<a name="https"></a>
## 5. HTTPS support

TBD - trusting the idp

<a name="heartbeat"></a>
## 6. Monitoring

TBD - heartbeat endpoint

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
| `eidas.proxy.oidc.default-ui-language` | No | Sets the `ui_locales` parameter value in OpenID Connect authentication request. Defaults to `et` if not specified. |
| `eidas.proxy.oidc.connect-timeout-in-milliseconds` | No | Maximum period in milliseconds to establish a connection to the OpenID Connect token endpoint. Defaults to 5000 milliseconds if not specified. |
| `eidas.proxy.oidc.read-timeout-in-milliseconds` | No | Maximum period in milliseconds to wait for the OpenID Connect token endpoint response. Defaults to 5000 milliseconds if not specified. |
| `eidas.proxy.oidc.max-clock-skew-in-seconds` | No | Sets the maximum allowed clock differences when validating the time ID-token was issued. Defaults to 30 seconds if not specified. |
| `eidas.proxy.oidc.error-code-user-cancel` | No | <p>The expected error code returned in the OpenID Connect authentication [error response](https://openid.net/specs/openid-connect-core-1_0.html#AuthError) when user cancel's the authentication process at the IDP. </p> <p>Defaults to `user_cancel` when not specified.</p>    |


<a name="configuration_parameters_eidas"></a> 
### Integration with the `EidasNode` webapp

| Parameter        | Mandatory | Description, example |
| :---------------- | :---------- | :----------------|
| `eidas.proxy.node-specific-response-url` | Yes | The URL in the `EidasNode` webapp, that accepts the lighttoken that references the member state specific authentication response. |

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
| `eidas.proxy.supported-sp-types` | No | <p>A comma separated list of supported service provider types. Defaults to `public, private` when not specified.</p><p> An error response (AccessDenied) is sent to the EidasNode Connector service when the service provider type in the authentication request is not listed as one of the configuration parameter values.</p> |

<a name="configuration_parameters_oidc"></a> 
### Mapping eIDAS attributes to OpenID Connect authentication request scopes

Configuring the requested scopes for OIDC authentication request

| Parameter        | Mandatory | Description, example |
| :---------------- | :---------- | :----------------|
| `eidas.proxy.oidc.attribute-scope-mapping.<eidas attribute>=<scope value>` | No | Where `<eidas attribute>` is the (Friendly) Name of the attribute passed from EidasNode webapp and `<scope value>` is the corresponding OpenID Connect scope required to request that attribute from the IDP.  |

Example: The following configuration maps four requested natural person eIDAS attributes to their respective scopes:  

````
eidas.proxy.oidc.attribute-scope-mapping.FirstName=eidas:attribute:first_name
eidas.proxy.oidc.attribute-scope-mapping.FamilyName=eidas:attribute:family_name
eidas.proxy.oidc.attribute-scope-mapping.DateOfBirth=eidas:attribute:date_of_birth
eidas.proxy.oidc.attribute-scope-mapping.PersonIdentifier=eidas:attribute:person_identifier
````

<a name="configuration_claims_oidc"></a> 
### Mapping eIDAS attributes to OpenID Connect ID-token claims

Configuring claims extraction from the OIDC id_token

| Parameter        | Mandatory | Description, example |
| :---------------- | :---------- | :----------------|
| `eidas.proxy.oidc.response-claim-mapping.attributes.<eidas attribute>=<jsonpath to claim>` | No | Where `<eidas attribute>` is the (Friendly) Name of the attribute passed from EidasNode webapp and `<jsonpath to claim>` is a [jsonpath expression](https://goessner.net/articles/JsonPath/) to extract the corresponding OpenID Connect claim value from the IDP returned ID-token.  |

Example: The following configuration maps four natural person eIDAS attributes to OIDC id_token claims:

````
eidas.proxy.oidc.response-claim-mapping.attributes.FirstName=$.profile_attributes.given_name
eidas.proxy.oidc.response-claim-mapping.attributes.FamilyName=$.profile_attributes.family_name
eidas.proxy.oidc.response-claim-mapping.attributes.DateOfBirth=$.profile_attributes.date_of_birth
eidas.proxy.oidc.response-claim-mapping.attributes.PersonIdentifier=$.sub
````