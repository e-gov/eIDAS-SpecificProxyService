package ee.ria.eidas.proxy.specific.web;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSortedSet;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.proc.BadJOSEException;
import com.nimbusds.jwt.JWT;
import com.nimbusds.oauth2.sdk.*;
import com.nimbusds.oauth2.sdk.auth.ClientAuthentication;
import com.nimbusds.oauth2.sdk.auth.ClientSecretBasic;
import com.nimbusds.oauth2.sdk.auth.Secret;
import com.nimbusds.oauth2.sdk.id.ClientID;
import com.nimbusds.oauth2.sdk.id.Issuer;
import com.nimbusds.openid.connect.sdk.OIDCTokenResponse;
import com.nimbusds.openid.connect.sdk.OIDCTokenResponseParser;
import com.nimbusds.openid.connect.sdk.claims.ClaimsSet;
import com.nimbusds.openid.connect.sdk.op.OIDCProviderMetadata;
import com.nimbusds.openid.connect.sdk.validators.IDTokenValidator;
import ee.ria.eidas.proxy.specific.config.SpecificProxyServiceProperties;
import ee.ria.eidas.proxy.specific.service.SpecificProxyService;
import ee.ria.eidas.proxy.specific.storage.StoredMSProxyServiceRequestCorrelationMap;
import eu.eidas.auth.commons.EidasParameterKeys;
import eu.eidas.auth.commons.attribute.AttributeDefinition;
import eu.eidas.auth.commons.attribute.AttributeRegistry;
import eu.eidas.auth.commons.attribute.AttributeValue;
import eu.eidas.auth.commons.attribute.ImmutableAttributeMap;
import eu.eidas.auth.commons.light.ILightRequest;
import eu.eidas.auth.commons.light.ILightResponse;
import eu.eidas.auth.commons.light.impl.LightResponse;
import eu.eidas.auth.commons.light.impl.ResponseStatus;
import eu.eidas.auth.commons.protocol.eidas.LevelOfAssurance;
import eu.eidas.auth.commons.protocol.impl.SamlNameIdFormat;
import eu.eidas.auth.commons.tx.BinaryLightToken;
import eu.eidas.specificcommunication.BinaryLightTokenHelper;
import eu.eidas.specificcommunication.protocol.SpecificCommunicationService;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.util.UriComponentsBuilder;

import javax.annotation.Nonnull;
import javax.cache.Cache;
import javax.servlet.ServletException;
import java.net.*;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;

@Slf4j
@Controller
public class IdpResponseController {

	public static final String ENDPOINT_IDP_RESPONSE = "/IdpResponse";
	public static final String PARAMETER_NAME_CODE = "code";
	public static final String PARAMETER_NAME_STATE = "state";
	public static final String PARAMETER_NAME_ERROR = "error";
	public static final String PARAMETER_NAME_ERROR_DESCRIPTION = "error_description";

	public static final String PARAMETER_TOKEN = "binaryLightToken";


	@Autowired
	private SpecificProxyServiceProperties specificProxyServiceProperties;

	@Autowired
	private SpecificProxyService specificProxyService;

	@Autowired
	private OIDCProviderMetadata oidcProviderMetadata;

	@Autowired
	private Cache<String, StoredMSProxyServiceRequestCorrelationMap.CorrelatedRequestsHolder> specificMSIdpRequestCorrelationMap;

	@Autowired
	@Qualifier("springManagedSpecificProxyserviceCommunicationService")
	private SpecificCommunicationService specificCommunicationService;

	@Autowired
	private AttributeRegistry eidasAttributeRegistry;


	@GetMapping(value = ENDPOINT_IDP_RESPONSE)
	public ModelAndView processIdpResponse (Model model,
								final @RequestParam(name = PARAMETER_NAME_STATE) String state,
							  final @RequestParam(name = PARAMETER_NAME_CODE, required=false) String code,
							  final @RequestParam(name = PARAMETER_NAME_ERROR, required=false) String error,
							  final @RequestParam(name = PARAMETER_NAME_ERROR_DESCRIPTION, required=false) String errorDescription) {

		return executeIdpResponseProcessing(model, code, state);
	}

