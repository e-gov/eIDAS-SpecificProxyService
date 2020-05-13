# EE specific eIDAS proxy service


## Build

````
./mvnw clean package
````

## Integration with EidasNode webapp

**NB!** It is assumed that the `SpecificProxyService` webapp is installed in the same web server instance as `EidasNode` and that both have access to the same configuration files.
  
### 1. Communication link with EidasNode 

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

### 2. Supported eIDAS attributes     

Supported eIDAS attributes list is loaded from
`$SPECIFIC_PROXY_SERVICE_CONFIG_REPOSITORY/eidas-attributes.xml` - defines a set of eIDAS attributes supported by the ProxyService

## Configuration parameters

### Identity provider (OpenID Connect provider)

| Parameter        | Mandatory | Description, example |
| :---------------- | :---------- | :----------------|
| `eidas.proxy.oidc.client-id` | Yes | OpenID Connect client ID. |
| `eidas.proxy.oidc.client-secret` | Yes | OpenID Connect client secret. |
| `eidas.proxy.oidc.redirect-uri` | Yes | OpenID Connect client redirect URI. |
| `eidas.proxy.oidc.issuer-url` | Yes | OpenID Connect issuer URL. |
| `eidas.proxy.oidc.scope` | No | Sets the value of `scope` parameter in the OpenID Connect authentication request. Defaults to `openid idcard mid` if not specified. |

### Integration with the `EidasNode` webapp

| Parameter        | Mandatory | Description, example |
| :---------------- | :---------- | :----------------|
| `eidas.proxy.node-specific-response-url` | Yes | The URL in the `EidasNode` webapp, that accepts the lighttoken that references the member state specific authentication response. |

## User consent

| Parameter        | Mandatory | Description, example |
| :---------------- | :---------- | :----------------|
| `eidas.proxy.ask-consent` | No | Whether the `SpecificProxyService` webapp should display a consent page to the user. Defaults to `true`. |
| `eidas.proxy.consent-binary-light-token.issuer` | Yes <sup>1</sup> | Whether the `SpecificProxyService` webapp should display a consent page to the user. Defaults to true. |
| `eidas.proxy.consent-binary-light-token.secret` | Yes <sup>1</sup> | Whether the `SpecificProxyService` webapp should display a consent page to the user. Defaults to true. |
| `eidas.proxy.consent-binary-light-token.algorithm` | Yes <sup>1</sup> | Whether the `SpecificProxyService` webapp should display a consent page to the user. Defaults to true. |

<sup>1</sup> Required when `eidas.proxy.ask-consent` is set to `true`
