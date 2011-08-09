/* Copyright (c) 2001 - 2007 TOPP - www.openplans.org. All rights reserved.
 * This code is licensed under the GPL 2.0 license, availible at the root
 * application directory.
 */
package org.geoserver.wfs;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;

import javax.xml.namespace.QName;

import net.opengis.wfs.InsertElementType;
import net.opengis.wfs.InsertedFeatureType;
import net.opengis.wfs.TransactionResponseType;
import net.opengis.wfs.TransactionType;
import net.opengis.wfs.WfsFactory;

import org.eclipse.emf.ecore.EObject;
import org.geoserver.catalog.FeatureTypeInfo;
import org.geoserver.config.GeoServer;
import org.geoserver.feature.ReprojectingFeatureCollection;
import org.geoserver.wfs.request.Insert;
import org.geoserver.wfs.request.TransactionElement;
import org.geoserver.wfs.request.TransactionRequest;
import org.geoserver.wfs.request.TransactionResponse;
import org.geotools.data.DataUtilities;
import org.geotools.data.FeatureStore;
import org.geotools.data.simple.SimpleFeatureCollection;
import org.geotools.data.simple.SimpleFeatureIterator;
import org.geotools.data.simple.SimpleFeatureStore;
import org.geotools.feature.DefaultFeatureCollection;
import org.geotools.geometry.jts.JTS;
import org.geotools.referencing.operation.projection.PointOutsideEnvelopeException;
import org.opengis.feature.simple.SimpleFeature;
import org.opengis.feature.simple.SimpleFeatureType;
import org.opengis.feature.type.GeometryDescriptor;
import org.opengis.filter.FilterFactory;
import org.opengis.filter.identity.FeatureId;
import org.opengis.referencing.crs.CoordinateReferenceSystem;

import com.vividsolutions.jts.geom.Geometry;


/**
 * Handler for the insert element
 *
 * @author Andrea Aime - TOPP
 *
 */
public class InsertElementHandler extends AbstractTransactionElementHandler {
    /**
     * logger
     */
    static Logger LOGGER = org.geotools.util.logging.Logging.getLogger("org.geoserver.wfs");
    private FilterFactory filterFactory;

    public InsertElementHandler(GeoServer gs, FilterFactory filterFactory) {
        super(gs);
        this.filterFactory = filterFactory;
    }

    public void checkValidity(TransactionElement element, Map<QName, FeatureTypeInfo> featureTypeInfos)
        throws WFSTransactionException {
        if (!getInfo().getServiceLevel().getOps().contains( WFSInfo.Operation.TRANSACTION_INSERT)) {
            throw new WFSException(element, "Transaction INSERT support is not enabled");
        }
    }

