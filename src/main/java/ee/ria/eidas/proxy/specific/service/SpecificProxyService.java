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
import com.jayway.jsonpath.JsonPath;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.proc.BadJOSEException;
import com.nimbusds.jwt.JWT;
import com.nimbusds.oauth2.sdk.*;
import com.nimbusds.oauth2.sdk.auth.ClientAuthentication;
import com.nimbusds.oauth2.sdk.auth.ClientSecretBasic;
import com.nimbusds.oauth2.sdk.auth.Secret;
import com.nimbusds.oauth2.sdk.http.HTTPRequest;
import com.nimbusds.oauth2.sdk.id.ClientID;
import com.nimbusds.openid.connect.sdk.OIDCTokenResponse;
import com.nimbusds.openid.connect.sdk.OIDCTokenResponseParser;
import com.nimbusds.openid.connect.sdk.claims.ClaimsSet;
import com.nimbusds.openid.connect.sdk.op.OIDCProviderMetadata;
import com.nimbusds.openid.connect.sdk.validators.IDTokenValidator;
import ee.ria.eidas.proxy.specific.config.SpecificProxyServiceProperties;
import ee.ria.eidas.proxy.specific.config.SpecificProxyServiceProperties.IdTokenClaimMappingProperties;
import ee.ria.eidas.proxy.specific.storage.SpecificProxyServiceCommunication;
import eu.eidas.auth.commons.attribute.AttributeDefinition;
import eu.eidas.auth.commons.attribute.AttributeRegistry;
import eu.eidas.auth.commons.attribute.ImmutableAttributeMap;
import eu.eidas.auth.commons.light.ILightRequest;
import eu.eidas.auth.commons.light.ILightResponse;
import eu.eidas.auth.commons.light.impl.LightResponse;
import eu.eidas.auth.commons.light.impl.ResponseStatus;
import eu.eidas.auth.commons.protocol.eidas.LevelOfAssurance;
import eu.eidas.auth.commons.protocol.eidas.spec.EidasSpec;
import eu.eidas.auth.commons.protocol.impl.SamlNameIdFormat;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import net.minidev.json.JSONObject;
import org.apache.commons.lang.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.springframework.util.Assert;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;
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

    private final SpecificProxyServiceProperties specificProxyServiceProperties;

    private final OIDCProviderMetadata oidcProviderMetadata;

    private final IDTokenValidator idTokenValidator;

    private final AttributeRegistry eidasAttributeRegistry;

    @SneakyThrows
    public SpecificProxyServiceCommunication.CorrelatedRequestsHolder createOidcAuthenticationRequest(ILightRequest originalIlightRequest) {
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

        return createCorrelatedRequestsHolder(originalIlightRequest, oidAuthenticationRequest.toURL(), state);
    }

    @SneakyThrows
    public ILightResponse queryIdpForRequestedAttributes(String oAuthCode, ILightRequest originalLightRequest) {

        JWT idToken = getIdToken(oAuthCode, new ClientID(specificProxyServiceProperties.getOidc().getClientId()));
        log.info("ID-TOKEN: " + idToken.getParsedString());

        try {
            ClaimsSet claims = idTokenValidator.validate(idToken, null);
            log.debug("OIDC response successfully verified!");
            ILightResponse lightResponse = translateToLightResponse(claims, originalLightRequest, specificProxyServiceProperties.getOidc().getResponseClaimMapping());
            log.debug("LightResponse for eIDAS-Proxy service: " + lightResponse.toString());
            return lightResponse;
        } catch (BadJOSEException | JOSEException e) {
            throw new IllegalStateException(String.format("Error when validating id_token! %s", e.getMessage()), e);
        }
    }

    private JWT getIdToken(String oAuthCode, ClientID clientID) throws URISyntaxException {
        ClientAuthentication clientAuth = new ClientSecretBasic(
                clientID,
                new Secret(specificProxyServiceProperties.getOidc().getClientSecret())
        );

        URI tokenEndpoint = oidcProviderMetadata.getTokenEndpointURI();
        TokenRequest request = new TokenRequest(oidcProviderMetadata.getTokenEndpointURI(), clientAuth, getAuthorizationGrant(oAuthCode), null, null, null);
        log.info("Request id-token from {} ", request.getEndpointURI());
        OIDCTokenResponse successResponse = getOidcTokenResponse(tokenEndpoint, request);

        return successResponse.getOIDCTokens().getIDToken();
    }

    private AuthorizationGrant getAuthorizationGrant(String oAuthCode) throws URISyntaxException {
        AuthorizationCode authorizationCode = new AuthorizationCode(oAuthCode);
        URI callback = new URI(specificProxyServiceProperties.getOidc().getRedirectUri());
        return new AuthorizationCodeGrant(authorizationCode, callback);
    }

    private OIDCTokenResponse getOidcTokenResponse(URI tokenEndpoint, TokenRequest request) {
        try {

            HTTPRequest httpRequest = request.toHTTPRequest();
            httpRequest.setConnectTimeout(specificProxyServiceProperties.getOidc().getConnectTimeoutInMilliseconds());
            httpRequest.setReadTimeout(specificProxyServiceProperties.getOidc().getReadTimeoutInMilliseconds());
            TokenResponse tokenResponse = OIDCTokenResponseParser.parse(httpRequest.send());
            if (tokenResponse.indicatesSuccess()) {
                return (OIDCTokenResponse)tokenResponse.toSuccessResponse();
            } else {
                TokenErrorResponse errorResponse = tokenResponse.toErrorResponse();
                log.error("Token endpoint " + tokenEndpoint + " returned an error: " + errorResponse.getErrorObject());
                throw new IllegalStateException("OIDC token request returned an error! " + errorResponse.getErrorObject());
            }

        } catch (IOException e) {
            throw new IllegalStateException(String.format("IO error while accessing OIDC token endpoint! %s", e.getMessage()), e);
        } catch (ParseException e) {
            throw new IllegalStateException(String.format("Invalid OIDC token endpoint response! %s", e.getMessage()) , e);
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

    private ILightResponse translateToLightResponse(ClaimsSet claimSet, ILightRequest originalLightRequest, IdTokenClaimMappingProperties mappingProperties) throws MalformedURLException, UnknownHostException {
        log.info("JWT (claims): " + claimSet.toJSONString());

        JSONObject claims = claimSet.toJSONObject();
        String responseId = JsonPath.read(claims, mappingProperties.getId());
        LevelOfAssurance loa = LevelOfAssurance.valueOf(StringUtils.upperCase(JsonPath.read(claims, mappingProperties.getAcr())));
        String issuer = JsonPath.read(claims, mappingProperties.getIssuer());
        ImmutableAttributeMap attributes = getAttributes(claims, mappingProperties);

        if (loa.numericValue() < LevelOfAssurance.fromString(originalLightRequest.getLevelOfAssurance()).numericValue()) {
            throw new IllegalStateException(String.format("Invalid level of assurance in IDP response. Authentication was requested with level '%s', but id-token contains level '%s'.", originalLightRequest.getLevelOfAssurance(), loa));
        }

        final LightResponse.Builder builder = LightResponse.builder()
                .id(responseId)
                .ipAddress(getIssuerIp(specificProxyServiceProperties.getOidc().getIssuerUrl()))
                .inResponseToId(originalLightRequest.getId())
                .issuer(issuer)
                .levelOfAssurance(loa.stringValue())
                .relayState(originalLightRequest.getRelayState())
                .status(ResponseStatus.builder().statusCode("urn:oasis:names:tc:SAML:2.0:status:Success").build())
                .subject(attributes.getFirstAttributeValue(EidasSpec.Definitions.PERSON_IDENTIFIER).toString())
                .subjectNameIdFormat(SamlNameIdFormat.UNSPECIFIED.getNameIdFormat())
                .attributes(attributes);

        return builder.build();
    }

    @NotNull
    private ImmutableAttributeMap getAttributes(JSONObject claims, IdTokenClaimMappingProperties mappingProperties) {
        ImmutableAttributeMap.Builder attrBuilder = ImmutableAttributeMap.builder();
        for ( Map.Entry<String, String> entry : mappingProperties.getAttributes().entrySet()) {
            putAttribute(attrBuilder, entry.getKey(), JsonPath.read(claims, entry.getValue()));
        }
        return attrBuilder.build();
    }

    private void putAttribute(ImmutableAttributeMap.Builder builder, String familyName, String value) {
        final ImmutableSortedSet<AttributeDefinition<?>> byFriendlyName = eidasAttributeRegistry.getByFriendlyName(familyName);
        final AttributeDefinition<?> attributeDefinition = byFriendlyName.first();
        builder.put(attributeDefinition, value);
    }

    private static String getIssuerIp(String issuerUrl) throws UnknownHostException, MalformedURLException {
        return InetAddress.getByName(new URL(issuerUrl).getHost()).getHostAddress();
    }

    private SpecificProxyServiceCommunication.CorrelatedRequestsHolder createCorrelatedRequestsHolder(ILightRequest incomingLightRequest, URL redirectUrl, String state) {
        return new SpecificProxyServiceCommunication.CorrelatedRequestsHolder(
                incomingLightRequest,
                Collections.singletonMap(state, redirectUrl)
        );
    }

    private String getLevelOfAssurance(ILightRequest originalIlightRequest) {
        Assert.notNull(originalIlightRequest.getLevelOfAssurance(), "Mandatory LevelOfAssurance field is missing in LightRequest!");
        LevelOfAssurance loa = LevelOfAssurance.fromString(originalIlightRequest.getLevelOfAssurance());
        if (loa == null) {
            throw new IllegalArgumentException("Invalid level of assurance value. Allowed values: " + Arrays.stream(LevelOfAssurance.values()).map(LevelOfAssurance::getValue).collect(Collectors.joining(", ")) );
        }
        return loa.name().toLowerCase();
    }
}
