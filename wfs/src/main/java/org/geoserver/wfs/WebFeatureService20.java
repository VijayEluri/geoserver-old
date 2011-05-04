/* Copyright (c) 2001 - 2011 TOPP - www.openplans.org. All rights reserved.
 * This code is licensed under the GPL 2.0 license, availible at the root
 * application directory.
 */
package org.geoserver.wfs;

import net.opengis.wfs.FeatureCollectionType;
import net.opengis.wfs20.DescribeFeatureTypeType;
import net.opengis.wfs20.GetCapabilitiesType;
import net.opengis.wfs20.GetFeatureType;
import net.opengis.wfs20.GetFeatureWithLockType;
import net.opengis.wfs20.TransactionResponseType;
import net.opengis.wfs20.TransactionType;

import org.geoserver.catalog.FeatureTypeInfo;
import org.geotools.xml.transform.TransformerBase;

/**
 * Web Feature Service implementation version 2.0.
 * <p>
 * Each of the methods on this class corresponds to an operation as defined
 * by the Web Feature Specification. See {@link http://www.opengeospatial.org/standards/wfs}
 * for more details.
 * </p>
 * @author Justin Deoliveira, OpenGeo
  */
public interface WebFeatureService20 {
    /**
     * The configuration of the service.
     */
    WFSInfo getServiceInfo();
    
    /**
     * WFS GetCapabilities operation.
     *
     * @param request The get capabilities request.
     *
     * @return A transformer instance capable of serializing a wfs capabilities
     * document.
     *
     * @throws WFSException Any service exceptions.
     */
    TransformerBase getCapabilities(GetCapabilitiesType request)
        throws WFSException;
    
    /**
     * WFS DescribeFeatureType operation.
     *
     * @param request The describe feature type request.
     *
     * @return A set of feature type metadata objects.
     *
     * @throws WFSException Any service exceptions.
     */
    FeatureTypeInfo[] describeFeatureType(DescribeFeatureTypeType request)
        throws WFSException;
    
    /**
     * WFS GetFeature operation.
     *
     * @param request The get feature request.
     *
     * @return A feature collection type instance.
     *
     * @throws WFSException Any service exceptions.
     */
    FeatureCollectionType getFeature(GetFeatureType request)
        throws WFSException;
    
    /**
     * WFS GetFeatureWithLock operation.
     *
     * @param request The get feature with lock request.
     *
      * @return A feature collection type instance.
     *
     * @throws WFSException Any service exceptions.
     */
    FeatureCollectionType getFeatureWithLock(GetFeatureWithLockType request)
        throws WFSException;
    
    /**
     * WFS transaction operation.
     *
     * @param request The transaction request.
     *
     * @return A transaction response instance.
     *
     * @throws WFSException Any service exceptions.
     */
    TransactionResponseType transaction(TransactionType request) throws WFSException;
    
    /**
     * Release lock operation.
     * <p>
     * This is not an official operation of the spec.
     * </p>
     * @param lockId A prefiously held lock id.
     */
    void releaseLock(String lockId) throws WFSException;


}
