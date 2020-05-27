package ee.ria.eidas.proxy.specific.web;

import ee.ria.eidas.proxy.specific.config.SpecificProxyServiceProperties;
import ee.ria.eidas.proxy.specific.error.BadRequestException;
import ee.ria.eidas.proxy.specific.error.RequestDeniedException;
import ee.ria.eidas.proxy.specific.storage.EidasNodeCommunication;
import ee.ria.eidas.proxy.specific.storage.SpecificProxyServiceCommunication;
import eu.eidas.auth.commons.EidasParameterKeys;
import eu.eidas.auth.commons.light.ILightResponse;
import eu.eidas.auth.commons.tx.BinaryLightToken;
import eu.eidas.specificcommunication.BinaryLightTokenHelper;
import eu.eidas.specificcommunication.exception.SpecificCommunicationException;
import lombok.Data;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Controller;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.util.UriComponentsBuilder;

import javax.validation.constraints.NotNull;
import javax.validation.constraints.Pattern;
import javax.validation.constraints.Size;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.util.List;

import static ee.ria.eidas.proxy.specific.error.SpecificProxyServiceExceptionHandler.MULTIPLE_INSTANCES_OF_PARAMETER_IS_NOT_ALLOWED;
import static ee.ria.eidas.proxy.specific.web.filter.HttpRequestHelper.getBooleanParameterValue;
import static ee.ria.eidas.proxy.specific.web.filter.HttpRequestHelper.getStringParameterValue;

@Slf4j
@Controller
@ConditionalOnProperty("eidas.proxy.ask-consent")
public class ConsentController {

	public static final String ENDPOINT_USER_CONSENT = "/Consent";

	@Autowired
	private SpecificProxyServiceProperties specificProxyServiceProperties;

	@Autowired
	private EidasNodeCommunication eidasNodeCommunication;

	@Autowired
	private SpecificProxyServiceCommunication specificProxyServiceCommunication;

	@GetMapping(value = ENDPOINT_USER_CONSENT)
	public ModelAndView consent(@Validated RequestParameters request) throws SpecificCommunicationException, MalformedURLException {

		String tokenBase64 = getStringParameterValue(request.getToken());
		boolean cancel = getBooleanParameterValue(request.getCancel(), false);

		final ILightResponse originalLightResponse = specificProxyServiceCommunication.getAndRemovePendingLightResponse(tokenBase64);
		if (originalLightResponse == null)
			throw new BadRequestException("Invalid token");

		if (cancel) {
			throw new RequestDeniedException("User canceled the authentication process", originalLightResponse.getInResponseToId());
		}

		BinaryLightToken binaryLightToken = eidasNodeCommunication.putResponse(originalLightResponse);
		String token = BinaryLightTokenHelper.encodeBinaryLightTokenBase64(binaryLightToken);
		URL redirectUrl = UriComponentsBuilder.fromUri(URI.create(specificProxyServiceProperties.getNodeSpecificResponseUrl()))
					.queryParam(EidasParameterKeys.TOKEN.getValue() , token)
					.build().toUri().toURL();

		return new ModelAndView("redirect:" + redirectUrl);
	}

	@Data
	public static class RequestParameters {

		@NotNull
		@Size(max = 1, message = MULTIPLE_INSTANCES_OF_PARAMETER_IS_NOT_ALLOWED)
		private List<@Pattern(regexp = "^[A-Za-z0-9+/=]{1,1000}$", message = "only base64 characters allowed") String> token;

		private List<Boolean> cancel;
	}
}