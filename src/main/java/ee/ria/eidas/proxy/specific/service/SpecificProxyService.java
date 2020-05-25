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
import ee.ria.eidas.proxy.specific.error.RequestDeniedException;
import ee.ria.eidas.proxy.specific.error.BadRequestException;
import ee.ria.eidas.proxy.specific.storage.StoredMSProxyServiceRequestCorrelationMap;
import ee.ria.eidas.proxy.specific.storage.StoredMSProxyServiceRequestCorrelationMap.CorrelatedRequestsHolder;
import eu.eidas.auth.commons.EIDASStatusCode;
import eu.eidas.auth.commons.EIDASSubStatusCode;
import eu.eidas.auth.commons.EidasParameterKeys;
import eu.eidas.auth.commons.attribute.AttributeDefinition;
import eu.eidas.auth.commons.attribute.AttributeRegistry;
import eu.eidas.auth.commons.attribute.ImmutableAttributeMap;
import eu.eidas.auth.commons.exceptions.SecurityEIDASException;
import eu.eidas.auth.commons.light.ILightRequest;
import eu.eidas.auth.commons.light.ILightResponse;
import eu.eidas.auth.commons.light.impl.LightRequest;
import eu.eidas.auth.commons.light.impl.LightResponse;
import eu.eidas.auth.commons.light.impl.ResponseStatus;
import eu.eidas.auth.commons.protocol.eidas.LevelOfAssurance;
import eu.eidas.auth.commons.protocol.impl.SamlNameIdFormat;
import eu.eidas.auth.commons.tx.BinaryLightToken;
import eu.eidas.specificcommunication.BinaryLightTokenHelper;
import eu.eidas.specificcommunication.exception.SpecificCommunicationException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.util.Assert;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.util.UriComponentsBuilder;

import javax.annotation.PostConstruct;
import javax.cache.Cache;
import javax.servlet.ServletException;
import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;

/**
 * SpecificProxyService: provides implementation for interacting with the selected IdP.
 * For the request: it creates the OIDC protocol authentication request to be send to IdP for authentication
 * For the response: it processes the received OIDC callback response, retrieves the person attributes and builds the LightResponse
 *
 */
@Slf4j
@RequiredArgsConstructor
public class SpecificProxyService {

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

    private static LightJAXBCodec codec;

    private final SpecificProxyServiceProperties specificProxyServiceProperties;

    private final AttributeRegistry eidasAttributeRegistry;

    private final Cache<String, String> eidasRequestCommunicationCache;

    private final Cache<String, String> eidasResponseCommunicationCache;

    private final OIDCProviderMetadata oidcProviderMetadata;

    private final Cache<String, CorrelatedRequestsHolder> idpRequestCommunicationCache;

    private final Cache<String, ILightResponse> idpConsentCommunicationCache;

    static {
        try {
            codec = new LightJAXBCodec(JAXBContext.newInstance(LightRequest.class, LightResponse.class,
                    ImmutableAttributeMap.class, AttributeDefinition.class));
        } catch (JAXBException e) {
            log.error("Unable to instantiate in static initializer ",e);
        }
    }

    public URL createIdpRedirect(final String tokenBase64) {

        final ILightRequest incomingLightRequest = getIncomingiLightRequest(tokenBase64, eidasAttributeRegistry.getAttributes());

        return createOidcAuthenticationRequest(incomingLightRequest);
    }

    public URL createRequestDeniedRedirect(RequestDeniedException ex) throws MalformedURLException, SpecificCommunicationException {

        ILightResponse lightResponse = createILightResponseFailure(ex.getInResponseTo(),
                EIDASStatusCode.REQUESTER_URI, EIDASSubStatusCode.REQUEST_DENIED_URI, ex.getMessage(), lightTokenResponseIssuerName);

        final BinaryLightToken binaryLightToken = putResponse(lightResponse);
        final String token = BinaryLightTokenHelper.encodeBinaryLightTokenBase64(binaryLightToken);

        return UriComponentsBuilder.fromUri(URI.create(specificProxyServiceProperties.getNodeSpecificResponseUrl()))
                .queryParam(EidasParameterKeys.TOKEN.getValue() , token)
                .build().toUri().toURL();
    }

