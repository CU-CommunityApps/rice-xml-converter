package org.kuali.rice.krad.service.impl;

import java.io.StringReader;
import java.io.StringWriter;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import javax.xml.stream.XMLStreamWriter;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.sax.SAXSource;
import javax.xml.transform.stream.StreamResult;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpression;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;

import org.apache.commons.beanutils.PropertyUtils;
import org.apache.commons.lang.StringUtils;
import org.kuali.rice.core.api.config.property.ConfigContext;
import org.kuali.rice.core.api.util.RiceUtilities;
import org.kuali.rice.krad.service.MaintainableXMLConversionService;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.core.io.AbstractResource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.FileSystemResource;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

/**
 * ====
 * CU Customization:
 * Added IU's just-in-time maintainable XML conversion feature,
 * available at https://github.com/ewestfal/rice-xml-converter
 * 
 * Also updated this feature to perform streaming conversion,
 * and to properly update nested elements and elements relying
 * on "class" attributes for identification.
 * ====
 */
public class MaintainableXMLConversionServiceImpl implements MaintainableXMLConversionService, InitializingBean {

	// ==== CU Customization: Added logger. ====
	private static final org.apache.log4j.Logger LOG = org.apache.log4j.Logger.getLogger(MaintainableXMLConversionServiceImpl.class);

	private static final String CONVERSION_RULE_FILE_PARAMETER = "maintainable.conversion.rule.file";
	private static final String SERIALIZATION_ATTRIBUTE = "serialization";
	private static final String CLASS_ATTRIBUTE = "class";
	private static final String MAINTENANCE_ACTION_ELEMENT_NAME = "maintenanceAction";

	// ==== CU Customization: Added extra helper constants ====
	private static final String ATTR_INDICATOR = "(ATTR)";
	private static final String MOVE_NODES_TO_PARENT_INDICATOR = "(MOVE_NODES_TO_PARENT)";
	private static final String CONVERT_TO_MAP_ENTRIES_INDICATOR = "(CONVERT_TO_MAP_ENTRIES)";
	private static final String ENTRY_ELEMENT_NAME = "entry";

	private Map<String, String> classNameRuleMap;
	private Map<String, Map<String, String>> classPropertyRuleMap;
	private String conversionRuleFile;

	// ==== CU Customization: Copied this map from KRAD dev tools MaintainableXMLConversionServiceImpl class. ====
	private Map<String, String> dateRuleMap;

	public MaintainableXMLConversionServiceImpl() {
		String conversionRuleFile = ConfigContext.getCurrentContextConfig().getProperty(CONVERSION_RULE_FILE_PARAMETER);
		this.setConversionRuleFile(conversionRuleFile);
	}

	// ==== CU Customization: Initialize the rule maps at bean setup rather than at each conversion attempt. ====
	@Override
	public void afterPropertiesSet() throws Exception {
		if (StringUtils.isNotBlank(this.getConversionRuleFile())) {
			this.setRuleMaps();
		}
	}

