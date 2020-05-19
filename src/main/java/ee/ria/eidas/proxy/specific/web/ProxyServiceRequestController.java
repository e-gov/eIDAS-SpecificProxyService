package ee.ria.eidas.proxy.specific.web;

import ee.ria.eidas.proxy.specific.service.SpecificProxyService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.ModelAndView;

import java.net.URL;
import java.util.List;

@Slf4j
@Validated
@Controller
public class ProxyServiceRequestController {

	public static final String PARAMETER_NAME_TOKEN = "token";
	public static final String ENDPOINT_PROXY_SERVICE_REQUEST = "/ProxyServiceRequest";

	@Autowired
	private SpecificProxyService specificProxyService;

	@GetMapping(value = ENDPOINT_PROXY_SERVICE_REQUEST)
	public ModelAndView get(
			final @RequestParam(name = PARAMETER_NAME_TOKEN) List<String> base64Token) {
		return execute( base64Token );
	}

	@PostMapping(value = ENDPOINT_PROXY_SERVICE_REQUEST)
	public ModelAndView post(
			final @RequestParam(name= PARAMETER_NAME_TOKEN) List<String> base64Token) {
		return execute( base64Token );
	}

	private ModelAndView execute(final List<String> base64Tokens) {

		String base64Token = validateRequestParams(base64Tokens);

		URL redirectURL = specificProxyService.createIdpRedirect(base64Token);

		return new ModelAndView("redirect:" + redirectURL.toString());
	}

	private String validateRequestParams(List<String> base64Tokens) {
		if (base64Tokens.size() > 1)
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Multiple token parameters not allowed");

		String base64Token = base64Tokens.get(0);
		if (!base64Token.matches("^[A-Za-z0-9+/=]{1,1000}$"))
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid token");

		return base64Token;
	}
}