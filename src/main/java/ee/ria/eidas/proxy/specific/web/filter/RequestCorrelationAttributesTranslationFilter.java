package ee.ria.eidas.proxy.specific.web.filter;

import ee.ria.eidas.proxy.specific.config.SpecificProxyServiceProperties;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang.StringUtils;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.info.BuildProperties;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

import static java.util.Arrays.stream;

/**
 * Translates and sets specific MDC attribute as a Servlet request attribute.
 */
@Component
public class RequestCorrelationAttributesTranslationFilter extends OncePerRequestFilter {

	public static final String MDC_ATTRIBUTE_NAME_SESSION_ID = "sessionId";
	public static final String MDC_ATTRIBUTE_NAME_REQUEST_ID = "requestId";
	public static final String MDC_ATTRIBUTE_NAME_VERSION = "serviceVersion";

	@Autowired(required=false)
	private BuildProperties buildProperties;

	@Autowired
	private SpecificProxyServiceProperties specificProxyServiceProperties;

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
			throws ServletException, IOException {

		if (request.getCookies() != null) {
			stream(request.getCookies()).filter(
					c -> c.getName().equals(specificProxyServiceProperties.getWebapp().getSessionIdCookieName())
			).findFirst().ifPresent( c ->  MDC.put(MDC_ATTRIBUTE_NAME_SESSION_ID, c.getValue()));
		}

		String requestId = MDC.get("traceId");
		if (StringUtils.isNotEmpty(requestId)) {
			request.setAttribute(MDC_ATTRIBUTE_NAME_REQUEST_ID, requestId); // make accessible to access-log
			MDC.put(MDC_ATTRIBUTE_NAME_REQUEST_ID, requestId);
		}

		if (buildProperties != null) {
			MDC.put(MDC_ATTRIBUTE_NAME_VERSION, buildProperties.getVersion());
		}

		filterChain.doFilter(request, response);
	}
}