    public String createStoreBinaryLightTokenResponseBase64(ILightResponse lightResponse) throws SpecificCommunicationException {
        final BinaryLightToken binaryLightToken = BinaryLightTokenHelper.createBinaryLightToken(
                specificProxyServiceProperties.getConsentBinaryLightToken().getIssuer(),
                specificProxyServiceProperties.getConsentBinaryLightToken().getSecret(),
                specificProxyServiceProperties.getConsentBinaryLightToken().getAlgorithm());
        final String binaryTokenResponse = BinaryLightTokenHelper.encodeBinaryLightTokenBase64(binaryLightToken);
        storeBinaryTokenLightResponse(binaryLightToken.getToken().getId(), lightResponse);
        return binaryTokenResponse;
    }

    public ILightResponse getIlightResponse(String binaryLightTokenBase64) throws SpecificCommunicationException {
        if (StringUtils.isNotEmpty(binaryLightTokenBase64)) {
            final String lightTokenId = BinaryLightTokenHelper.getBinaryLightTokenId(binaryLightTokenBase64,
                    specificProxyServiceProperties.getConsentBinaryLightToken().getSecret(),
                    specificProxyServiceProperties.getConsentBinaryLightToken().getAlgorithm());
            return getRemoveBinaryTokenResponse(lightTokenId);
        } else {
            return null;
        }
    }

    // TODO should not be public
    public BinaryLightToken putRequest(final ILightRequest iLightRequest) throws SpecificCommunicationException {

        final BinaryLightToken binaryLightToken = BinaryLightTokenHelper.createBinaryLightToken(
                lightTokenRequestIssuerName,
                lightTokenRequestSecret,
                lightTokenRequestAlgorithm);
        final String tokenId = binaryLightToken.getToken().getId();
        eidasRequestCommunicationCache.put(tokenId, codec.marshall(iLightRequest));
        return binaryLightToken;
    }

    public ILightRequest getAndRemoveRequest(final String tokenBase64,
                                             final Collection<AttributeDefinition<?>> registry) throws SpecificCommunicationException {
        final String binaryLightTokenId = BinaryLightTokenHelper.getBinaryLightTokenId(tokenBase64,
                lightTokenRequestSecret,
                lightTokenRequestAlgorithm);

        ILightRequest request = unmarshalRequest(registry, binaryLightTokenId);

        if (request == null)
            throw new SpecificCommunicationException("The original request has expired or invalid ID was specified");

        if (!specificProxyServiceProperties.getSupportedSpTypes().contains(request.getSpType()))
            throw new RequestDeniedException("Service provider type not supported. Allowed types: " + specificProxyServiceProperties.getSupportedSpTypes(), request.getId());

        log.info("Lightrequest found from cache for ID: '{}'. Cache: '{}'. Lightrequest: '{}'", binaryLightTokenId, eidasRequestCommunicationCache.getName(),  request.toString());
        return request;
    }

    public ILightResponse prepareFailureResponse(ILightRequest iLightRequest, String statusMessage) {

        return createILightResponseFailure(iLightRequest.getId(),
                EIDASStatusCode.RESPONDER_URI, EIDASSubStatusCode.REQUEST_DENIED_URI, statusMessage, lightTokenResponseIssuerName );
    }

    public ILightResponse prepareILightResponseFailure(final String lightToken, String statusMessage) throws SpecificCommunicationException {
        final ILightResponse iLightResponse = getIlightResponse(lightToken);
        if (iLightResponse == null)
            throw new BadRequestException("Error retrieving the corresponding lightResponse");

        return createILightResponseFailure(iLightResponse.getInResponseToId(),
                EIDASStatusCode.RESPONDER_URI, EIDASSubStatusCode.REQUEST_DENIED_URI, statusMessage, specificProxyServiceProperties.getConsentBinaryLightToken().getIssuer());
    }

    public BinaryLightToken putResponse(final ILightResponse iLightResponse) throws SpecificCommunicationException {
        final BinaryLightToken binaryLightToken = BinaryLightTokenHelper.createBinaryLightToken(
                lightTokenResponseIssuerName, lightTokenResponseSecret, lightTokenResponseAlgorithm);
        final String tokenId = binaryLightToken.getToken().getId();
        eidasResponseCommunicationCache.put(tokenId, codec.marshall(iLightResponse));
        return binaryLightToken;
    }

