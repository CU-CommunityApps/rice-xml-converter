package org.kuali.rice.krad.service;

import org.kuali.rice.core.api.resourceloader.GlobalResourceLoader;
import org.kuali.rice.krad.service.MaintainableXMLConversionService;

/**
 * ====
 * CU Customization:
 * Added IU's just-in-time maintainable XML conversion feature,
 * available at https://github.com/ewestfal/rice-xml-converter
 * 
 * We have modified the IU code a bit so that it uses this
 * locator class instead. That way, we do not have to update
 * the KRADServiceLocatorWeb class itself.
 * ====
 */
public class ExtraKRADServiceLocatorWeb {

    /*
     * Begin IU Customization
     *
     * Added a new locator for the new MaintainableXMLConversionService
     */
    public static final String MAINTAINABLE_XML_CONVERSION_SERVICE = "kradMaintainableXMLConversionService";
    /*
     * End IU Customization
     */

    // ==== CU Customization: Copied this method from KRADServiceLocatorWeb ====
    public static <T extends Object> T getService(String serviceName) {
        return GlobalResourceLoader.<T>getService(serviceName);
    }

    /*
     * Begin IU Customization
     * 2012-06-06 - James Bennett (jawbenne@indiana.edu)
     * EN-2405
     *
     * Added a new locator for the new MaintainableXMLConversionService
     */
    public static final MaintainableXMLConversionService getMaintainableXMLConversionService() {
        return getService(MAINTAINABLE_XML_CONVERSION_SERVICE);
    }
    /*
     * End IU Customizaton
     */

}
