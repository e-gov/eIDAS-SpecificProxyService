package ee.ria.eidas.proxy.specific.web.filter;

import lombok.Getter;
import org.springframework.http.HttpMethod;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;

public class DisabledHttpMethodsFilter extends OncePerRequestFilter {

	@Getter
	private final List<HttpMethod> disabledMethods;

	public DisabledHttpMethodsFilter(List<HttpMethod> disabledMethods) {
		this.disabledMethods = disabledMethods;
	}

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
			throws ServletException, IOException {
		if (disabledMethods.contains(HttpMethod.resolve(request.getMethod()))) {
			response.sendError(HttpServletResponse.SC_METHOD_NOT_ALLOWED, String.format("Request method '%s' not supported", request.getMethod()));
		} else {
			filterChain.doFilter(request, response);
		}
	}
}