    private static ILightResponse createILightResponseFailure(
            String inResponseTo,
            EIDASStatusCode eidasStatusCode,
            EIDASSubStatusCode eidasSubStatusCode,
            String statusMessage, String issuer) {

        final ResponseStatus responseStatus = ResponseStatus.builder()
                .statusCode(eidasStatusCode.toString())
                .subStatusCode(eidasSubStatusCode.toString())
                .statusMessage(statusMessage)
                .failure(true)
                .build();

        return new LightResponse.Builder()
                .id(UUID.randomUUID().toString())
                .inResponseToId(inResponseTo)
                .issuer(issuer)
                .status(responseStatus).build();
    }

    private URL createOidcAuthenticationRequest(ILightRequest originalIlightRequest) {
        try {
            final String state = UUID.randomUUID().toString();

            URI oidAuthenticationRequest = UriComponentsBuilder.fromUri(oidcProviderMetadata.getAuthorizationEndpointURI())
                    .queryParam("scope", getScope(originalIlightRequest))
                    .queryParam("response_type", "code")
                    .queryParam("client_id",  specificProxyServiceProperties.getOidc().getClientId())
                    .queryParam("redirect_uri", specificProxyServiceProperties.getOidc().getRedirectUri())
                    .queryParam("acr_values", getLevelOfAssurance(originalIlightRequest))
                    .queryParam("ui_locales", specificProxyServiceProperties.getOidc().getDefaultUiLanguage())
                    .queryParam("state", state)
                    .encode(StandardCharsets.UTF_8).build().toUri();

            final CorrelatedRequestsHolder correlatedRequestsHolder = new CorrelatedRequestsHolder(
                    originalIlightRequest,
                    Collections.singletonMap(state, oidAuthenticationRequest)
            );
            idpRequestCommunicationCache.put(state, correlatedRequestsHolder);
            return oidAuthenticationRequest.toURL();
        } catch (MalformedURLException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Error converting the lightRequest instance to OIDC authentication request", e);
        }
    }

    private void storeBinaryTokenLightResponse(String id, ILightResponse iLightResponse) {
        idpConsentCommunicationCache.put(id, iLightResponse);
    }

    private ILightResponse getRemoveBinaryTokenResponse(String id) {
        final ILightResponse iLightResponse = idpConsentCommunicationCache.get(id);
        if (null != iLightResponse) {
            idpConsentCommunicationCache.remove(id);
        }

        return iLightResponse;
    }

    private ILightRequest getIncomingiLightRequest(String tokenBase64, final Collection<AttributeDefinition<?>> registry) {
        try {
            return getAndRemoveRequest(tokenBase64, registry);
        } catch (SpecificCommunicationException | SecurityEIDASException e) {
            throw new BadRequestException("Invalid token", e);
        }
    }

    private ILightRequest unmarshalRequest(Collection<AttributeDefinition<?>> registry, String binaryLightTokenId) throws SpecificCommunicationException {
        try {
            return codec.unmarshallRequest(eidasRequestCommunicationCache.getAndRemove(binaryLightTokenId),registry);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to unmarshal incoming request! " + e.getMessage(), e);
        }
    }

    private String getScope(ILightRequest originalIlightRequest) {
        log.debug("Start composing OIDC scope value");
        Set<String> scopes = new LinkedHashSet<>();
        scopes.add("openid");
        scopes.addAll(specificProxyServiceProperties.getOidc().getScope());

        for (ImmutableAttributeMap.ImmutableAttributeEntry<?> entry : originalIlightRequest.getRequestedAttributes().entrySet()) {
            if (!specificProxyServiceProperties.getOidc().getAttributeScopeMapping().containsKey(entry.getKey().getFriendlyName())) {
                log.warn("Attribute was requested that has no OIDC scope mapping: " + entry.getKey().getFriendlyName() );
            } else {
                String scope = specificProxyServiceProperties.getOidc().getAttributeScopeMapping().get(entry.getKey().getFriendlyName());
                log.debug("Add attribute scope: " + scope);
                scopes.add(scope);
            }
        }

        return StringUtils.join(scopes, ' ');
    }

