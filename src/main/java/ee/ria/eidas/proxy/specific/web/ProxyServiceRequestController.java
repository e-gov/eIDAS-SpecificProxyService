package ee.ria.eidas.proxy.specific.web;

import ee.ria.eidas.proxy.specific.config.SpecificProxyServiceProperties;
import ee.ria.eidas.proxy.specific.error.RequestDeniedException;
import ee.ria.eidas.proxy.specific.service.SpecificProxyService;
import ee.ria.eidas.proxy.specific.storage.EidasNodeCommunication;
import ee.ria.eidas.proxy.specific.storage.SpecificProxyServiceCommunication;
import eu.eidas.auth.commons.light.ILightRequest;
import eu.eidas.specificcommunication.exception.SpecificCommunicationException;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.ModelAndView;

import javax.validation.constraints.NotNull;
import javax.validation.constraints.Pattern;
import javax.validation.constraints.Size;
import java.util.List;

import static ee.ria.eidas.proxy.specific.error.SpecificProxyServiceExceptionHandler.MULTIPLE_INSTANCES_OF_PARAMETER_IS_NOT_ALLOWED;
import static ee.ria.eidas.proxy.specific.web.filter.HttpRequestHelper.getStringParameterValue;

@Slf4j
@Validated
@Controller
public class ProxyServiceRequestController {

	public static final String ENDPOINT_PROXY_SERVICE_REQUEST = "/ProxyServiceRequest";

	@Autowired
	private SpecificProxyServiceProperties specificProxyServiceProperties;

	@Autowired
	private SpecificProxyService specificProxyService;

	@Autowired
	private EidasNodeCommunication eidasNodeCommunication;

	@Autowired
	private SpecificProxyServiceCommunication specificProxyServiceCommunication;

	@GetMapping(value = ENDPOINT_PROXY_SERVICE_REQUEST)
	public ModelAndView get( @Validated RequestParameters request ) throws SpecificCommunicationException {
		return execute(request);
	}

	@PostMapping(value = ENDPOINT_PROXY_SERVICE_REQUEST)
	public ModelAndView post( @Validated RequestParameters request ) throws SpecificCommunicationException {
		return execute(request);
	}

	private ModelAndView execute(RequestParameters request) throws SpecificCommunicationException {

		String tokenBase64 = getStringParameterValue(request.getToken());

		ILightRequest incomingLightRequest = eidasNodeCommunication.getAndRemoveRequest(tokenBase64);

		if (!specificProxyServiceProperties.getSupportedSpTypes().contains(incomingLightRequest.getSpType()))
			throw new RequestDeniedException("Service provider type not supported. Allowed types: " + specificProxyServiceProperties.getSupportedSpTypes(), incomingLightRequest.getId());

		SpecificProxyServiceCommunication.CorrelatedRequestsHolder correlatedRequestsHolder = specificProxyService.createOidcAuthenticationRequest(incomingLightRequest);

		specificProxyServiceCommunication.putIdpRequest(correlatedRequestsHolder.getIdpAuthenticationRequestState(), correlatedRequestsHolder);

		return new ModelAndView("redirect:" + correlatedRequestsHolder.getIdpAuthenticationRequest());
	}

	@Data
	public static class RequestParameters {

		@NotNull
		@Size(max = 1, message = MULTIPLE_INSTANCES_OF_PARAMETER_IS_NOT_ALLOWED)
		private List<@Pattern(regexp = "^[A-Za-z0-9+/=]{1,1000}$", message = "only base64 characters allowed") String> token;
	}
}