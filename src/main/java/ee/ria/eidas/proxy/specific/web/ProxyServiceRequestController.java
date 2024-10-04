package ee.ria.eidas.proxy.specific.web;

import ee.ria.eidas.proxy.specific.config.SpecificProxyServiceProperties;
import ee.ria.eidas.proxy.specific.error.BadRequestException;
import ee.ria.eidas.proxy.specific.error.RequestDeniedException;
import ee.ria.eidas.proxy.specific.service.SpecificProxyService;
import ee.ria.eidas.proxy.specific.storage.EidasNodeCommunication;
import ee.ria.eidas.proxy.specific.storage.SpecificProxyServiceCommunication;
import ee.ria.eidas.proxy.specific.storage.SpecificProxyServiceCommunication.CorrelatedRequestsHolder;
import eu.eidas.auth.commons.attribute.AttributeDefinition;
import eu.eidas.auth.commons.light.ILightRequest;
import eu.eidas.auth.commons.protocol.impl.SamlNameIdFormat;
import eu.eidas.specificcommunication.exception.SpecificCommunicationException;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.ModelAndView;

import java.util.Collections;
import java.util.List;
import java.util.Locale;

import static ee.ria.eidas.proxy.specific.error.SpecificProxyServiceExceptionHandler.MULTIPLE_INSTANCES_OF_PARAMETER_IS_NOT_ALLOWED;
import static ee.ria.eidas.proxy.specific.web.filter.HttpRequestHelper.getStringParameterValue;
import static java.util.Arrays.asList;
import static java.util.stream.Collectors.toList;

@Slf4j
@Validated
@Controller
public class ProxyServiceRequestController {

    public static final String ENDPOINT_PROXY_SERVICE_REQUEST = "/ProxyServiceRequest";
    private static final List<String> ISO_COUNTRY_CODES = asList(Locale.getISOCountries());

    @Autowired
    private SpecificProxyServiceProperties specificProxyServiceProperties;

    @Autowired
    private SpecificProxyService specificProxyService;

    @Autowired
    private EidasNodeCommunication eidasNodeCommunication;

    @Autowired
    private SpecificProxyServiceCommunication specificProxyServiceCommunication;

    @GetMapping(value = ENDPOINT_PROXY_SERVICE_REQUEST)
    public ModelAndView get(@Validated RequestParameters request) throws SpecificCommunicationException {
        return execute(request);
    }

    @PostMapping(value = ENDPOINT_PROXY_SERVICE_REQUEST)
    public ModelAndView post(@Validated RequestParameters request) throws SpecificCommunicationException {
        return execute(request);
    }

    private ModelAndView execute(RequestParameters request) throws SpecificCommunicationException {
        String tokenBase64 = getStringParameterValue(request.getToken());

        ILightRequest incomingLightRequest = eidasNodeCommunication.getAndRemoveRequest(tokenBase64);
        validateLightRequest(incomingLightRequest);

        CorrelatedRequestsHolder correlatedRequestsHolder = specificProxyService.createOidcAuthenticationRequest(incomingLightRequest);
        specificProxyServiceCommunication.putIdpRequest(correlatedRequestsHolder.getIdpAuthenticationRequestState(), correlatedRequestsHolder);

        return new ModelAndView("redirect:" + correlatedRequestsHolder.getIdpAuthenticationRequest());
    }

    private void validateLightRequest(ILightRequest incomingLightRequest) {
        if (incomingLightRequest == null)
            throw new BadRequestException("Invalid token");

        if (!specificProxyServiceProperties.getSupportedSpTypes().contains(incomingLightRequest.getSpType()))
            throw new RequestDeniedException("Service provider type not supported. Allowed types: "
                    + specificProxyServiceProperties.getSupportedSpTypes(), incomingLightRequest.getId());

        if (!ISO_COUNTRY_CODES.contains(incomingLightRequest.getCitizenCountryCode()))
            throw new BadRequestException("CitizenCountryCode not in 3166-1-alpha-2 format");

        if (incomingLightRequest.getNameIdFormat() != null && SamlNameIdFormat.fromString(incomingLightRequest.getNameIdFormat()) == null)
            throw new BadRequestException("Invalid NameIdFormat");

        if (containsLegalPersonAndNaturalPersonAttributes(incomingLightRequest))
            throw new BadRequestException("Request may not contain both legal person and natural person attributes");
    }

    private boolean containsLegalPersonAndNaturalPersonAttributes(ILightRequest incomingLightRequest) {
        List<String> requestAttributesByFriendlyName = incomingLightRequest.getRequestedAttributes().getAttributeMap().keySet()
                .stream().map(AttributeDefinition::getFriendlyName).collect(toList());
        return !Collections.disjoint(asList("LegalName", "LegalPersonIdentifier"), requestAttributesByFriendlyName) &&
                !Collections.disjoint(asList("GivenName", "FamilyName", "PersonIdentifier", "DateOfBirth"), requestAttributesByFriendlyName);
    }

    @Data
    public static class RequestParameters {

        @NotNull
        @Size(max = 1, message = MULTIPLE_INSTANCES_OF_PARAMETER_IS_NOT_ALLOWED)
        private List<@Pattern(regexp = "^[A-Za-z0-9+/=]{1,1000}$", message = "only base64 characters allowed") String> token;
    }
}
