package ee.ria.eidas.proxy.specific.web;

import ee.ria.eidas.proxy.specific.config.SpecificProxyServiceProperties;
import ee.ria.eidas.proxy.specific.service.SpecificProxyService;
import eu.eidas.auth.commons.EIDASStatusCode;
import eu.eidas.auth.commons.EIDASSubStatusCode;
import eu.eidas.auth.commons.EidasParameterKeys;
import eu.eidas.auth.commons.light.ILightResponse;
import eu.eidas.auth.commons.light.impl.LightResponse;
import eu.eidas.auth.commons.light.impl.ResponseStatus;
import eu.eidas.auth.commons.tx.BinaryLightToken;
import eu.eidas.specificcommunication.BinaryLightTokenHelper;
import eu.eidas.specificcommunication.exception.SpecificCommunicationException;
import eu.eidas.specificcommunication.protocol.SpecificCommunicationService;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.UUID;

@Slf4j
@Controller
public class ConsentController {

	public static final String ENDPOINT_USER_CONSENT = "/Consent";
	public static final String PARAMETER_TOKEN = "binaryLightToken";
	public static final String PARAMETER_CANCEL = "cancel";


	@Autowired
	private SpecificProxyServiceProperties specificProxyServiceProperties;

	@Autowired
	private SpecificProxyService specificProxyService;

	@Autowired
	@Qualifier("springManagedSpecificProxyserviceCommunicationService")
	private SpecificCommunicationService specificCommunicationService;

	@SneakyThrows
	@GetMapping(value = ENDPOINT_USER_CONSENT)
	public ModelAndView consent(final @RequestParam(name = PARAMETER_TOKEN) String lightToken,
								final @RequestParam(name = PARAMETER_CANCEL, required=false) boolean cancel) {

		final ILightResponse lightResponse;

		if (cancel) {
			lightResponse = prepareILightResponseFailure(lightToken);
		} else {
			lightResponse = specificProxyService.getIlightResponse(lightToken);
		}

		final BinaryLightToken binaryLightToken = specificCommunicationService.putResponse(lightResponse);
		final String token = BinaryLightTokenHelper.encodeBinaryLightTokenBase64(binaryLightToken);
		URI redirectUrl = UriComponentsBuilder.fromUri(URI.create(specificProxyServiceProperties.getNodeSpecificResponseUrl()))
				.queryParam(EidasParameterKeys.TOKEN.getValue() , token)
				.build().toUri();

		return new ModelAndView("redirect:" + redirectUrl);
	}

	private ILightResponse prepareILightResponseFailure(final String lightToken) throws SpecificCommunicationException {
		final ILightResponse iLightResponse = specificProxyService.getIlightResponse(lightToken);
		if (iLightResponse == null)
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Error retrieving the corresponding lightResponse");

		return createILightResponseFailure(iLightResponse.getInResponseToId(),
				EIDASStatusCode.RESPONDER_URI, EIDASSubStatusCode.REQUEST_DENIED_URI, "Citizen consent not given.");
	}

	private ILightResponse createILightResponseFailure(
			String inResponseTo,
			EIDASStatusCode eidasStatusCode,
			EIDASSubStatusCode eidasSubStatusCode,
			String statusMessage) {

		final ResponseStatus responseStatus = ResponseStatus.builder()
				.statusCode(eidasStatusCode.toString())
				.subStatusCode(eidasSubStatusCode.toString())
				.statusMessage(statusMessage)
				.failure(true)
				.build();

		return new LightResponse.Builder()
				.id(UUID.randomUUID().toString())
				.inResponseToId(inResponseTo)
				.issuer(specificProxyServiceProperties.getConsentBinaryLightToken().getIssuer())
				.status(responseStatus).build();
	}
}