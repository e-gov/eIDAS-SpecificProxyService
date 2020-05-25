package ee.ria.eidas.proxy.specific.web;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import ee.ria.eidas.proxy.specific.config.SpecificProxyServiceProperties;
import ee.ria.eidas.proxy.specific.error.BadRequestException;
import ee.ria.eidas.proxy.specific.service.SpecificProxyService;
import eu.eidas.auth.commons.EidasParameterKeys;
import eu.eidas.auth.commons.attribute.AttributeDefinition;
import eu.eidas.auth.commons.attribute.AttributeRegistry;
import eu.eidas.auth.commons.attribute.AttributeValue;
import eu.eidas.auth.commons.attribute.ImmutableAttributeMap;
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
import static ee.ria.eidas.proxy.specific.web.IdpResponseController.IdpCallbackRequest.getParameterValue;


@Slf4j
@Controller
public class IdpResponseController {

	public static final String ENDPOINT_IDP_RESPONSE = "/IdpResponse";

	public static final String PARAMETER_TOKEN = "binaryLightToken";

	@Autowired
	private SpecificProxyServiceProperties specificProxyServiceProperties;

	@Autowired
	private SpecificProxyService specificProxyService;

	@Autowired
	private AttributeRegistry eidasAttributeRegistry;

	@GetMapping(value = ENDPOINT_IDP_RESPONSE)
	public ModelAndView processIdpResponse (
				@Validated IdpCallbackRequest idpCallbackRequest,
				Model model) throws SpecificCommunicationException, ServletException, MalformedURLException {

		String state = getParameterValue(idpCallbackRequest.getState());
		String errorCode = getParameterValue(idpCallbackRequest.getError());
		String errorDescription = getParameterValue(idpCallbackRequest.getErrorDescription());
		String oAuthCode = getParameterValue(idpCallbackRequest.getCode());

		if (errorCode == null && oAuthCode == null) {
			throw new BadRequestException("Either error or code parameter is required");
		}

		if (errorCode != null && oAuthCode != null) {
			throw new BadRequestException("Either error or code parameter can be present in a callback request. Both code and error parameters found");
		}


		if ( errorCode != null ) {
			log.info("Handling error callback from Idp: {}", idpCallbackRequest);
			return processIdpErrorResponse(state, errorCode, errorDescription);
		} else {
			log.info("Handling successful authentication callback from Idp: {}", idpCallbackRequest);
			return processIdpAuthenticationResponse(model, state, oAuthCode);
		}

	}

	private ModelAndView processIdpAuthenticationResponse(Model model, String state, String oAuthCode) throws SpecificCommunicationException, ServletException {
		ILightResponse lightResponse = specificProxyService.doDelegatedAuthentication(
				oAuthCode,
				state);

		if (specificProxyServiceProperties.isAskConsent()) {
			return getConsentModelAndView(model, lightResponse);
		} else {

			final BinaryLightToken binaryLightToken = specificProxyService.putResponse(lightResponse);
			final String token = BinaryLightTokenHelper.encodeBinaryLightTokenBase64(binaryLightToken);

			URI redirectUrl = UriComponentsBuilder
					.fromUri(URI.create(specificProxyServiceProperties.getNodeSpecificResponseUrl()))
					.queryParam(EidasParameterKeys.TOKEN.getValue() , token)
					.build().toUri();

			return new ModelAndView("redirect:" + redirectUrl);
		}
	}

	private ModelAndView processIdpErrorResponse(String state, String errorCode, String errorDescription) throws SpecificCommunicationException, MalformedURLException {
		URL redirectUrl = specificProxyService.createIdpAuthenticationFailedRedirectURL(state, errorCode, errorDescription);
		return new ModelAndView("redirect:" + redirectUrl);
	}

	private ModelAndView getConsentModelAndView(Model model, ILightResponse lightResponse) throws SpecificCommunicationException {
		ImmutableMap<AttributeDefinition<?>, ImmutableSet<? extends AttributeValue<?>>> attributes = prepareAttributesToAskConsent(lightResponse);

		model.addAttribute(EidasParameterKeys.ATTRIBUTE_LIST.toString(),attributes);
		model.addAttribute("LoA", lightResponse.getLevelOfAssurance());
		model.addAttribute("redirectUrl", "Consent");
		model.addAttribute(EidasParameterKeys.BINDING.toString(), "GET");
		model.addAttribute(PARAMETER_TOKEN, specificProxyService.createStoreBinaryLightTokenResponseBase64(lightResponse));

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

		public static String getParameterValue(List<String> state) {
			return state != null ? state.get(0) : null;
		}
	}
}