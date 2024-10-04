package ee.ria.eidas.proxy.specific.web.filter;

import ee.ria.eidas.proxy.specific.config.SpecificProxyServiceProperties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang.StringUtils;
import org.slf4j.MDC;
import org.springframework.boot.info.BuildProperties;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

import static java.util.Arrays.stream;

@Slf4j
@Order(Ordered.HIGHEST_PRECEDENCE)
@RequiredArgsConstructor
public class RequestCorrelationAttributesTranslationFilter extends OncePerRequestFilter {

    public static final String MDC_ATTRIBUTE_NAME_SESSION_ID = "sessionId";
    public static final String MDC_ATTRIBUTE_NAME_VERSION = "serviceVersion";
    public static final String REQUEST_ATTRIBUTE_NAME_REQUEST_ID = "requestId";

    private final BuildProperties buildProperties;
    private final SpecificProxyServiceProperties specificProxyServiceProperties;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        if (request.getCookies() != null) {
            stream(request.getCookies()).filter(
                    c -> c.getName().equals(specificProxyServiceProperties.getWebapp().getSessionIdCookieName())
            ).findFirst().ifPresent(c -> MDC.put(MDC_ATTRIBUTE_NAME_SESSION_ID, c.getValue()));
        }

        // NB! Set traceId also as HttpServletRequest attribute to make it accessible for Tomcat's AccessLogValve
        String requestId = MDC.get("traceId");
        if (StringUtils.isNotEmpty(requestId)) {
            request.setAttribute(REQUEST_ATTRIBUTE_NAME_REQUEST_ID, requestId);
        }

        if (buildProperties != null) {
            MDC.put(MDC_ATTRIBUTE_NAME_VERSION, buildProperties.getVersion());
        }

        filterChain.doFilter(request, response);
    }
}
