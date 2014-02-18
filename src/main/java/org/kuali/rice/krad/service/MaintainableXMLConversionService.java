package org.kuali.rice.krad.service;

/**
 * ====
 * CU Customization:
 * Added IU's just-in-time maintainable XML conversion feature,
 * available at https://github.com/ewestfal/rice-xml-converter
 * ====
 */
public interface MaintainableXMLConversionService {
	
	public String transformMaintainableXML(String xml);
}
