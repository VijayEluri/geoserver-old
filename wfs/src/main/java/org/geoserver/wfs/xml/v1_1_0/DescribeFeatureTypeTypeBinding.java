/* Copyright (c) 2001 - 2007 TOPP - www.openplans.org. All rights reserved.
 * This code is licensed under the GPL 2.0 license, availible at the root
 * application directory.
 */
package org.geoserver.wfs.xml.v1_1_0;

import javax.xml.namespace.QName;

import net.opengis.wfs.DescribeFeatureTypeType;
import net.opengis.wfs.WfsFactory;

import org.geotools.xml.AbstractComplexBinding;
import org.geotools.xml.ElementInstance;
import org.geotools.xml.Node;


/**
 * Binding object for the type http://www.opengis.net/wfs:DescribeFeatureTypeType.
 *
 * <p>
 *        <pre>
 *         <code>
 *  &lt;xsd:complexType name="DescribeFeatureTypeType"&gt;
 *      &lt;xsd:annotation&gt;
 *          &lt;xsd:documentation&gt;
 *              The DescribeFeatureType operation allows a client application
 *              to request that a Web Feature Service describe one or more
 *              feature types.   A Web Feature Service must be able to generate
 *              feature descriptions as valid GML3 application schemas.
 *
 *              The schemas generated by the DescribeFeatureType operation can
 *              be used by a client application to validate the output.
 *
 *              Feature instances within the WFS interface must be specified
 *              using GML3.  The schema of feature instances specified within
 *              the WFS interface must validate against the feature schemas
 *              generated by the DescribeFeatureType request.
 *           &lt;/xsd:documentation&gt;
 *      &lt;/xsd:annotation&gt;
 *      &lt;xsd:complexContent&gt;
 *          &lt;xsd:extension base="wfs:BaseRequestType"&gt;
 *              &lt;xsd:sequence&gt;
 *                  &lt;xsd:element maxOccurs="unbounded" minOccurs="0"
 *                      name="TypeName" type="xsd:QName"&gt;
 *                      &lt;xsd:annotation&gt;
 *                          &lt;xsd:documentation&gt;
 *                          The TypeName element is used to enumerate the
 *                          feature types to be described.  If no TypeName
 *                          elements are specified then all features should
 *                          be described.  The name must be a valid type
 *                          that belongs to the feature content as defined
 *                          by the GML Application Schema.
 *                       &lt;/xsd:documentation&gt;
 *                      &lt;/xsd:annotation&gt;
 *                  &lt;/xsd:element&gt;
 *              &lt;/xsd:sequence&gt;
 *              &lt;xsd:attribute default="text/xml; subtype=gml/3.1.1"
 *                  name="outputFormat" type="xsd:string" use="optional"&gt;
 *                  &lt;xsd:annotation&gt;
 *                      &lt;xsd:documentation&gt;
 *                       The outputFormat attribute is used to specify what schema
 *                       description language should be used to describe features.
 *                       The default value of 'text/xml; subtype=3.1.1' means that
 *                       the WFS must generate a GML3 application schema that can
 *                       be used to validate the GML3 output of a GetFeature
 *                       request or feature instances specified in Transaction
 *                       operations.
 *                       For the purposes of experimentation, vendor extension,
 *                       or even extensions that serve a specific community of
 *                       interest, other acceptable output format values may be
 *                       advertised by a WFS service in the capabilities document.
 *                       The meaning of such values in not defined in the WFS
 *                       specification.  The only proviso is such cases is that
 *                       clients may safely ignore outputFormat values that do
 *                       not recognize.
 *                    &lt;/xsd:documentation&gt;
 *                  &lt;/xsd:annotation&gt;
 *              &lt;/xsd:attribute&gt;
 *          &lt;/xsd:extension&gt;
 *      &lt;/xsd:complexContent&gt;
 *  &lt;/xsd:complexType&gt;
 *
 *          </code>
 *         </pre>
 * </p>
 *
 * @generated
 */
public class DescribeFeatureTypeTypeBinding extends AbstractComplexBinding {
    WfsFactory wfsfactory;

    public DescribeFeatureTypeTypeBinding(WfsFactory wfsfactory) {
        this.wfsfactory = wfsfactory;
    }

    /**
     * @generated
     */
    public QName getTarget() {
        return WFS.DESCRIBEFEATURETYPETYPE;
    }

    public int getExecutionMode() {
        return BEFORE;
    }

    /**
     * <!-- begin-user-doc -->
     * <!-- end-user-doc -->
     *
     * @generated modifiable
     */
    public Class getType() {
        return DescribeFeatureTypeType.class;
    }

    /**
     * <!-- begin-user-doc -->
     * <!-- end-user-doc -->
     *
     * @generated modifiable
     */
    public Object parse(ElementInstance instance, Node node, Object value)
        throws Exception {
        DescribeFeatureTypeType describeFeatureType = wfsfactory
            .createDescribeFeatureTypeType();

        //&lt;xsd:element maxOccurs="unbounded" minOccurs="0"
        //   name="TypeName" type="xsd:QName"&gt;
        describeFeatureType.getTypeName().addAll(node.getChildValues(QName.class));

        //lt;xsd:attribute default="text/xml; subtype=gml/3.1.1"
        //   name="outputFormat" type="xsd:string" use="optional"&gt;
        if (node.hasAttribute("outputFormat")) {
            describeFeatureType.setOutputFormat((String) node.getAttributeValue("outputFormat"));
        }

        return describeFeatureType;
    }
}
