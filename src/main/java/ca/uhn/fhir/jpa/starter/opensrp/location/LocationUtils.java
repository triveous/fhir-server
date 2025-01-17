package ca.uhn.fhir.jpa.starter.opensrp.location;

import org.apache.commons.lang3.StringUtils;

public class LocationUtils {
    public static String cleanIdString(String idString) {
        return StringUtils.isNotBlank(idString) && idString.contains("/_") ? idString.substring(0, idString.indexOf("/_")) : idString;
    }
}
