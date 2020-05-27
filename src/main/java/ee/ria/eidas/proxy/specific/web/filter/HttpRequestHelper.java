package ee.ria.eidas.proxy.specific.web.filter;

import lombok.experimental.UtilityClass;

import java.util.List;

@UtilityClass
public class HttpRequestHelper {

    public static boolean getBooleanParameterValue(List<Boolean> param, boolean defaultValue) {
        return param != null ? param.get(0) : defaultValue;
    }

    public static String getStringParameterValue(List<String> param) {
        return param != null ? param.get(0) : null;
    }
}