	@Override
	public String transformMaintainableXML(String xml) {
		// ==== CU Customization: Fixed a bug with the population of the maintenanceAction variable. ====
		String maintenanceAction = "<" + MAINTENANCE_ACTION_ELEMENT_NAME + ">" + StringUtils.substringAfter(xml, "<" + MAINTENANCE_ACTION_ELEMENT_NAME + ">");
		xml = StringUtils.substringBefore(xml, "<" + MAINTENANCE_ACTION_ELEMENT_NAME + ">");
		if(StringUtils.isNotBlank(this.getConversionRuleFile())) {
			try {
				// ==== CU Customization: Updated this section to use a new conversion process involving StAX. ====
				/*this.setRuleMaps();
				DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
				DocumentBuilder db = dbf.newDocumentBuilder();
				Document document = db.parse(new InputSource(new StringReader(xml)));
				for(Node childNode = document.getFirstChild(); childNode != null;) {
					Node nextChild = childNode.getNextSibling();
					transformClassNode(document, childNode);
					childNode = nextChild;
				}*/
				XMLInputFactory xInFactory = XMLInputFactory.newInstance();
				xInFactory.setProperty(XMLInputFactory.IS_COALESCING, Boolean.TRUE);
				XMLStreamReader xmlIn = xInFactory.createXMLStreamReader(new StringReader(xml));

				XMLOutputFactory xOutFactory = XMLOutputFactory.newInstance();
				StringWriter newXml = new StringWriter();
				XMLStreamWriter xmlOut = xOutFactory.createXMLStreamWriter(newXml);

				doStreamedConversion(xmlIn, xmlOut);

				xmlIn.close();
				xmlOut.close();

				TransformerFactory transFactory = TransformerFactory.newInstance();
				Transformer trans = transFactory.newTransformer();
				trans.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");
				trans.setOutputProperty(OutputKeys.INDENT, "yes");

				StringWriter writer = new StringWriter();
				StreamResult result = new StreamResult(writer);
				//DOMSource source = new DOMSource(document);
				SAXSource source = new SAXSource(new InputSource(new StringReader(newXml.toString())));
				trans.transform(source, result);
				xmlIn.close();
				xml = writer.toString().replaceAll("(?m)^\\s+\\n", "");
			// ==== CU Customization: Write exceptions to the Logger instead of the error stream. ====
			//} catch (ParserConfigurationException e) {
				//e.printStackTrace();
			} catch (XMLStreamException e) {
				LOG.error("Error converting legacy maintainable XML", e);
			//} catch (SAXException e) {
				//e.printStackTrace();
			//} catch (IOException e) {
				//e.printStackTrace();
			//} catch (ClassNotFoundException e) {
				//e.printStackTrace();
			} catch (TransformerConfigurationException e) {
				LOG.error("Error converting legacy maintainable XML", e);
			} catch (TransformerException e) {
				LOG.error("Error converting legacy maintainable XML", e);
			}
			//catch (XPathExpressionException e) {
				//e.printStackTrace();
			//} catch (IllegalAccessException e) {
				//e.printStackTrace();
			//} catch (InvocationTargetException e) {
				//e.printStackTrace();
			//} catch (NoSuchMethodException e) {
				//e.printStackTrace();
			//} catch (InstantiationException e) {
				//e.printStackTrace();
			//}
		}
		// ==== CU Customization: Commented out IU-specific code. ====
		/*if(StringUtils.contains(xml, "edu.iu.uis.dp.bo.DataManager") || StringUtils.contains(xml, "edu.iu.uis.dp.bo.DataSteward")){
			xml = StringUtils.replace(xml, "org.kuali.rice.kim.bo.impl.PersonImpl", "org.kuali.rice.kim.impl.identity.PersonImpl");
			xml = xml.replaceAll("<autoIncrementSet.+", "");
			xml = xml.replaceAll("<address.+","");
		}*/
		return xml + maintenanceAction;
	}

	public String getConversionRuleFile() {
		return conversionRuleFile;
	}

	public void setConversionRuleFile(String conversionRuleFile) {
		this.conversionRuleFile = conversionRuleFile;
	}

	private void transformClassNode(Document document, Node node) throws ClassNotFoundException, XPathExpressionException, IllegalAccessException, InvocationTargetException, NoSuchMethodException, InstantiationException {
		String className = node.getNodeName();
		if(this.classNameRuleMap.containsKey(className)) {
			String newClassName = this.classNameRuleMap.get(className);
			document.renameNode(node, null, newClassName);
			className = newClassName;
		}
		Class<?> dataObjectClass = Class.forName(className);
		if(classPropertyRuleMap.containsKey(className)) {
			transformNode(document, node, dataObjectClass, classPropertyRuleMap.get(className));
		}
		transformNode(document, node, dataObjectClass, classPropertyRuleMap.get("*"));
	}

