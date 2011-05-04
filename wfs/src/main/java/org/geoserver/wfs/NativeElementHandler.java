/* Copyright (c) 2001 - 2007 TOPP - www.openplans.org. All rights reserved.
 * This code is licensed under the GPL 2.0 license, availible at the root
 * application directory.
 */
package org.geoserver.wfs;

import java.util.Map;
import java.util.logging.Logger;

import javax.xml.namespace.QName;

import net.opengis.wfs.NativeType;
import net.opengis.wfs.TransactionResponseType;
import net.opengis.wfs.TransactionType;

import org.eclipse.emf.ecore.EObject;
import org.geoserver.catalog.FeatureTypeInfo;
import org.geotools.data.FeatureStore;


/**
 * Processes native elements as unrecognized ones, and checks wheter they can be
 * safely ignored on not.
 *
 * @author Andrea Aime - TOPP
 */
public class NativeElementHandler implements TransactionElementHandler {
    /**
     * logger
     */
    static Logger LOGGER = org.geotools.util.logging.Logging.getLogger("org.geoserver.wfs");

    /**
     * Empty array of QNames
     */
    protected static final QName[] EMPTY_QNAMES = new QName[0];
    
    Class elementClass;
    RequestObjectHandler handler;
    
    public NativeElementHandler() {
        this(NativeType.class);
    }
    
    public NativeElementHandler(Class elementClass) {
        this.elementClass = elementClass;
        this.handler = RequestObjectHandler.get(elementClass);
    }

    public void checkValidity(EObject nativ, Map featureTypeInfos)
        throws WFSTransactionException {
        
        if (!handler.isSafeToIgnore(nativ)) {
            throw new WFSTransactionException("Native element:" + handler.getVendorId(nativ)
                + " unsupported but marked as" + " unsafe to ignore", "InvalidParameterValue");
        }
    }

    public void execute(EObject element, Object request, Map featureSources,
        Object response, TransactionListener listener) throws WFSTransactionException {
        // nothing to do, we just ignore if possible
    }

    public Class getElementClass() {
        return elementClass;
    }

    /**
     * @return an empty array.
     * @see org.geoserver.wfs.TransactionElementHandler#getTypeNames(org.eclipse.emf.ecore.EObject)
     */
    public QName[] getTypeNames(EObject element) throws WFSTransactionException {
        // we don't handle this
        return EMPTY_QNAMES;
    }
}
