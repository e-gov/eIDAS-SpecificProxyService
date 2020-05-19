package ee.ria.eidas.proxy.specific.error;

import ee.ria.eidas.proxy.specific.service.SpecificProxyService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.handler.AbstractHandlerExceptionResolver;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.net.URL;

// TODO debug režiimis tehnililine lisainfo väljastada

@Component
public class ExceptionResolver extends AbstractHandlerExceptionResolver {

    @Autowired
    private SpecificProxyService specificProxyService;
 
    @Override
    protected ModelAndView doResolveException(
            HttpServletRequest request, HttpServletResponse response,
            Object handler, Exception ex) {

        try {
            if (ex instanceof IllegalArgumentException) {
                response.sendError(HttpServletResponse.SC_BAD_REQUEST, ex.getMessage());
                return new ModelAndView();
            } else if (ex instanceof AuthenticationRequestDeniedException) {
                logger.info(ex.getMessage(), ex);
                URL redirectUrl = specificProxyService.createRequestDeniedRedirect((AuthenticationRequestDeniedException)ex);
                return new ModelAndView("redirect:" + redirectUrl);
            } else {
                logger.error(ex.getMessage(), ex);
                response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Something went wrong internally. Please consult server logs for further details.");
                return new ModelAndView();
            }

        } catch (Exception handlerException) {
            logger.warn("Handling of [" + ex.getClass().getName() + "] resulted in Exception", handlerException);
        }
        return null;
    }
}