    private String getLevelOfAssurance(ILightRequest originalIlightRequest) {
        Assert.notNull(originalIlightRequest.getLevelOfAssurance(), "Mandatory LevelOfAssurance field is missing in LightRequest!");
        LevelOfAssurance loa = LevelOfAssurance.fromString(originalIlightRequest.getLevelOfAssurance());
        if (loa == null) {
            throw new IllegalArgumentException("Invalid level of assurance value. Allowed values: " + Arrays.stream(LevelOfAssurance.values()).map(LevelOfAssurance::getValue).collect(Collectors.joining(", ")) );
        }
        return loa.name().toLowerCase();
    }

    private ILightRequest getRemoveCorrelatediLightRequest(final String inResponseToId) {
        Optional<ILightRequest> iLightRequest = Optional.ofNullable(idpRequestCommunicationCache.get(inResponseToId))
                .map( StoredMSProxyServiceRequestCorrelationMap.CorrelatedRequestsHolder::getILightRequest);
        if (iLightRequest.isPresent()) {
            log.debug("Found and removed LightRequest for id: '{}'", inResponseToId);
            idpRequestCommunicationCache.remove(inResponseToId);
            return iLightRequest.get();
        } else {
            log.warn("Failed to find the original LightRequest for id: '{}' ", inResponseToId);
            return null;
        }
    }

    public URL createIdpAuthenticationFailedRedirectURL(String state, String errorCode, String errorDescription) throws MalformedURLException, SpecificCommunicationException {

        ILightRequest originalLightRequest =  getRemoveCorrelatediLightRequest(state);
        if (originalLightRequest == null) {
            throw new BadRequestException("Invalid state");
        }

        if (isAuthenticationCancelled(errorCode)) {
            final ILightResponse lightResponse = prepareFailureResponse(originalLightRequest, "User canceled the authentication process");
            final BinaryLightToken binaryLightToken = putResponse(lightResponse);
            final String token = BinaryLightTokenHelper.encodeBinaryLightTokenBase64(binaryLightToken);
            URI redirectUrl = UriComponentsBuilder.fromUri(URI.create(specificProxyServiceProperties.getNodeSpecificResponseUrl()))
                    .queryParam(EidasParameterKeys.TOKEN.getValue() , token)
                    .build().toUri();

            return redirectUrl.toURL();
        } else {
            throw new IllegalStateException(String.format("IDP has returned an error (code = '%s', description = '%s')", errorCode, errorDescription));
        }

    }

    public ILightResponse doDelegatedAuthentication(String oAuthCode, String state) throws ServletException {

        ILightRequest originalLightRequest =  getRemoveCorrelatediLightRequest(state);
        if (originalLightRequest == null) {
            throw new BadRequestException("Invalid state");
        }

        final ILightResponse lightResponse;

        try {
            ClientID clientID = new ClientID(specificProxyServiceProperties.getOidc().getClientId());
            ClientAuthentication clientAuth = new ClientSecretBasic(
                    clientID,
                    new Secret(specificProxyServiceProperties.getOidc().getClientSecret())
            );

            AuthorizationCode authorizationCode = new AuthorizationCode(oAuthCode);
            URI callback = new URI(specificProxyServiceProperties.getOidc().getRedirectUri());
            AuthorizationGrant codeGrant = new AuthorizationCodeGrant(authorizationCode, callback);

            URI tokenEndpoint = oidcProviderMetadata.getTokenEndpointURI();
            TokenRequest request = new TokenRequest(tokenEndpoint, clientAuth, codeGrant, null, null, Collections.singletonMap("state", Collections.singletonList(state)));
            log.info("Request id-token from {} ", request.getEndpointURI());
            TokenResponse tokenResponse = OIDCTokenResponseParser.parse(request.toHTTPRequest().send());

            if (!tokenResponse.indicatesSuccess()) {
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
                lightResponse = translateToLightResponse(state, claims, originalLightRequest);
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

    public ILightResponse translateToLightResponse(String state, ClaimsSet claimSet, ILightRequest iLightRequest) {
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

    private boolean isAuthenticationCancelled(String errorCode) {
        return errorCode.equals(specificProxyServiceProperties.getOidc().getErrorCodeUserCancel());
    }
}