	private void transformNode(Document document, Node node, Class<?> currentClass, Map<String, String> propertyMappings) throws ClassNotFoundException, XPathExpressionException, IllegalAccessException, InvocationTargetException, NoSuchMethodException, InstantiationException {
		for(Node childNode = node.getFirstChild(); childNode != null;) {
			Node nextChild = childNode.getNextSibling();
			String propertyName = childNode.getNodeName();
			if(childNode.hasAttributes()) {
				XPath xpath = XPathFactory.newInstance().newXPath();
				Node serializationAttribute = childNode.getAttributes().getNamedItem(SERIALIZATION_ATTRIBUTE);
				if(serializationAttribute != null && StringUtils.equals(serializationAttribute.getNodeValue(), "custom")) {
					Node classAttribute = childNode.getAttributes().getNamedItem(CLASS_ATTRIBUTE);
					if(classAttribute != null && StringUtils.equals(classAttribute.getNodeValue(), "org.kuali.rice.kns.util.TypedArrayList")) {
						((Element)childNode).removeAttribute(SERIALIZATION_ATTRIBUTE);
						((Element)childNode).removeAttribute(CLASS_ATTRIBUTE);
						XPathExpression listSizeExpression = xpath.compile("//" + propertyName + "/org.apache.ojb.broker.core.proxy.ListProxyDefaultImpl/default/size/text()");
						String size = (String)listSizeExpression.evaluate(childNode, XPathConstants.STRING);
						List<Node> nodesToAdd = new ArrayList<Node>();
						if(StringUtils.isNotBlank(size) && Integer.valueOf(size) > 0) {
							XPathExpression listTypeExpression = xpath.compile("//" + propertyName + "/org.kuali.rice.kns.util.TypedArrayList/default/listObjectType/text()");
							String listType = (String)listTypeExpression.evaluate(childNode, XPathConstants.STRING);
							XPathExpression listContentsExpression = xpath.compile("//" + propertyName + "/org.apache.ojb.broker.core.proxy.ListProxyDefaultImpl/" + listType);
							NodeList listContents = (NodeList)listContentsExpression.evaluate(childNode, XPathConstants.NODESET);
							for(int i = 0; i < listContents.getLength(); i++) {
								Node tempNode = listContents.item(i);
								transformClassNode(document, tempNode);
								nodesToAdd.add(tempNode);
							}
						}
						for(Node removeNode = childNode.getFirstChild(); removeNode != null;) {
							Node nextRemoveNode = removeNode.getNextSibling();
							childNode.removeChild(removeNode);
							removeNode = nextRemoveNode;
						}
						for(Node nodeToAdd : nodesToAdd) {
							childNode.appendChild(nodeToAdd);
						}
					} else {
						((Element)childNode).removeAttribute(SERIALIZATION_ATTRIBUTE);

						XPathExpression mapContentsExpression = xpath.compile("//" + propertyName + "/map/string");
						NodeList mapContents = (NodeList)mapContentsExpression.evaluate(childNode, XPathConstants.NODESET);
						List<Node> nodesToAdd = new ArrayList<Node>();
						if(mapContents.getLength() > 0 && mapContents.getLength() % 2 == 0) {
							for(int i = 0; i < mapContents.getLength(); i++) {
								Node keyNode = mapContents.item(i);
								Node valueNode = mapContents.item(++i);
								Node entryNode = document.createElement("entry");
								entryNode.appendChild(keyNode);
								entryNode.appendChild(valueNode);
								nodesToAdd.add(entryNode);
							}
						}
						for(Node removeNode = childNode.getFirstChild(); removeNode != null;) {
							Node nextRemoveNode = removeNode.getNextSibling();
							childNode.removeChild(removeNode);
							removeNode = nextRemoveNode;
						}
						for(Node nodeToAdd : nodesToAdd) {
							childNode.appendChild(nodeToAdd);
						}
					}
				}
			}
			if(propertyMappings != null && propertyMappings.containsKey(propertyName)) {
				String newPropertyName = propertyMappings.get(propertyName);
				if(StringUtils.isNotBlank(newPropertyName)) {
					document.renameNode(childNode, null, newPropertyName);
					propertyName = newPropertyName;
				} else {
					// If there is no replacement name then the element needs
					// to be removed and skip all other processing
					node.removeChild(childNode);
					childNode = nextChild;
					continue;
				}
			}
			if(childNode.hasChildNodes() && !(Collection.class.isAssignableFrom(currentClass) || Map.class.isAssignableFrom(currentClass))) {
				if(propertyName.equals("principalId") && (node.getNodeName().equals("dataManagerUser") || node.getNodeName().equals("dataStewardUser"))){
					currentClass = new org.kuali.rice.kim.impl.identity.PersonImpl().getClass();
				}
				Class<?> propertyClass = PropertyUtils.getPropertyType(currentClass.newInstance(), propertyName);
				if(propertyClass != null && classPropertyRuleMap.containsKey(propertyClass.getName())) {
					transformNode(document, childNode, propertyClass, this.classPropertyRuleMap.get(propertyClass.getName()));
				}
				transformNode(document, childNode, propertyClass, classPropertyRuleMap.get("*"));
			}
			childNode = nextChild;
		}
	}

