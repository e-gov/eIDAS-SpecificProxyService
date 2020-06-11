package ee.ria.eidas.proxy.specific.web;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import ee.ria.eidas.proxy.specific.config.SpecificProxyServiceProperties;
import ee.ria.eidas.proxy.specific.error.BadRequestException;
import ee.ria.eidas.proxy.specific.error.RequestDeniedException;
import ee.ria.eidas.proxy.specific.service.SpecificProxyService;
import ee.ria.eidas.proxy.specific.storage.EidasNodeCommunication;
import ee.ria.eidas.proxy.specific.storage.SpecificProxyServiceCommunication;
import eu.eidas.auth.commons.EidasParameterKeys;
import eu.eidas.auth.commons.attribute.AttributeDefinition;
import eu.eidas.auth.commons.attribute.AttributeRegistry;
import eu.eidas.auth.commons.attribute.AttributeValue;
import eu.eidas.auth.commons.attribute.ImmutableAttributeMap;
import eu.eidas.auth.commons.light.ILightRequest;
import eu.eidas.auth.commons.light.ILightResponse;
import eu.eidas.auth.commons.tx.BinaryLightToken;
import eu.eidas.specificcommunication.BinaryLightTokenHelper;
import eu.eidas.specificcommunication.exception.SpecificCommunicationException;
import lombok.Data;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.util.UriComponentsBuilder;

import javax.servlet.ServletException;
import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.Size;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.util.List;

import static ee.ria.eidas.proxy.specific.error.SpecificProxyServiceExceptionHandler.MULTIPLE_INSTANCES_OF_PARAMETER_IS_NOT_ALLOWED;
import static ee.ria.eidas.proxy.specific.web.filter.HttpRequestHelper.getStringParameterValue;


@Slf4j
@Controller
public class IdpResponseController {

	public static final String ENDPOINT_IDP_RESPONSE = "/IdpResponse";

	public static final String PARAMETER_TOKEN = "token";

	@Autowired
	private SpecificProxyServiceProperties specificProxyServiceProperties;

	@Autowired
	private SpecificProxyService specificProxyService;

	@Autowired
	private EidasNodeCommunication eidasNodeCommunication;

	@Autowired
	private SpecificProxyServiceCommunication specificProxyServiceCommunication;

	@Autowired
	private AttributeRegistry eidasAttributeRegistry;

	@GetMapping(value = ENDPOINT_IDP_RESPONSE)
	public ModelAndView processIdpResponse (
				@Validated IdpCallbackRequest idpCallbackRequest,
				Model model) throws SpecificCommunicationException, MalformedURLException {

		String state = getStringParameterValue(idpCallbackRequest.getState());
		String errorCode = getStringParameterValue(idpCallbackRequest.getError());
		String errorDescription = getStringParameterValue(idpCallbackRequest.getErrorDescription());
		String oAuthCode = getStringParameterValue(idpCallbackRequest.getCode());

		if (errorCode == null && oAuthCode == null) {
			throw new BadRequestException("Either error or code parameter is required");
		}

		if (errorCode != null && oAuthCode != null) {
			throw new BadRequestException("Either error or code parameter can be present in a callback request. Both code and error parameters found");
		}

		ILightRequest originalLightRequest = specificProxyServiceCommunication.getAndRemoveIdpRequest(state);
		if (originalLightRequest == null) {
			throw new BadRequestException("Invalid state");
		}

		if ( errorCode != null ) {
			if (isAuthenticationCancelled(errorCode)) {
				throw new RequestDeniedException("User canceled the authentication process", originalLightRequest.getId());
			} else {
				throw new IllegalStateException(String.format("OIDC authentication request has returned an error (code = '%s', description = '%s')", errorCode, errorDescription));
			}
		}

		log.info("Handling successful authentication callback from Idp: {}", idpCallbackRequest);
		ILightResponse lightResponse = specificProxyService.queryIdpForRequestedAttributes(
				oAuthCode,
				originalLightRequest);

		return processIdpAuthenticationResponse(model, lightResponse);
	}

	private ModelAndView processIdpAuthenticationResponse(Model model, ILightResponse lightResponse) throws SpecificCommunicationException, MalformedURLException {

		if (specificProxyServiceProperties.isAskConsent()) {
			return getConsentModelAndView(model, lightResponse);
		} else {
			BinaryLightToken binaryLightToken = eidasNodeCommunication.putResponse(lightResponse);
			String token = BinaryLightTokenHelper.encodeBinaryLightTokenBase64(binaryLightToken);
			URL redirectUrl = UriComponentsBuilder
					.fromUri(URI.create(specificProxyServiceProperties.getNodeSpecificResponseUrl()))
					.queryParam(EidasParameterKeys.TOKEN.getValue() , token)
					.build().toUri().toURL();
			return new ModelAndView("redirect:" + redirectUrl);
		}
	}

	private ModelAndView getConsentModelAndView(Model model, ILightResponse lightResponse) throws SpecificCommunicationException {
		ImmutableMap<AttributeDefinition<?>, ImmutableSet<? extends AttributeValue<?>>> attributes = prepareAttributesToAskConsent(lightResponse);

		String base64Token = BinaryLightTokenHelper.encodeBinaryLightTokenBase64(specificProxyServiceCommunication.putPendingLightResponse(lightResponse));

		model.addAttribute(EidasParameterKeys.ATTRIBUTE_LIST.toString(),attributes);
		model.addAttribute("LoA", lightResponse.getLevelOfAssurance());
		model.addAttribute("redirectUrl", "Consent");
		model.addAttribute(EidasParameterKeys.BINDING.toString(), "GET");
		model.addAttribute(PARAMETER_TOKEN, base64Token);

		return new ModelAndView("citizenConsentResponse");
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

	private boolean isAuthenticationCancelled(String errorCode) {
		return errorCode.equals(specificProxyServiceProperties.getOidc().getErrorCodeUserCancel());
	}

	@Data
	@ToString
	public static class IdpCallbackRequest {
		@NotEmpty
		@Size(max = 1, message = MULTIPLE_INSTANCES_OF_PARAMETER_IS_NOT_ALLOWED)
		private List<String> state;

		@Size(max = 1, message = MULTIPLE_INSTANCES_OF_PARAMETER_IS_NOT_ALLOWED)
		private List<String> code;

		@Size(max = 1, message = MULTIPLE_INSTANCES_OF_PARAMETER_IS_NOT_ALLOWED)
		private List<String> error;

		@Size(max = 1, message = MULTIPLE_INSTANCES_OF_PARAMETER_IS_NOT_ALLOWED)
		private List<String> errorDescription;
	}
}