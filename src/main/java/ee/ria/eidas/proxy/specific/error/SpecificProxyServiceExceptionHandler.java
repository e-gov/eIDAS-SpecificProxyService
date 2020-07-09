package ee.ria.eidas.proxy.specific.error;

import ee.ria.eidas.proxy.specific.config.SpecificProxyServiceProperties;
import ee.ria.eidas.proxy.specific.storage.EidasNodeCommunication;
import eu.eidas.auth.commons.EidasParameterKeys;
import eu.eidas.auth.commons.tx.BinaryLightToken;
import eu.eidas.specificcommunication.BinaryLightTokenHelper;
import eu.eidas.specificcommunication.exception.SpecificCommunicationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.BindException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.view.RedirectView;

import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;

import static java.lang.String.format;
import static org.springframework.web.util.UriComponentsBuilder.fromUri;

@Slf4j
@ControllerAdvice
public class SpecificProxyServiceExceptionHandler {
    public static final String MULTIPLE_INSTANCES_OF_PARAMETER_IS_NOT_ALLOWED = "using multiple instances of parameter is not allowed";
    public static final String BAD_REQUEST_ERROR_MESSAGE = "Bad request: %s";

    @Autowired
    private SpecificProxyServiceProperties specificProxyServiceProperties;

    @Autowired
    private EidasNodeCommunication eidasNodeCommunication;

    @ExceptionHandler({RequestDeniedException.class})
    public RedirectView handleRequestDeniedException(RequestDeniedException ex) throws MalformedURLException,
            SpecificCommunicationException {
        BinaryLightToken binaryLightToken = eidasNodeCommunication.putErrorResponse(ex);
        String token = BinaryLightTokenHelper.encodeBinaryLightTokenBase64(binaryLightToken);
        URL redirectUrl = fromUri(URI.create(specificProxyServiceProperties.getNodeSpecificResponseUrl()))
                .queryParam(EidasParameterKeys.TOKEN.getValue(), token)
                .build().toUri().toURL();
        return new RedirectView(redirectUrl.toString());
    }

    @ExceptionHandler({BindException.class})
    public ModelAndView handleBindException(BindException ex, HttpServletResponse response) throws IOException {
        log.error(format(BAD_REQUEST_ERROR_MESSAGE, ex.getMessage()));
        response.sendError(HttpServletResponse.SC_BAD_REQUEST);
        return new ModelAndView();
    }

    @ExceptionHandler({BadRequestException.class})
    public ModelAndView handleBadRequestException(BadRequestException ex, HttpServletResponse response) throws IOException {
        log.error(format(BAD_REQUEST_ERROR_MESSAGE, ex.getMessage()));
        response.sendError(HttpServletResponse.SC_BAD_REQUEST);
        return new ModelAndView();
    }

    @ExceptionHandler({Exception.class})
    public void handleAll(Exception ex) throws Exception {
        log.error("Server encountered an unexpected error: {}", ex.getMessage(), ex);
        throw ex;
    }
}