	private void setRuleMaps() {
		setupConfigurationMaps();
		try {
			DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
			DocumentBuilder db = dbf.newDocumentBuilder();

			AbstractResource resource = null;
			Document doc = null;
			if(StringUtils.startsWith(this.getConversionRuleFile(), "classpath")) {
				resource = new ClassPathResource(this.getConversionRuleFile(), Thread.currentThread().getContextClassLoader());
			} else {
				resource = new FileSystemResource(this.getConversionRuleFile());
			}
			if(!resource.exists()) {
				// ==== CU Customization: If a classpath resource, make another attempt to load it via Spring if it couldn't be found above. ====
				if (StringUtils.startsWith(this.getConversionRuleFile(), "classpath")) {
					doc = db.parse(RiceUtilities.getResourceAsStream(this.getConversionRuleFile()));
				} else {
					doc = db.parse(this.getClass().getResourceAsStream(this.getConversionRuleFile()));
				}
			} else {
				doc = db.parse(resource.getInputStream());
			}
			doc.getDocumentElement().normalize();
			XPath xpath = XPathFactory.newInstance().newXPath();

			// Get the moved classes rules
			XPathExpression exprClassNames = xpath.compile("//*[@name='maint_doc_classname_changes']/pattern");
			NodeList classNamesList = (NodeList) exprClassNames.evaluate(doc, XPathConstants.NODESET);
			for (int s = 0; s < classNamesList.getLength(); s++) {
				String matchText = xpath.evaluate("match/text()", classNamesList.item(s));
				String replaceText = xpath.evaluate("replacement/text()", classNamesList.item(s));
				classNameRuleMap.put(matchText, replaceText);
			}

			// Get the property changed rules

			XPathExpression exprClassProperties = xpath.compile(
					"//*[@name='maint_doc_changed_class_properties']/pattern");
			XPathExpression exprClassPropertiesPatterns = xpath.compile("pattern");
			NodeList propertyClassList = (NodeList) exprClassProperties.evaluate(doc, XPathConstants.NODESET);
			for (int s = 0; s < propertyClassList.getLength(); s++) {
				String classText = xpath.evaluate("class/text()", propertyClassList.item(s));
				Map<String, String> propertyRuleMap = new HashMap<String, String>();
				NodeList classPropertiesPatterns = (NodeList) exprClassPropertiesPatterns.evaluate(
						propertyClassList.item(s), XPathConstants.NODESET);
				for (int c = 0; c < classPropertiesPatterns.getLength(); c++) {
					String matchText = xpath.evaluate("match/text()", classPropertiesPatterns.item(c));
					String replaceText = xpath.evaluate("replacement/text()", classPropertiesPatterns.item(c));
					propertyRuleMap.put(matchText, replaceText);
				}
				classPropertyRuleMap.put(classText, propertyRuleMap);
			}

			// ==== CU Customization: Added date map from KRAD dev tools MaintainableXMLConversionServiceImpl class ====
			// Get the Date rules
            XPathExpression dateFieldNames = xpath.compile("//*[@name='maint_doc_date_changes']/pattern");
            NodeList DateNamesList = (NodeList) dateFieldNames.evaluate(doc, XPathConstants.NODESET);
            for (int s = 0; s < DateNamesList.getLength(); s++) {
                String matchText = xpath.evaluate("match/text()", DateNamesList.item(s));
                String replaceText = xpath.evaluate("replacement/text()", DateNamesList.item(s));
                dateRuleMap.put(matchText, replaceText);
            }
		} catch (Exception e) {
			// ==== CU Customization: Added better logging. ====
			LOG.error("Error parsing rule xml file. Please check file.", e);
			//System.out.println("Error parsing rule xml file. Please check file. : " + e.getMessage());
			//e.printStackTrace();
		}
	}

