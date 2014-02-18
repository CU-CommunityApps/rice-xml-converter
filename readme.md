Rice XML Converter
==================

(NOTE: This readme was originally added by Eric Westfall. It has been altered by Cornell in several places to reflect the changes in this version of the enhancement.)

(NOTE: This is a modified version of IU's XML conversion enhancement added by Eric Westfall. Cornell University (CU) has updated it to include additional features plus XML streaming, and has also added some of the features in UCD's XML conversion enhancement from Jonathan Keller that he posted to the Rice collab list on 09/16/2013.)

This project is an example of using the maintainable xml converter from Kuali Rice 2.x to do "just-in-time" conversion on maintenance document XML. It also includes patches to existing maintenance XML conversion code from Rice 2.0 so that the conversion rules file includes more conversions, as well as allowing for a custom rules file to be supplied. An application wanting to use a custom file would simply copy MaintainableXMLUpgradeRules.xml, add any additional rules, and then reference that as appropriate in Rice xml configuration (see information on the default configuration below).

This project just includes a few of the files that can be "patched" to accomplish this. A summary is below:

* MaintenanceDocumentBase - includes a modification to getDataObjectFromXML which attempts to convert the maintenance document XML if it fails to reconstitute a business object from the given maintainable XML. Also includes a modification based on one from UCD that can handle legacy business object notes XML.
* MaintainableXMLConversionService - a newly introduced interface which simply allows us to fetch the conversion service from spring via a service locator
* ExtraKRADServiceLocatorWeb - a new service locator that includes a getter for the MaintainableXMLConversionService
* MaintainableXMLConversionServiceImpl - a modified implementation of the maintainable xml conversion service which allows for a custom rule file to be supplied
* MaintainableXMLUpgradeRules.xml - a modified list of conversion rules, the original list supplied in Rice 2 code has a number of gaps which we encountered. CU has made its own updates to this file to address additional gaps.

(NOTE: This version of the enhancement has removed the KRADServiceLocatorWeb customization and instead uses the new locator class above.)

Also, the original IU code where this modification was made also set up the following default configuration in a Rice xml config file:

```XML
<param name="maintainable.conversion.rule.file" override="false">/org/kuali/rice/krad/config/MaintainableXMLUpgradeRules.xml</param>
```

In order to apply these changes, you would patch your copy of Rice code with the changes.

Note that all of the changes here are based on Rice 2.3.3 source code! (IU's original enhancement was based on Rice 2.2.3 source code.) So if you are using a different version you will probably want to hand-pick the changes and apply them to the version of Rice you are using.

Maintainable Document XML Conversion
====================================

In Rice 2.0 some properties on maintainable objects have been changed/removed which can cause issues opening older maintenance documents since those documents contain invalid XML. This converter handles this situation and transforms the XML which is stored in the maintenance document table before XStream parses it for display on the page to help resolve this problem. If your maintainable classes have changed during an upgrade to Kuali Rice 2.x you may need to add some additional configuration to get your maintenance documents to work properly. This consists of two parts:

1. Create a file with the conversion rules for how to convert the maintainable document XML (see below for an example).
2. Modify the "maintainable.conversion.rule.file" configuration parameter to point to your XML file with the conversion rules.

Note: If your maintainables haven't changed during the upgrade it is likely that you will not need to do any additional configuration for your older maintenance documents to work properly.

Note: You will also need to add a service bean to your KRAD Spring overrides with an id of "kradMaintainableXMLConversionService" and a class of "org.kuali.rice.krad.service.impl.MaintainableXMLConversionServiceImpl".

Maintainable XML conversion rule file
-------------------------------------

This file contains three types of mappings:

1. Maintainable implementation class in 1.x to the implementation class in 2.x. (NOTE: The CU enhancements do NOT use any configuration from this section anymore!)
2. A mapping of a 2.x maintainable implementation class to a map of property names which have changed between the 1.x and 2.x versions. 
3. A list of date properties to append time or timezone information onto, in the event that old date properties in the maintainable XML lack it.

You can also specify an empty replacement value in mapping type 2 above to have the element or property removed entirely.

Since the CU code no longer uses the mappings from mapping type 1 above, they should instead be placed into the "global" map entry. This "global" entry specifies "*" as the class to match on, and contains the matches and replacements that formerly went in the older section.

For example, say you had the following class which you use for a maintainable object with Rice 1.0.3:

```Java
public class FooImpl {
  public String property1;
  public String property2;
}
```

Which has changed to the following in your new version of the code with Rice 2.x:

```Java
public class FooBo {
  public String newProperty1;
  public String property2;
}
```

The maintenance document XML converter would use XML like the following to correct the XML from the database:

```XML
<rules>

  <rule name="maint_doc_classname_changes" alsoRenameClass="true">
    <!--
        Leave this section blank! Its contents are not used in CU's version of the enhancement!
     -->
  </rule>

  <rule name="maint_doc_changed_class_properties">
    <pattern>
      <class>FooBo</class>
      <pattern>
        <match>property1</match>
        <replacement>newProperty1</replacement>
      </pattern>
    </pattern>
    <!--
        The entry below is treated as a "global" one, and will be applied to any matching elements or sub-elements.
        If a match exists in both the global entry and a non-global entry, then the non-global one takes precedence. 
     -->
    <pattern>
      <class>*</class>
      <pattern>
        <match>FooImpl</match>
        <replacement>FooBo</replacement>
      </pattern>
    </pattern>
  </rule>
</rules>
```

The conversion file for the Rice maintainable documents is called MaintainableXMLUpgradeRules.xml. You can refer to this XML file for more realistic examples if needed.

Additional CU Features
----------------------

Aside from the customizations mentioned above, CU's version of the enhancement contains the following extra features:

* Matches will also be attempted against the value of an element's "class" attribute if present. (An element may have both its name and its "class" attribute updated.) If an element does have a "class" attribute and its converted value has a corresponding non-global entry, then that non-global entry will be used instead of any non-global entry for the element's converted name.
* Adding the text "(ATTR)" to the end of a key will cause it to match an attribute name instead of an element name. (However, such keys will NOT match an attribute's value!) This is handy for removing an element's "class" attribute after conversion, even if there are non-global changes for it.
* When matching on an element name or a "class" attribute value, setting the replacement text to "(MOVE_NODES_TO_PARENT)" will result in the removal of the element and its attributes, but the process will preserve its sub-elements and move them to the parent of the removed element. Useful for converting old TypedArrayLists with ListProxyDefaultImpl sub-elements.
* When matching on elements referring to map entries, setting the replacement text to "CONVERT_TO_MAP_ENTRIES" will cause that element and its next "sibling" to be wrapped inside of an "entry" element. This allows for implementing the string-map conversion behavior from IU's and Rice's conversion code. (NOTE: Nested map handling of this type is not supported in the current code.)

The default MaintainableXMLUpgradeRules.xml file in this project contains example usage of these new features.