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

import static javax.servlet.http.HttpServletResponse.SC_METHOD_NOT_ALLOWED;

public class AllowedHttpMethodsFilter extends OncePerRequestFilter {

    @Getter
    private final List<HttpMethod> allowedMethods;

    public AllowedHttpMethodsFilter(List<HttpMethod> allowedMethods) {
        this.allowedMethods = allowedMethods;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        if (allowedMethods != null && !allowedMethods.contains(HttpMethod.resolve(request.getMethod()))) {
            response.sendError(SC_METHOD_NOT_ALLOWED, String.format("Request method '%s' not supported", request.getMethod()));
        } else {
            filterChain.doFilter(request, response);
        }
    }
}