	private void setupConfigurationMaps() {
		classNameRuleMap = new HashMap<String, String>();
		classPropertyRuleMap = new HashMap<String, Map<String,String>>();
		// ==== CU Customization: Added date map from KRAD dev tools MaintainableXMLConversionServiceImpl class ====
		dateRuleMap = new HashMap<String, String>();

		// Pre-populate the class property rules with some defaults which apply to every BO
		Map<String, String> defaultPropertyRules = new HashMap<String, String>();
		defaultPropertyRules.put("boNotes", "");
		defaultPropertyRules.put("autoIncrementSet", "");
		classPropertyRuleMap.put("*", defaultPropertyRules);
	}
	
	
	
	/*
	 * =========================================================
	 * CU Customization:
	 * Created a lower-level streaming implementation of the
	 * XML conversion logic that also takes nested elements
	 * and "class" attributes into account.
	 * =========================================================
	 */
	private void doStreamedConversion(XMLStreamReader xmlIn, XMLStreamWriter xmlOut) throws XMLStreamException {
		// Convenience constants for faster processing.
		final Map<String,String> EMPTY_PROP_RULE_MAP = Collections.emptyMap();
		final String[] ONE_ATTR_NAME = new String[1];
		final String[] ONE_ATTR_VALUE = new String[1];
		final String[] TWO_ATTR_NAMES = new String[2];
		final String[] TWO_ATTR_VALUES = new String[2];
		
		// Variables for reading and writing character data.
		char[] charBuffer = new char[256];
		int charLen = 0;
		int charStart = 0;
		
		// Variables for processing dates.
		int dateLen = -1;
		String dateSuffix = null;
		
		// Variables for handling map entries.
		int mapEntryElemCount = -1;
		int mapEntryElemDepth = -1;
		
		// Variables for processing element attributes.
		int i = 0;
		int attributeLen = 0;
		String[] newAttrNames = null;
		String[] newAttrValues = null;
		String attributeValue = null;
		int classAttributeIndex = -1;
		
		// Variables for holding element names and replacing element names or attribute values.
		String newName = null;
		String replacement = null;
		boolean hasReplacement = false;
		boolean suppressWrite = false;
		
		// Variables for remembering the depths at which certain patterns occur.
		LinkedList<String> classNameStack = new LinkedList<String>();
		String nameStackTop = "";
		LinkedList<Integer> classNameDepthStack = new LinkedList<Integer>();
		int depthStackTop = -1;
		LinkedList<Integer> moveToParentDepthStack = new LinkedList<Integer>();
		int moveToParentDepthStackTop = -1;
		
		// Variables for holding the current properties rule map and the global rule map.
		Map<String,String> currentPropRuleMap = EMPTY_PROP_RULE_MAP;
		Map<String,String> globalPropRuleMap = classPropertyRuleMap.get("*");
		
		// Variables for recording current depth or the depth of the element being skipped.
		int depth = 0;
		int skipDepth = -1;

		if (globalPropRuleMap == null) {
			globalPropRuleMap = EMPTY_PROP_RULE_MAP;
		}

		// Parse the XML.
		while (xmlIn.hasNext()) {
			
			// Verify whether the current element is being skipped.
			if (skipDepth == -1) {
				switch (xmlIn.next()) {
					
					case XMLStreamConstants.START_ELEMENT :
						// Reset relevant variables.
						suppressWrite = false;
						classAttributeIndex = -1;
						// Increment depth.
						depth++;
						
						// Get element attributes.
						attributeLen = xmlIn.getAttributeCount();
						if (attributeLen > 0) {
							// Use pre-defined convenience arrays when possible.
							if (attributeLen == 1) {
								newAttrNames = ONE_ATTR_NAME;
								newAttrValues = ONE_ATTR_VALUE;
							} else if (attributeLen == 2) {
								newAttrNames = TWO_ATTR_NAMES;
								newAttrValues = TWO_ATTR_VALUES;
							} else {
								newAttrNames = new String[attributeLen];
								newAttrValues = new String[attributeLen];
							}
							// Record the attributes.
							for (i = 0; i < attributeLen; i++) {
								newName = xmlIn.getAttributeLocalName(i);
								attributeValue = xmlIn.getAttributeValue(i);
								// Keep track of where the "class" attribute is, if found.
								if (CLASS_ATTRIBUTE.equals(newName)) {
									classAttributeIndex = i;
								}
								newAttrNames[i] = newName;
								newAttrValues[i] = attributeValue;
							}
						}
						
						// Update the "class" attribute value if necessary.
						if (classAttributeIndex != -1) {
							attributeValue = newAttrValues[classAttributeIndex];
							// Determine if a replacement value exists.
							if (depth == depthStackTop + 1 && currentPropRuleMap.containsKey(attributeValue)) {
								replacement = currentPropRuleMap.get(attributeValue);
								hasReplacement = true;
							} else if (globalPropRuleMap.containsKey(attributeValue)) {
								replacement = globalPropRuleMap.get(attributeValue);
								hasReplacement = true;
							} else {
								hasReplacement = false;
							}
							// Perform any needed updates or skips.
							if (hasReplacement) {
								if (StringUtils.isBlank(replacement)) {
									// If blank, skip the element and its children.
									skipDepth = depth;
									suppressWrite = true;
								} else if (MOVE_NODES_TO_PARENT_INDICATOR.equals(replacement)) {
									// If indicated, do not write the current element but still add its children to the parent element.
									moveToParentDepthStack.push(Integer.valueOf(moveToParentDepthStackTop));
									moveToParentDepthStackTop = depth;
									suppressWrite = true;
								} else if (CONVERT_TO_MAP_ENTRIES_INDICATOR.equals(replacement)) {
									// If indicated, prepare to wrap map key/value pairs in "entry" elements.
									if (mapEntryElemCount == -1) {
										mapEntryElemCount = 0;
										mapEntryElemDepth = depth;
									}
								} else {
									// Otherwise, just update the "class" attribute's value.
									attributeValue = replacement;
									newAttrValues[classAttributeIndex] = attributeValue;
								}
							}
						}
						
						// Get element name.
						newName = xmlIn.getLocalName();
						
						// Update the element name if necessary.
						if (!suppressWrite) {
							// Determine whether a replacement exists in the prop rule maps.
							if (depth == depthStackTop + 1 && currentPropRuleMap.containsKey(newName)) {
								replacement = currentPropRuleMap.get(newName);
								hasReplacement = true;
							} else if (globalPropRuleMap.containsKey(newName)) {
								replacement = globalPropRuleMap.get(newName);
								hasReplacement = true;
							} else {
								hasReplacement = false;
							}
							// Rename or skip element if specified by direct parent element or global map.
							if (hasReplacement) {
								if (StringUtils.isBlank(replacement)) {
									// If blank, skip the element and its children.
									skipDepth = depth;
									suppressWrite = true;
								} else if (MOVE_NODES_TO_PARENT_INDICATOR.equals(replacement)) {
									// If indicated, do not write the current element but still add its children to the parent element.
									if (moveToParentDepthStackTop != depth) {
										moveToParentDepthStack.push(Integer.valueOf(moveToParentDepthStackTop));
										moveToParentDepthStackTop = depth;
									}
									suppressWrite = true;
								} else if (CONVERT_TO_MAP_ENTRIES_INDICATOR.equals(replacement)) {
									// If indicated, prepare to wrap map key/value pairs in "entry" elements.
									if (mapEntryElemCount == -1) {
										mapEntryElemCount = 0;
										mapEntryElemDepth = depth;
									}
								} else {
									// Otherwise, rename the element.
									newName = replacement;
								}
							}
						}
						
						// Update property rule map tracking if the whole element is not being skipped.
						if (skipDepth != depth) {
							replacement = null;
							// Give precedence to "class" attribute values for prop rule map updates.
							if (classAttributeIndex != -1 && classPropertyRuleMap.containsKey(attributeValue)) {
								replacement = attributeValue;
							} else if (classPropertyRuleMap.containsKey(newName)) {
								replacement = newName;
							}
							// Update tracking and stacks if necessary.
							if (replacement != null) {
								classNameStack.push(nameStackTop);
								classNameDepthStack.push(Integer.valueOf(depthStackTop));
								nameStackTop = replacement;
								depthStackTop = depth;
								currentPropRuleMap = classPropertyRuleMap.get(replacement);
								if (currentPropRuleMap == null) {
									currentPropRuleMap = EMPTY_PROP_RULE_MAP;
								}
							}
						}
						
						// Write the element and its attributes if it's not being suppressed.
						if (!suppressWrite) {
							// Write opening "entry" element if necessary.
							if (mapEntryElemDepth == depth) {
								if (mapEntryElemCount == 0) {
									xmlOut.writeStartElement(ENTRY_ELEMENT_NAME);
								}
								mapEntryElemCount++;
							}
							
							// Write the element.
							xmlOut.writeStartElement(newName);
							
							// Track length of date text, if a date field.
							if (StringUtils.isNotBlank(dateRuleMap.get(newName))) {
								dateSuffix = " " + dateRuleMap.get(newName);
								dateLen = 0;
							}
							
							// Write or suppress attributes as needed.
							if (attributeLen > 0) {
								for (i = 0; i < attributeLen; i++) {
									suppressWrite = false;
									// Check for replacement name.
									if (depth == depthStackTop && currentPropRuleMap.containsKey(newAttrNames[i] + ATTR_INDICATOR)) {
										replacement = currentPropRuleMap.get(newAttrNames[i] + ATTR_INDICATOR);
										hasReplacement = true;
									} else if (globalPropRuleMap.containsKey(newAttrNames[i] + ATTR_INDICATOR)) {
										replacement = globalPropRuleMap.get(newAttrNames[i] + ATTR_INDICATOR);
										hasReplacement = true;
									} else {
										hasReplacement = false;
									}
									// Replace name or suppress the whole attribute as needed.
									if (hasReplacement) {
										if (StringUtils.isBlank(replacement)) {
											suppressWrite = true;
										} else {
											newAttrNames[i] = replacement;
										}
									}
									// Write the attribute if it's not being suppressed.
									if (!suppressWrite) {
										xmlOut.writeAttribute(newAttrNames[i], newAttrValues[i]);
									}
								}
							}
						}
						break;
					
					case XMLStreamConstants.END_ELEMENT :
						// Update stacks if necessary.
						if (depth == depthStackTop) {
							nameStackTop = classNameStack.pop();
							depthStackTop = classNameDepthStack.pop().intValue();
							currentPropRuleMap = classPropertyRuleMap.get(nameStackTop);
							if (currentPropRuleMap == null) {
								currentPropRuleMap = EMPTY_PROP_RULE_MAP;
							}
						}
						// Write the end element or skip it as needed.
						if (depth == moveToParentDepthStackTop) {
							moveToParentDepthStackTop = moveToParentDepthStack.pop().intValue();
						} else {
							// If a date field, write a default time suffix if one is not present.
							if (dateLen != -1) {
								if (dateLen == 10) {
									xmlOut.writeCharacters(dateSuffix);
								}
								dateLen = -1;
								dateSuffix = null;
							}
							// Write the end element.
							xmlOut.writeEndElement();
							// If a map entry value, write the closing "entry" tag.
							if (mapEntryElemDepth == depth && mapEntryElemCount == 2) {
								xmlOut.writeEndElement();
								mapEntryElemCount = -1;
								mapEntryElemDepth = -1;
							}
						}
						// Decrement depth.
						depth--;
						break;
					
					case XMLStreamConstants.PROCESSING_INSTRUCTION :
						// Write out processing instructions as-is.
						if (StringUtils.isNotBlank(xmlIn.getPIData())) {
							xmlOut.writeProcessingInstruction(xmlIn.getPITarget(), xmlIn.getPIData());
						} else {
							xmlOut.writeProcessingInstruction(xmlIn.getPITarget());
						}
						break;
					
					case XMLStreamConstants.CHARACTERS :
						// Write out the character data as-is, and record date length if inside a date element.
						charStart = 0;
						do {
							charLen = xmlIn.getTextCharacters(charStart, charBuffer, 0, 256);
							if (charLen != 0) {
								xmlOut.writeCharacters(charBuffer, 0, charLen);
							}
							charStart += charLen;
							if (dateLen != -1) {
								dateLen += charLen;
							}
						} while (charLen == 256);
						break;
					
					case XMLStreamConstants.COMMENT :
						// TODO: Is it safe to ignore this case if the reader is a coalesced one?
						break;
					
					case XMLStreamConstants.SPACE :
						// TODO: Is it safe to ignore this case if the reader is a coalesced one?
						break;
					
					case XMLStreamConstants.START_DOCUMENT :
						xmlOut.writeStartDocument();
						break;
					
					case XMLStreamConstants.END_DOCUMENT :
						xmlOut.writeEndDocument();
						break;
					
					case XMLStreamConstants.ENTITY_REFERENCE :
						// Write out entity references as-is.
						xmlOut.writeEntityRef(xmlIn.getLocalName());
						break;
					
					case XMLStreamConstants.ATTRIBUTE :
						// Ignore, since we're handling these in the START_ELEMENT case.
						break;
					
					case XMLStreamConstants.DTD :
						// Ignore, since we do not bother with DTDs in maint doc XML.
						break;
					
					case XMLStreamConstants.CDATA :
						// TODO: Is it safe to ignore this case if the reader is a coalesced one?
						break;
					
					case XMLStreamConstants.NAMESPACE :
						// Write out namespaces as-is.
						xmlOut.writeNamespace(xmlIn.getPrefix(), xmlIn.getNamespaceURI());
						break;
					
					case XMLStreamConstants.NOTATION_DECLARATION :
						// Ignore, since we do not bother with DTDs in maint doc XML.
						break;
					
					case XMLStreamConstants.ENTITY_DECLARATION :
						// Ignore, since we do not bother with DTDs in maint doc XML.
						break;
					
					default :
						break;
				}
			} else {
				// If in element-skipping mode, ignore XML content until the current element has been fully skipped.
				while (depth >= skipDepth) {
					switch (xmlIn.next()) {
						case XMLStreamConstants.START_ELEMENT :
							depth++;
							break;
						case XMLStreamConstants.END_ELEMENT :
							depth--;
							break;
						default :
							break;
					}
				}
				skipDepth = -1;
			}
			
		}
	}

}