	@SneakyThrows
	private ModelAndView executeIdpResponseProcessing(final Model model, final String code, final String state) {
		ILightResponse lightResponse = prepareNodeResponse(code, state);

		if (specificProxyServiceProperties.isAskConsent()) {

			ImmutableMap<AttributeDefinition<?>, ImmutableSet<? extends AttributeValue<?>>> attributes = prepareAttributesToAskConsent(lightResponse);

			model.addAttribute(EidasParameterKeys.ATTRIBUTE_LIST.toString(),attributes);
			model.addAttribute("LoA", lightResponse.getLevelOfAssurance());
			model.addAttribute("redirectUrl", "Consent");
			model.addAttribute(EidasParameterKeys.BINDING.toString(), "GET");
			model.addAttribute(PARAMETER_TOKEN, specificProxyService.createStoreBinaryLightTokenResponseBase64(lightResponse));

			return new ModelAndView("citizenConsentResponse");
		} else {

			final BinaryLightToken binaryLightToken = specificCommunicationService.putResponse(lightResponse);
			final String token = BinaryLightTokenHelper.encodeBinaryLightTokenBase64(binaryLightToken);

			URI redirectUrl = UriComponentsBuilder
					.fromUri(URI.create(specificProxyServiceProperties.getNodeSpecificResponseUrl()))
					.queryParam(EidasParameterKeys.TOKEN.getValue() , token)
					.build().toUri();

			return new ModelAndView("redirect:" + redirectUrl);
		}
	}

	private ImmutableMap<AttributeDefinition<?>, ImmutableSet<? extends AttributeValue<?>>> prepareAttributesToAskConsent(ILightResponse lightResponse) {
		ImmutableAttributeMap responseImmutableAttributeMap = lightResponse.getAttributes();
		ImmutableMap<AttributeDefinition<?>, ImmutableSet<? extends AttributeValue<?>>> responseImmutableMap = responseImmutableAttributeMap.getAttributeMap();
		ImmutableAttributeMap.Builder filteredAttrMapBuilder = ImmutableAttributeMap.builder();

		for (AttributeDefinition attrDef : responseImmutableMap.keySet()) {
			final boolean isEidasCoreAttribute = eidasAttributeRegistry.contains(attrDef);
			if (isEidasCoreAttribute) {
				filteredAttrMapBuilder.put(attrDef, responseImmutableAttributeMap.getAttributeValuesByNameUri(attrDef.getNameUri()));
			}
		}
		return filteredAttrMapBuilder.build().getAttributeMap();
	}

	private ILightResponse prepareNodeResponse(String code, String state) throws ServletException {
		final ILightResponse lightResponse;
		try {
			ClientID clientID = new ClientID(specificProxyServiceProperties.getOidc().getClientId());
			ClientAuthentication clientAuth = new ClientSecretBasic(
					clientID,
					new Secret(specificProxyServiceProperties.getOidc().getClientSecret())
			);

			AuthorizationCode authorizationCode = new AuthorizationCode(code);
			URI callback = new URI(specificProxyServiceProperties.getOidc().getRedirectUri());
			AuthorizationGrant codeGrant = new AuthorizationCodeGrant(authorizationCode, callback);

			URI tokenEndpoint = oidcProviderMetadata.getTokenEndpointURI();
			TokenRequest request = new TokenRequest(tokenEndpoint, clientAuth, codeGrant, null, null, Collections.singletonMap("state", Collections.singletonList(state)));
			log.info("Request id-token from {} ", request.getEndpointURI());
			TokenResponse tokenResponse = OIDCTokenResponseParser.parse(request.toHTTPRequest().send());

			if (!tokenResponse.indicatesSuccess()) {
				// We got an error response...
				TokenErrorResponse errorResponse = tokenResponse.toErrorResponse();
				log.error("Token endpoint " + tokenEndpoint + " returned an error: " + errorResponse.getErrorObject());
				throw new IllegalStateException("OIDC token request returned an error!");
			}

			OIDCTokenResponse successResponse = (OIDCTokenResponse)tokenResponse.toSuccessResponse();

			// Get the ID and access token, the server may also return a refresh token
			JWT idToken = successResponse.getOIDCTokens().getIDToken();
			log.info("ID-TOKEN: " + idToken.getParsedString());

			// Verify the ID-token
			Issuer iss = new Issuer(oidcProviderMetadata.getIssuer());
			JWSAlgorithm jwsAlg = JWSAlgorithm.RS256;
			URL jwkSetURL = oidcProviderMetadata.getJWKSetURI().toURL();

			// Create validator for signed ID tokens
			IDTokenValidator validator = new IDTokenValidator(iss, clientID, jwsAlg, jwkSetURL);
			try {
				ClaimsSet claims = validator.validate(idToken, null);
				log.info("OIDC response successfully verified!");
				lightResponse = translateToLightResponse(state, claims);
				log.info("LightResponse for eIDAS-Proxy service: " + lightResponse.toString());
			} catch (BadJOSEException | JOSEException e) {
				throw new IllegalStateException("Error when validating id_token!", e);
			}

		} catch (Exception e) {
			log.error("Error unmarshalling MS Specific Request"+e);
			throw new ServletException(e);
		}

		return lightResponse;
	}

