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

import eu.eidas.auth.commons.attribute.AttributeDefinition;
import eu.eidas.auth.commons.attribute.ImmutableAttributeMap;
import eu.eidas.auth.commons.light.ILightRequest;
import eu.eidas.auth.commons.light.ILightResponse;
import eu.eidas.auth.commons.light.impl.LightRequest;
import eu.eidas.auth.commons.light.impl.LightResponse;
import eu.eidas.auth.commons.tx.BinaryLightToken;
import eu.eidas.specificcommunication.BinaryLightTokenHelper;
import eu.eidas.specificcommunication.CommunicationCache;
import eu.eidas.specificcommunication.exception.SpecificCommunicationException;
import eu.eidas.specificcommunication.protocol.SpecificCommunicationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.util.Assert;

import javax.annotation.PostConstruct;
import javax.cache.Cache;
import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import java.util.Collection;

/**
 * Implements {@link SpecificCommunicationService} to be used for exchanging of
 * {@link ILightRequest} and {@link ILightResponse} between the specific
 * proxy-service and node proxy-service
 *
 * @since 2.0
 */
@Slf4j
@RequiredArgsConstructor
public class SpecificProxyserviceCommunicationServiceImpl implements SpecificCommunicationService {

	private static LightJAXBCodec codec;

	private final Cache<String, String> specificNodeProxyserviceRequestCommunicationCache;

	private final Cache<String, String> specificNodeProxyserviceResponseCommunicationCache;

	static {
		try {
			codec = new LightJAXBCodec(JAXBContext.newInstance(LightRequest.class, LightResponse.class,
					ImmutableAttributeMap.class, AttributeDefinition.class));
		} catch (JAXBException e) {
			log.error("Unable to instantiate in static initializer ",e);
		}
	}

	@Value("${lightToken.proxyservice.request.issuer.name}")
	private String lightTokenRequestIssuerName;

	@Value("${lightToken.proxyservice.request.secret}")
	private String lightTokenRequestSecret;

	@Value("${lightToken.proxyservice.request.algorithm}")
	private String lightTokenRequestAlgorithm;

	@Value("${lightToken.proxyservice.response.issuer.name}")
	private String lightTokenResponseIssuerName;

	@Value("${lightToken.proxyservice.response.secret}")
	private String lightTokenResponseSecret;

	@Value("${lightToken.proxyservice.response.algorithm}")
	private String lightTokenResponseAlgorithm;

	@PostConstruct
	public void init() {
		Assert.notNull(lightTokenRequestIssuerName, "issuerName cannot be null. Please check your configuration");
	}

	@Override
	public BinaryLightToken putRequest(final ILightRequest iLightRequest) throws SpecificCommunicationException {


		final BinaryLightToken binaryLightToken = BinaryLightTokenHelper.createBinaryLightToken(
				lightTokenRequestIssuerName,
				lightTokenRequestSecret,
				lightTokenRequestAlgorithm);
		final String tokenId = binaryLightToken.getToken().getId();
		specificNodeProxyserviceRequestCommunicationCache.put(tokenId, codec.marshall(iLightRequest));
		return binaryLightToken;
	}

	@Override
	public ILightRequest getAndRemoveRequest(final String tokenBase64,
			final Collection<AttributeDefinition<?>> registry) throws SpecificCommunicationException {
		final String binaryLightTokenId = BinaryLightTokenHelper.getBinaryLightTokenId(tokenBase64,
				lightTokenRequestSecret,
				lightTokenRequestAlgorithm);

		return codec.unmarshallRequest(specificNodeProxyserviceRequestCommunicationCache.getAndRemove(binaryLightTokenId),registry);
	}

	@Override
	public BinaryLightToken putResponse(final ILightResponse iLightResponse) throws SpecificCommunicationException {
		final BinaryLightToken binaryLightToken = BinaryLightTokenHelper.createBinaryLightToken(
				lightTokenResponseIssuerName, lightTokenResponseSecret, lightTokenResponseAlgorithm);
		final String tokenId = binaryLightToken.getToken().getId();
		specificNodeProxyserviceResponseCommunicationCache.put(tokenId, codec.marshall(iLightResponse));
		return binaryLightToken;
	}

	@Override
	public ILightResponse getAndRemoveResponse(final String tokenBase64,
			final Collection<AttributeDefinition<?>> registry) throws SpecificCommunicationException {
		// TODO - connector specific
		return null;
	}

}
