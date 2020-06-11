package ee.ria.eidas.proxy.specific.error;

import ee.ria.eidas.proxy.specific.config.SpecificProxyServiceProperties;
import ee.ria.eidas.proxy.specific.storage.EidasNodeCommunication;
import eu.eidas.auth.commons.EidasParameterKeys;
import eu.eidas.auth.commons.tx.BinaryLightToken;
import eu.eidas.specificcommunication.BinaryLightTokenHelper;
import eu.eidas.specificcommunication.exception.SpecificCommunicationException;
import lombok.Getter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.validation.ObjectError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;
import org.springframework.web.servlet.view.RedirectView;
import org.springframework.web.util.UriComponentsBuilder;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@ControllerAdvice
public class SpecificProxyServiceExceptionHandler extends ResponseEntityExceptionHandler {

    public static final String MULTIPLE_INSTANCES_OF_PARAMETER_IS_NOT_ALLOWED = "using multiple instances of parameter is not allowed";

    @Autowired
    private SpecificProxyServiceProperties specificProxyServiceProperties;

    @Autowired
    private EidasNodeCommunication eidasNodeCommunication;

    public SpecificProxyServiceExceptionHandler() {
        super();
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex,
            HttpHeaders headers,
            HttpStatus status,
            WebRequest request) {
        List<String> errors = new ArrayList<>();
        for (FieldError error : ex.getBindingResult().getFieldErrors()) {
            errors.add("Parameter " + error.getField() + ": " + error.getDefaultMessage());
        }
        for (ObjectError error : ex.getBindingResult().getGlobalErrors()) {
            errors.add("Parameter " + error.getObjectName() + ": " + error.getDefaultMessage());
        }

        ErrorResponse apiError =
                new ErrorResponse(ex.getLocalizedMessage(), errors);
        return handleExceptionInternal(
                ex, apiError, headers, HttpStatus.BAD_REQUEST, request);
    }

    @Override
    protected ResponseEntity<Object> handleBindException(BindException ex, HttpHeaders headers, HttpStatus status, WebRequest request) {
        List<String> errors = new ArrayList<>();
        for (FieldError error : ex.getBindingResult().getFieldErrors()) {
            errors.add("Parameter " + error.getField() + ": " + error.getDefaultMessage());
        }
        for (ObjectError error : ex.getBindingResult().getGlobalErrors()) {
            errors.add("Parameter " + error.getObjectName() + ": " + error.getDefaultMessage());
        }

        ErrorResponse apiError =
                new ErrorResponse("Bad request", errors);
        return handleExceptionInternal(
                ex, apiError, headers, HttpStatus.BAD_REQUEST, request);
    }

    @ExceptionHandler( {BadRequestException.class})
    public ResponseEntity<Object> handleBindException(BadRequestException ex, WebRequest request) {


        ErrorResponse apiError =
                new ErrorResponse("Bad request", ex.getMessage());
        return handleExceptionInternal(
                ex, apiError, new HttpHeaders(), HttpStatus.BAD_REQUEST, request);
    }

    @ExceptionHandler({ RequestDeniedException.class })
    public RedirectView handleAuthenticationRequestDenied(RequestDeniedException ex, HttpServletRequest request,
                                                          HttpServletResponse response) throws MalformedURLException, SpecificCommunicationException {

        logger.warn(ex.getMessage(), ex);
        BinaryLightToken binaryLightToken = eidasNodeCommunication.putErrorResponse(ex);
        String token = BinaryLightTokenHelper.encodeBinaryLightTokenBase64(binaryLightToken);
        URL redirectUrl = UriComponentsBuilder.fromUri(URI.create(specificProxyServiceProperties.getNodeSpecificResponseUrl()))
                .queryParam(EidasParameterKeys.TOKEN.getValue() , token)
                .build().toUri().toURL();
        return new RedirectView(redirectUrl.toString());
    }

    @ExceptionHandler({ Exception.class })
    public ResponseEntity<Object> handleAll(Exception ex, WebRequest request) {
        logger.error("Server encountered an unexpected error: " + ex.getMessage(), ex);
        ErrorResponse apiError = new ErrorResponse("Internal server error", "Something went wrong internally. Please consult server logs for further details.");
        return new ResponseEntity<>(
                apiError, new HttpHeaders(), HttpStatus.INTERNAL_SERVER_ERROR);
    }

    @Getter
    public static class ErrorResponse {

        private String message;
        private List<String> errors;

        public ErrorResponse(String message, List<String> errors) {
            super();
            this.message = message;
            this.errors = errors;
        }

        public ErrorResponse(String message, String error) {
            super();
            this.message = message;
            errors = Arrays.asList(error);
        }
    }
}