	public ILightResponse translateToLightResponse(String state, ClaimsSet claimSet) {
		try {
			log.info("JWT (claims): " + claimSet.toJSONString());

			ImmutableAttributeMap.Builder attrBuilder = ImmutableAttributeMap.builder();
			Map profileAttributes = claimSet.getClaim("profile_attributes", Map.class);
			String subject = claimSet.getStringClaim("sub");
			putAttribute(attrBuilder, "FamilyName", String.valueOf(profileAttributes.get("family_name")) );
			putAttribute(attrBuilder, "FirstName", String.valueOf(profileAttributes.get("given_name")) );
			putAttribute(attrBuilder, "DateOfBirth", String.valueOf(profileAttributes.get("date_of_birth")) );
			putAttribute(attrBuilder, "PersonIdentifier", subject);
			LevelOfAssurance loa = LevelOfAssurance.valueOf(claimSet.getStringClaim("acr").toUpperCase());

			final String inResponseToId = state;
			ILightRequest iLightRequest =  getRemoveCorrelatediLightRequest(inResponseToId).get();

			final LightResponse.Builder builder = LightResponse.builder()
					.id(claimSet.getStringClaim("jti"))
					.ipAddress(getIssuerIp(specificProxyServiceProperties.getOidc().getIssuerUrl()))
					.inResponseToId(iLightRequest.getId())
					.issuer(claimSet.getIssuer().getValue())
					.levelOfAssurance(loa.stringValue())
					.relayState(iLightRequest.getRelayState())
					.status(ResponseStatus.builder().statusCode("urn:oasis:names:tc:SAML:2.0:status:Success").build())
					.subject(subject)
					.subjectNameIdFormat(SamlNameIdFormat.UNSPECIFIED.getNameIdFormat())
					.attributes(attrBuilder.build());

			return builder.build();

		} catch (Exception e) {
			throw new IllegalStateException("Error :" + e, e);
		}
	}

	private void putAttribute(ImmutableAttributeMap.Builder builder, String familyName, String value) {
		final ImmutableSortedSet<AttributeDefinition<?>> byFriendlyName = eidasAttributeRegistry.getByFriendlyName(familyName);
		final AttributeDefinition<?> attributeDefinition = byFriendlyName.first();
		builder.put(attributeDefinition, value);
	}

	private static String getIssuerIp(String issuerUrl) throws UnknownHostException, MalformedURLException {
		return InetAddress.getByName(new URL(issuerUrl).getHost()).getHostAddress();
	}

	private Optional<ILightRequest> getRemoveCorrelatediLightRequest(@Nonnull final String inResponseToId) {
		Optional<ILightRequest> iLightRequest = Optional.ofNullable(specificMSIdpRequestCorrelationMap.get(inResponseToId))
				.map( StoredMSProxyServiceRequestCorrelationMap.CorrelatedRequestsHolder::getILightRequest);
		if (iLightRequest.isPresent()) {
			specificMSIdpRequestCorrelationMap.remove(inResponseToId);
		}
		return iLightRequest;
	}
}