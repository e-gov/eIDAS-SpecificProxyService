package ee.ria.eidas.proxy.specific.web;

import ee.ria.eidas.proxy.specific.service.SpecificProxyService;
import eu.eidas.auth.commons.attribute.AttributeDefinition;
import eu.eidas.auth.commons.exceptions.SecurityEIDASException;
import eu.eidas.auth.commons.light.ILightRequest;
import eu.eidas.specificcommunication.exception.SpecificCommunicationException;
import eu.eidas.specificcommunication.protocol.SpecificCommunicationService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.server.ResponseStatusException;

import javax.servlet.http.HttpServletResponse;
import javax.xml.bind.JAXBException;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Collection;

@Slf4j
@Controller
public class ProxyServiceRequestController {

	public static final String PARAMETER_NAME_TOKEN = "token";
	public static final String ENDPOINT_PROXY_SERVICE_REQUEST = "/ProxyServiceRequest";

	@Autowired
	private SpecificProxyService specificProxyService;

	@Autowired
	@Qualifier("springManagedSpecificProxyserviceCommunicationService")
	private SpecificCommunicationService specificCommunicationService;

	@Autowired
	@Qualifier("attributeRegistry")
	private Collection<AttributeDefinition<?>> attributeRegistry;


	@RequestMapping(value = ENDPOINT_PROXY_SERVICE_REQUEST, method = RequestMethod.GET)
	public void doGet(
			final @RequestParam(name = PARAMETER_NAME_TOKEN, required=true) String base64Token,
			final HttpServletResponse httpServletResponse) throws IOException {
		execute(base64Token, httpServletResponse);
	}

	@RequestMapping(value = ENDPOINT_PROXY_SERVICE_REQUEST, method = RequestMethod.POST)
	public void doPost(
			final @RequestParam(name= PARAMETER_NAME_TOKEN, required=true) String base64Token,
			final HttpServletResponse httpServletResponse) throws IOException {
		execute(base64Token, httpServletResponse);
	}

	private void execute(
			final String tokenBase64,
						 final HttpServletResponse httpServletResponse) throws IOException {

		if (!tokenBase64.matches("^[A-Za-z0-9+/=]{1,1000}$"))
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid token");

		httpServletResponse.sendRedirect(prepareSpecificRequest(tokenBase64).toString());
	}

	private URL prepareSpecificRequest(final String tokenBase64) {

		final ILightRequest originalIlightRequest = getIncomingiLightRequest(tokenBase64, attributeRegistry);

		return createSpecificRequest(originalIlightRequest, null);
	}

	private ILightRequest getIncomingiLightRequest(String tokenBase64, final Collection<AttributeDefinition<?>> registry) {
		try {
			return specificCommunicationService.getAndRemoveRequest(tokenBase64, registry);
		} catch (SpecificCommunicationException | SecurityEIDASException e) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Error retrieving the corresponding lightRequest", e);
		}
	}

	private URL createSpecificRequest(ILightRequest originalIlightRequest, ILightRequest consentedIlightRequest) {
		try {
			return specificProxyService.translateNodeRequest(originalIlightRequest, consentedIlightRequest);
		} catch (JAXBException | MalformedURLException e) {
			throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Error converting the lightRequest instance to OIDC authentication request", e);
		}
	}
}