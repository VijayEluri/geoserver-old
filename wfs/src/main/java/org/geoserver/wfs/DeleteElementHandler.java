/* Copyright (c) 2001 - 2007 TOPP - www.openplans.org. All rights reserved.
 * This code is licensed under the GPL 2.0 license, availible at the root
 * application directory.
 */
package org.geoserver.wfs;

import java.io.IOException;
import java.math.BigInteger;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;

import javax.xml.namespace.QName;

import net.opengis.wfs.AllSomeType;
import net.opengis.wfs.DeleteElementType;
import net.opengis.wfs.TransactionResponseType;
import net.opengis.wfs.TransactionType;

import org.eclipse.emf.ecore.EObject;
import org.geoserver.catalog.FeatureTypeInfo;
import org.geoserver.config.GeoServer;
import org.geotools.data.DataStore;
import org.geotools.data.DataUtilities;
import org.geotools.data.Query;
import org.geotools.data.FeatureLockException;
import org.geotools.data.FeatureLocking;
import org.geotools.data.FeatureStore;
import org.geotools.data.FeatureWriter;
import org.geotools.data.simple.SimpleFeatureLocking;
import org.geotools.data.simple.SimpleFeatureStore;
import org.geotools.factory.CommonFactoryFinder;
import org.geotools.xml.EMFUtils;
import org.opengis.feature.simple.SimpleFeature;
import org.opengis.feature.simple.SimpleFeatureType;
import org.opengis.filter.Filter;
import org.opengis.filter.FilterFactory;
import org.opengis.referencing.crs.CoordinateReferenceSystem;

import com.vividsolutions.jts.geom.Envelope;


/**
 * Processes standard Delete elements
 *
 * @author Andrea Aime - TOPP
 *
 */
public class DeleteElementHandler extends AbstractTransactionElementHandler {
    /**
     * logger
     */
    static Logger LOGGER = org.geotools.util.logging.Logging.getLogger("org.geoserver.wfs");
    
    FilterFactory factory = CommonFactoryFinder.getFilterFactory(null);

    Class elementClass;
    RequestObjectHandler handler;
    
    public DeleteElementHandler(GeoServer gs) {
        this(gs, DeleteElementType.class);
    }

    public DeleteElementHandler(GeoServer gs, Class elementClass) {
        super(gs);
        this.elementClass = elementClass;
        this.handler = RequestObjectHandler.get(elementClass);
    }

    public Class getElementClass() {
        return elementClass;
    }

    /**
     * @see org.geoserver.wfs.TransactionElementHandler#getTypeNames(org.eclipse.emf.ecore.EObject)
     */
    public QName[] getTypeNames(EObject element) throws WFSTransactionException {
        return new QName[]{handler.getTypeName(element)};
    }

    public void checkValidity(EObject delete, Map featureTypeInfos)
        throws WFSTransactionException {
        if (!getInfo().getServiceLevel().getOps().contains(WFSInfo.Operation.TRANSACTION_DELETE)) {
            throw new WFSException("Transaction Delete support is not enabled");
        }

        Filter f = handler.getFilter(delete);
        
        if ((f == null) || Filter.INCLUDE.equals(f)) {
            throw new WFSTransactionException("Must specify filter for delete",
                "MissingParameterValue");
        }
    }

    public void execute(EObject delete, Object request, Map featureStores, Object response, 
        TransactionListener listener) throws WFSTransactionException {
        
        QName elementName = handler.getTypeName(delete);
        String handle = handler.getHandle(delete);
        
        long deleted = handler.getTotalDeleted(response).longValue();

        SimpleFeatureStore store = DataUtilities.simple((FeatureStore) featureStores.get(elementName));

        if (store == null) {
            throw new WFSException("Could not locate FeatureStore for '" + elementName + "'");
        }

        String typeName = store.getSchema().getTypeName();
        LOGGER.finer("Transaction Delete:" + delete);

        try {
            Filter filter = handler.getFilter(delete);
            
            // make sure all geometric elements in the filter have a crs, and that the filter
            // is reprojected to store's native crs as well
            CoordinateReferenceSystem declaredCRS = WFSReprojectionUtil.getDeclaredCrs(
                    store.getSchema(), handler.getVersion(request));
            filter = WFSReprojectionUtil.normalizeFilterCRS(filter, store.getSchema(), declaredCRS);
            
            // notify listeners
            TransactionEvent event = new TransactionEvent(TransactionEventType.PRE_DELETE, request,
                    elementName, store.getFeatures(filter));
            event.setSource( delete );
            listener.dataStoreChange( event );

            // compute damaged area
            Envelope damaged = store.getBounds(new Query(elementName.getLocalPart(), filter));

            if (damaged == null) {
                damaged = store.getFeatures(filter).getBounds();
            }

            if ((handler.getLockId(request) != null) && store instanceof FeatureLocking
                    && (handler.isReleaseActionSome(request))) {
                SimpleFeatureLocking locking;
                locking = (SimpleFeatureLocking) store;

                // TODO: Revisit Lock/Delete interaction in gt2
                if (false) {
                    // REVISIT: This is bad - by releasing locks before
                    // we remove features we open ourselves up to the
                    // danger of someone else locking the features we
                    // are about to remove.
                    //
                    // We cannot do it the other way round, as the
                    // Features will not exist
                    //
                    // We cannot grab the fids offline using AUTO_COMMIT
                    // because we may have removed some of them earlier
                    // in the transaction
                    //
                    locking.unLockFeatures(filter);
                    store.removeFeatures(filter);
                } else {
                    // This a bit better and what should be done, we
                    // will need to rework the gt2 locking api to work
                    // with fids or something
                    //
                    // The only other thing that would work
                    // would be to specify that FeatureLocking is
                    // required to remove locks when removing Features.
                    // 
                    // While that sounds like a good idea, it
                    // would be extra work when doing release mode ALL.
                    // 
                    DataStore data = (DataStore) store.getDataStore();
                    FeatureWriter<SimpleFeatureType, SimpleFeature> writer;
                    writer = data.getFeatureWriter(typeName, filter, store.getTransaction());

                    try {
                        while (writer.hasNext()) {
                            String fid = writer.next().getID();
                            Set featureIds = new HashSet();
                            featureIds.add(factory.featureId(fid));
                            locking.unLockFeatures(factory.id(featureIds));
                            writer.remove();
                            deleted++;
                        }
                    } finally {
                        writer.close();
                    }

                    store.removeFeatures(filter);
                }
            } else {
                // We don't have to worry about locking right now
            	int deletedCount = store.getFeatures(filter).size();
            	if(deletedCount > 0)
            		deleted += deletedCount;
                store.removeFeatures(filter);
            }
        } catch (IOException e) {
            String msg = e.getMessage();
            String eHandle = handler.getHandle(delete);
            String code = null;
            
            //check case of feature lock exception and set appropriate exception
            //code
            if ( e instanceof FeatureLockException ) {
                code = "MissingParameterValue";
            }
            throw new WFSTransactionException(msg, e, code, eHandle, handle);
        }

        // update deletion count
        handler.setTotalDeleted(response, BigInteger.valueOf(deleted));
    }
}
