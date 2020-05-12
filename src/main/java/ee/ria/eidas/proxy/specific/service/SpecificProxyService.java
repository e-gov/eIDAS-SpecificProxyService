/* 
#   Copyright (c) 2017 European Commission  
#   Licensed under the EUPL, Version 1.2 or – as soon they will be 
#   approved by the European Commission - subsequent versions of the 
#    EUPL (the "Licence"); 
#    You may not use this work except in compliance with the Licence. 
#    You may obtain a copy of the Licence at: 
#    * https://joinup.ec.europa.eu/page/eupl-text-11-12  
#    *
#    Unless required by applicable law or agreed to in writing, software 
#    distributed under the Licence is distributed on an "AS IS" basis, 
#    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. 
#    See the Licence for the specific language governing permissions and limitations under the Licence.
 */

package ee.ria.eidas.proxy.specific.service;

import com.nimbusds.openid.connect.sdk.op.OIDCProviderMetadata;
import ee.ria.eidas.proxy.specific.config.SpecificProxyServiceProperties;
import ee.ria.eidas.proxy.specific.storage.StoredMSProxyServiceRequestCorrelationMap;
import eu.eidas.auth.commons.light.ILightRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.util.UriComponentsBuilder;

import javax.cache.Cache;
import javax.xml.bind.JAXBException;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.util.Collections;
import java.util.UUID;

/**
 * SpecificProxyService: provides a sample implementation for interacting with the IdP.
 * For the request: it creates the simple protocol request to be send to IdP for authentication
 * For the response: it processes the received IdP specific response and builds the LightResponse
 *
 * @since 2.0
 */
@Slf4j
@RequiredArgsConstructor
public class SpecificProxyService {

    private final OIDCProviderMetadata oidcProviderMetadata;

    private final SpecificProxyServiceProperties specificProxyServiceProperties;

    /**
     * Correlation Map between the simple protocol request Id to be send to the IdP and the holder
     * of the light request and correlated simple protocol request sent by the Proxy-service.
     */
    private final Cache specificMSIdpRequestCorrelationMap;


    /**
     * Method that translates from the Node Request to the MS Specific Request.
     *
     * @param originalIlightRequest  the initial light request received
     * @param consentedIlightRequest the resulting light request that only contains the consent attributes
     * @return MS Specific Request translated from the Node Request Base64 encoded
     * @throws JAXBException if the MS Specific Request could not be marshalled
     */
    public URL translateNodeRequest(ILightRequest originalIlightRequest, ILightRequest consentedIlightRequest) throws JAXBException, MalformedURLException {
        final String state = UUID.randomUUID().toString();

        URI oidAuthenticationRequest = UriComponentsBuilder.fromUri(oidcProviderMetadata.getAuthorizationEndpointURI())
                .queryParam("scope", specificProxyServiceProperties.getOidc().getScope())
                .queryParam("response_type", "code")
                .queryParam("client_id",  specificProxyServiceProperties.getOidc().getClientId())
                .queryParam("redirect_uri", specificProxyServiceProperties.getOidc().getRedirectUri())
                .queryParam("state", state).build().toUri();

        final StoredMSProxyServiceRequestCorrelationMap.CorrelatedRequestsHolder correlatedRequestsHolder = new StoredMSProxyServiceRequestCorrelationMap.CorrelatedRequestsHolder(
                originalIlightRequest,
                Collections.singletonMap(state, oidAuthenticationRequest)
        );
        specificMSIdpRequestCorrelationMap.put(state, correlatedRequestsHolder);
        return oidAuthenticationRequest.toURL();
    }
}
