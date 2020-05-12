# EE specific eIDAS proxy service

## Configuration

The following environment variables and EidasNode configuration files are required for SpecificProxyService:
  
1. The `EIDAS_CONFIG_REPOSITORY` environment variable. 

    Required configuration files:
    * `$EIDAS_CONFIG_REPOSITORY/igniteSpecificCommunication.xml` - Spring configuration file that specifies the Ignite cache configuration details. Needed for shared request/response communication configuration.
    
        Extra map configuration must be defined in addition to default cache maps provided with EidasNode sample installation:
        ````xml
            <bean class="org.apache.ignite.configuration.CacheConfiguration">
                <property name="name" value="specificMSIdpRequestCorrelationMap"/>
                <property name="atomicityMode" value="ATOMIC"/>
                <property name="backups" value="1"/>
                <property name="expiryPolicyFactory" ref="7_minutes_duration"/>
            </bean>
        ````

2. `SPECIFIC_PROXY_SERVICE_CONFIG_REPOSITORY` environment variable.

    Required configuration files:
     
    * `$SPECIFIC_PROXY_SERVICE_CONFIG_REPOSITORY/specificCommunicationDefinitionProxyservice.xml` - Properties file. Communication settings between EidasNode and SpecificProxyService applications.
    * `$SPECIFIC_PROXY_SERVICE_CONFIG_REPOSITORY/eidas-attributes.xml` - defines a set of eIDAS attributes supported by the ProxyService

## Build

````
./mvnw clean package
````