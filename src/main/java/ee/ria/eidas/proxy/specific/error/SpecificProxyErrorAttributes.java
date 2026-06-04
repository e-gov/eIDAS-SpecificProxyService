package ee.ria.eidas.proxy.specific.error;

import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.boot.web.error.ErrorAttributeOptions;
import org.springframework.boot.web.servlet.error.DefaultErrorAttributes;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.validation.BindException;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.validation.ObjectError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.context.request.WebRequest;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static java.lang.String.format;
import static java.lang.String.join;
import static org.springframework.boot.web.error.ErrorAttributeOptions.Include.BINDING_ERRORS;
import static org.springframework.boot.web.error.ErrorAttributeOptions.Include.MESSAGE;

@Slf4j
@Component
public class SpecificProxyErrorAttributes extends DefaultErrorAttributes {
    public static final String INTERNAL_EXCEPTION_MSG = "Something went wrong internally. Please consult server logs " +
            "for further details.";

    @Override
    public Map<String, Object> getErrorAttributes(WebRequest webRequest, ErrorAttributeOptions options) {
        Map<String, Object> attr = super.getErrorAttributes(webRequest, options.including(MESSAGE, BINDING_ERRORS));
        attr.put("locale", webRequest.getLocale().toString());
        attr.put("incidentNumber", MDC.get("traceId"));

        if (HttpStatus.valueOf((int) attr.get("status")).is5xxServerError()) {
            attr.replace("message", INTERNAL_EXCEPTION_MSG);
        }

        Throwable error = getError(webRequest);
        if (error instanceof BindException) {
            attr.replace("errors", formatBindingErrors((BindException) error));
        } else if (error instanceof MethodArgumentNotValidException) {
            attr.replace("errors", formatBindingErrors(((MethodArgumentNotValidException) error).getBindingResult()));
        }
        return attr;
    }

    private String formatBindingErrors(BindingResult bindingResult) {
        List<String> errors = new ArrayList<>();
        for (FieldError fe : bindingResult.getFieldErrors()) {
            errors.add(format("Parameter '%s': %s", fe.getField(), fe.getDefaultMessage()));
        }
        for (ObjectError fe : bindingResult.getGlobalErrors()) {
            errors.add(format("Parameter '%s': %s", fe.getObjectName(), fe.getDefaultMessage()));
        }
        return join("; ", errors);
    }
}
