package ee.ria.eidas.proxy.specific.web;

import ee.ria.eidas.proxy.specific.service.SpecificProxyService;
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
import java.net.URL;
import java.util.List;

import static ee.ria.eidas.proxy.specific.error.SpecificProxyServiceExceptionHandler.MULTIPLE_INSTANCES_OF_PARAMETER_IS_NOT_ALLOWED;

@Slf4j
@Validated
@Controller
public class ProxyServiceRequestController {

	public static final String ENDPOINT_PROXY_SERVICE_REQUEST = "/ProxyServiceRequest";

	@Autowired
	private SpecificProxyService specificProxyService;

	@GetMapping(value = ENDPOINT_PROXY_SERVICE_REQUEST)
	public ModelAndView get( @Validated RequestParameters request ) {
		return execute( request.getToken().get(0)  );
	}

	@PostMapping(value = ENDPOINT_PROXY_SERVICE_REQUEST)
	public ModelAndView post( @Validated RequestParameters request ) {
		return execute( request.getToken().get(0) );
	}

	private ModelAndView execute(String base64Token) {

		URL redirectURL = specificProxyService.createIdpRedirect(base64Token);

		return new ModelAndView("redirect:" + redirectURL.toString());
	}

	@Data
	public static class RequestParameters {

		@NotNull
		@Size(max = 1, message = MULTIPLE_INSTANCES_OF_PARAMETER_IS_NOT_ALLOWED)
		private List<@Pattern(regexp = "^[A-Za-z0-9+/=]{1,1000}$", message = "only base64 characters allowed") String> token;
	}
}