    @SuppressWarnings("unchecked")
    public void execute(TransactionElement element, TransactionRequest request, Map featureStores, 
        TransactionResponse response, TransactionListener listener) throws WFSTransactionException {
        
        Insert insert = (Insert) element;
        LOGGER.finer("Transasction Insert:" + insert);

        long inserted = response.getTotalInserted().longValue();

        try {
            // group features by their schema
            HashMap /* <SimpleFeatureType,FeatureCollection> */ schema2features = new HashMap();

            
            List features = insert.getFeatures();
            for (Iterator f = features.iterator(); f.hasNext();) {
                SimpleFeature feature = (SimpleFeature) f.next();
                SimpleFeatureType schema = feature.getFeatureType();
                SimpleFeatureCollection collection;
                collection = (SimpleFeatureCollection) schema2features.get(schema);

                if (collection == null) {
                    collection = new DefaultFeatureCollection(null, schema);
                    schema2features.put(schema, collection);
                }

                collection.add(feature);
            }

            // JD: change from set fo list because if inserting
            // features into different feature stores, they could very well
            // get given the same id
            // JD: change from list to map so that the map can later be
            // processed and we can report the fids back in the same order
            // as they were supplied
            HashMap schema2fids = new HashMap();

            for (Iterator c = schema2features.values().iterator(); c.hasNext();) {
                SimpleFeatureCollection collection = (SimpleFeatureCollection) c.next();
                SimpleFeatureType schema = collection.getSchema();

                final QName elementName = new QName(schema.getName().getNamespaceURI(), schema.getTypeName());
                SimpleFeatureStore store;
                store = DataUtilities.simple((FeatureStore) featureStores.get(elementName));

                if (store == null) {
                    throw new WFSException(request, "Could not locate FeatureStore for '" + elementName
                        + "'");
                }

                if (collection != null) {
                    // if we really need to, make sure we are inserting coordinates that do
                    // match the CRS area of validity
                    if(getInfo().isCiteCompliant()) {
                        checkFeatureCoordinatesRange(collection);
                    }
                    
                    // reprojection
                    final GeometryDescriptor defaultGeometry = store.getSchema().getGeometryDescriptor();
                    if(defaultGeometry != null) {
                        CoordinateReferenceSystem target = defaultGeometry.getCoordinateReferenceSystem();
                        if (target != null) {
                            collection = new ReprojectingFeatureCollection(collection, target);
                        }
                    }
                    
                    // Need to use the namespace here for the
                    // lookup, due to our weird
                    // prefixed internal typenames. see
                    // http://jira.codehaus.org/secure/ViewIssue.jspa?key=GEOS-143

                    // Once we get our datastores making features
                    // with the correct namespaces
                    // we can do something like this:
                    // FeatureTypeInfo typeInfo =
                    // catalog.getFeatureTypeInfo(schema.getTypeName(),
                    // schema.getNamespace());
                    // until then (when geos-144 is resolved) we're
                    // stuck with:
                    // QName qName = (QName) typeNames.get( i );
                    // FeatureTypeInfo typeInfo =
                    // catalog.featureType( qName.getPrefix(),
                    // qName.getLocalPart() );

                    // this is possible with the insert hack above.
                    LOGGER.finer("Use featureValidation to check contents of insert");

                    // featureValidation(
                    // typeInfo.getDataStore().getId(), schema,
                    // collection );
                    List fids = (List) schema2fids.get(schema.getTypeName());

                    if (fids == null) {
                        fids = new LinkedList();
                        schema2fids.put(schema.getTypeName(), fids);
                    }

                    //fire pre insert event
                    TransactionEvent event = new TransactionEvent(TransactionEventType.PRE_INSERT,
                            request, elementName, collection);
                    event.setSource( insert );
                    
                    listener.dataStoreChange( event );
                    fids.addAll(store.addFeatures(collection));
                    
                    //fire post insert event
                    //event = new TransactionEvent(TransactionEventType.POST_INSERT, elementName, collection, insert );
                    //listener.dataStoreChange( event );
                }
            }

            // report back fids, we need to keep the same order the
            // fids were reported in the original feature collection
            for (Iterator f = features.iterator(); f.hasNext();) {
                SimpleFeature feature = (SimpleFeature) f.next();
                SimpleFeatureType schema = feature.getFeatureType();

                // get the next fid
                LinkedList fids = (LinkedList) schema2fids.get(schema.getTypeName());
                FeatureId featureId = (FeatureId) fids.removeFirst();
                response.addInsertedFeature(insert.getHandle(), featureId);
            }

            // update the insert counter
            inserted += features.size();
        } catch (Exception e) {
            String msg = "Error performing insert: " + e.getMessage();
            throw new WFSTransactionException(msg, e, insert.getHandle());
        }

        // update transaction summary
        response.setTotalInserted(BigInteger.valueOf(inserted));
    }

    
    /**
     * Checks that all features coordinates are within the expected coordinate range
     * @param collection
     * @throws PointOutsideEnvelopeException
     */
    void checkFeatureCoordinatesRange(SimpleFeatureCollection collection)
            throws PointOutsideEnvelopeException {
        List types = collection.getSchema().getAttributeDescriptors();
        SimpleFeatureIterator fi = collection.features();
        try {
            while(fi.hasNext()) {
                SimpleFeature f = fi.next();
                for (int i = 0; i < types.size(); i++) {
                    if(types.get(i) instanceof GeometryDescriptor) {
                        GeometryDescriptor gat = (GeometryDescriptor) types.get(i);
                        if(gat.getCoordinateReferenceSystem() != null) {
                            Geometry geom = (Geometry) f.getAttribute(i);
                            if(geom != null)
                                JTS.checkCoordinatesRange(geom, gat.getCoordinateReferenceSystem());
                        }
                    }
                }
            }
        } finally {
            fi.close();
        }
    }

    public Class getElementClass() {
        return Insert.class;
    }

    public QName[] getTypeNames(TransactionElement element) throws WFSTransactionException {
        Insert insert = (Insert) element;
        
        List typeNames = new ArrayList();

        List features = insert.getFeatures();
        if (!features.isEmpty()) {
            for (Iterator f = features.iterator(); f.hasNext();) {
                SimpleFeature feature = (SimpleFeature) f.next();

                String name = feature.getFeatureType().getTypeName();
                String namespaceURI = feature.getFeatureType().getName().getNamespaceURI();

                typeNames.add(new QName(namespaceURI, name));
            }
        } else {
            LOGGER.finer("Insert was empty - does not need a FeatuerSoruce");
        }

        return (QName[]) typeNames.toArray(new QName[typeNames.size()]);
    }
}
