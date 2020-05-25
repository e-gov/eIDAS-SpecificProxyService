package ee.ria.eidas.proxy.specific.web;

import ee.ria.eidas.proxy.specific.config.SpecificProxyServiceProperties;
import ee.ria.eidas.proxy.specific.service.SpecificProxyService;
import eu.eidas.auth.commons.EidasParameterKeys;
import eu.eidas.auth.commons.light.ILightResponse;
import eu.eidas.auth.commons.tx.BinaryLightToken;
import eu.eidas.specificcommunication.BinaryLightTokenHelper;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;

@Slf4j
@Controller
@ConditionalOnProperty("eidas.proxy.ask-consent")
public class ConsentController {

	public static final String ENDPOINT_USER_CONSENT = "/Consent";
	public static final String PARAMETER_TOKEN = "binaryLightToken";
	public static final String PARAMETER_CANCEL = "cancel";

	@Autowired
	private SpecificProxyServiceProperties specificProxyServiceProperties;

	@Autowired
	private SpecificProxyService specificProxyService;

	@SneakyThrows
	@GetMapping(value = ENDPOINT_USER_CONSENT)
	public ModelAndView consent(final @RequestParam(name = PARAMETER_TOKEN) String lightToken,
								final @RequestParam(name = PARAMETER_CANCEL, required=false) boolean cancel) {

		final ILightResponse lightResponse;

		if (cancel) {
			lightResponse = specificProxyService.prepareILightResponseFailure(lightToken, "User canceled the authentication process");
		} else {
			lightResponse = specificProxyService.getIlightResponse(lightToken);
		}

		final BinaryLightToken binaryLightToken = specificProxyService.putResponse(lightResponse);
		final String token = BinaryLightTokenHelper.encodeBinaryLightTokenBase64(binaryLightToken);
		URI redirectUrl = UriComponentsBuilder.fromUri(URI.create(specificProxyServiceProperties.getNodeSpecificResponseUrl()))
				.queryParam(EidasParameterKeys.TOKEN.getValue() , token)
				.build().toUri();

		return new ModelAndView("redirect:" + redirectUrl);
	}
}