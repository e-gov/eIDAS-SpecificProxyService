# EE specific eIDAS proxy service

## Requirements

1. `EIDAS_CONFIG_REPOSITORY` environment variable - the location of the shared ignite configuration

    Required configuration files:
    * `igniteSpecificCommunication.xml` - Spring configuration file that specifies the Ignite cache configuration details. Needed for shared request/response communication configuration.

2. `SPECIFIC_PROXY_SERVICE_CONFIG_REPOSITORY` environment variable - location for the shared communication settings
    
    Required configuration files:
     
    * `specificCommunicationDefinitionProxyservice.xml` - Properties file. Communication settings between EidasNode and SpecificProxyService applications.
    * `eidas-attributes.xml` - defines a set of eIDAS attributes supported by the ProxyService

## Build

````
./mvnw clean package
````