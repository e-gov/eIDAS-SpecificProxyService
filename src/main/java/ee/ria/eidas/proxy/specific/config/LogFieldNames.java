package ee.ria.eidas.proxy.specific.config;

import lombok.experimental.UtilityClass;

@UtilityClass
public class LogFieldNames {

    public static final String LIGHT_REQUEST = "light_request";
    public static final String LIGHT_REQUEST_LIGHT_TOKEN_ID = "light_request.light_token_id";

    public static final String LIGHT_RESPONSE = "light_response";
    public static final String LIGHT_RESPONSE_LIGHT_TOKEN_ID = "light_response.light_token_id";

    public static final String IGNITE_CACHE_NAME = "communication_cache.name";

    public static final String IDP_REQUEST_LIGHT_TOKEN_ID = "idp_request.light_token_id";
    public static final String IDP_REQUEST_CORRELATED_REQUESTS = "idp_request.correlated_requests";

    public static final String IDP_TOKEN_REQUEST_CODE = "idp.token_request.code";
    public static final String IDP_TOKEN_REQUEST_IN_RESPONSE_TO = "idp.token_request.in_response_to";
    public static final String IDP_TOKEN_REQUEST_HTTP_URL = "idp.token_request.http.url";
    public static final String IDP_TOKEN_REQUEST_HTTP_QUERY_PARAMS = "idp.token_request.http.query_params";
    public static final String IDP_TOKEN_REQUEST_HTTP_METHOD = "idp.token_request.http.method";
    public static final String IDP_TOKEN_REQUEST_HTTP_CONNECT_TIMEOUT = "idp.token_request.http.connect_timeout";
    public static final String IDP_TOKEN_REQUEST_HTTP_READ_TIMEOUT = "idp.token_request.http.read_timeout";
    public static final String IDP_TOKEN_REQUEST_AUTH_CLIENT_ID = "idp.token_request.auth.client_id";
    public static final String IDP_TOKEN_REQUEST_AUTH_METHOD = "idp.token_request.auth.method";

}
