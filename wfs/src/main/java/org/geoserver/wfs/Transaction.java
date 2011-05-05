/* Copyright (c) 2001 - 2007 TOPP - www.openplans.org. All rights reserved.
 * This code is licensed under the GPL 2.0 license, availible at the root
 * application directory.
 */
package org.geoserver.wfs;

import java.io.IOException;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.xml.namespace.QName;

import net.opengis.wfs.ActionType;
import net.opengis.wfs.AllSomeType;
import net.opengis.wfs.InsertedFeatureType;
import net.opengis.wfs.TransactionResponseType;
import net.opengis.wfs.TransactionType;
import net.opengis.wfs.WfsFactory;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.util.FeatureMap;
import org.geoserver.catalog.Catalog;
import org.geoserver.catalog.FeatureTypeInfo;
import org.geoserver.platform.GeoServerExtensions;
import org.geoserver.platform.ServiceException;
import org.geoserver.wfs.request.TransactionElement;
import org.geoserver.wfs.request.TransactionRequest;
import org.geoserver.wfs.request.TransactionResponse;
import org.geotools.data.DefaultTransaction;
import org.geotools.data.FeatureSource;
import org.geotools.data.FeatureStore;
import org.geotools.xml.EMFUtils;
import org.opengis.feature.Feature;
import org.opengis.feature.type.FeatureType;
import org.opengis.filter.FilterFactory;
import org.springframework.context.ApplicationContext;

/**
 * Web Feature Service Transaction operation.
 *
 * @author Justin Deoliveira, The Open Planning Project
 *
 */
public class Transaction {
    /**
     * logger
     */
    static Logger LOGGER = org.geotools.util.logging.Logging.getLogger("org.geoserver.wfs");

    /**
     * WFS configuration
     */
    protected WFSInfo wfs;

    /**
     * The catalog
     */
    protected Catalog catalog;

    /**
     * Filter factory
     */
    protected FilterFactory filterFactory;

    /** Geotools2 transaction used for this opperations */
    protected org.geotools.data.Transaction transaction;
    protected List transactionElementHandlers = new ArrayList();
    protected List transactionListeners = new ArrayList();
    protected List transactionPlugins = new ArrayList();
    
    public Transaction(WFSInfo wfs, Catalog catalog, ApplicationContext context) {
        this.wfs = wfs;
        this.catalog = catalog;
        
        // register element handlers, listeners and plugins
        transactionElementHandlers.addAll(GeoServerExtensions.extensions(TransactionElementHandler.class));
        transactionListeners.addAll(GeoServerExtensions.extensions(TransactionListener.class));
        transactionPlugins.addAll(GeoServerExtensions.extensions(TransactionPlugin.class));
        // plugins are listeners too, but I want to make sure they are notified
        // of
        // changes in the same order as the other plugin callbacks
        transactionListeners.removeAll(transactionPlugins);
        // sort plugins according to priority
        Collections.sort(transactionPlugins, new TransactionPluginComparator());
    }

    public void setFilterFactory(FilterFactory filterFactory) {
        this.filterFactory = filterFactory;
    }

    public TransactionResponse transaction(TransactionRequest request)
        throws WFSException {
        // make sure server is supporting transactions
        if (!wfs.getServiceLevel().contains(WFSInfo.ServiceLevel.TRANSACTIONAL) ) {
            throw new WFSException("Transaction support is not enabled");
        }

        try {
            return execute(request);
        } catch (WFSException e) {
            abort(request); // release any locks
            throw e;
        } catch (Throwable t) {
            abort(request); // release any locks
            throw new WFSException(t);
        }
    }

    /**
     * Execute Transaction request.
     *
     * <p>
     * The results of this opperation are stored for use by writeTo:
     *
     * <ul>
     * <li> transaction: used by abort & writeTo to commit/rollback </li>
     * <li> request: used for users getHandle information to report errors </li>
     * <li> stores: FeatureStores required for Transaction </li>
     * <li> failures: List of failures produced </li>
     * </ul>
     * </p>
     *
     * <p>
     * Because we are using geotools2 locking facilities our modification will
     * simply fail with IOException if we have not provided proper
     * authorization.
     * </p>
     *
     * <p>
     * The specification allows a WFS to implement PARTIAL sucess if it is
     * unable to rollback all the requested changes. This implementation is able
     * to offer full Rollback support and will not require the use of PARTIAL
     * success.
     * </p>
     *
     * @param transactionRequest
     *
     * @throws ServiceException
     *             DOCUMENT ME!
     * @throws WfsException
     * @throws WfsTransactionException
     *             DOCUMENT ME!
     */
    protected TransactionResponse execute(TransactionRequest request)
        throws Exception {
        // some defaults
        if (request.getReleaseAction() == null) {
            request.setReleaseActionAll();
        }

        // inform plugins we're about to start, and let them eventually
        // alter the request
        for (Iterator it = transactionPlugins.iterator(); it.hasNext();) {
            TransactionPlugin tp = (TransactionPlugin) it.next();
            fireBeforeTransaction(request, tp);
        }

        // setup the transaction listener multiplexer
        TransactionListenerMux multiplexer = new TransactionListenerMux();

        // the geotools transaction
        transaction = getDatastoreTransaction(request);

        //
        // We are going to preprocess our elements,
        // gathering all the FeatureSources we need
        //
        // Map of required FeatureStores by typeName
        Map stores = new HashMap();

        // Map of required FeatureStores by typeRef (dataStoreId:typeName)
        // (This will be added to the contents are harmed)
        Map stores2 = new HashMap();

        // List of type names, maintain this list because of the insert hack
        // described below
        // List typeNames = new ArrayList();
        Map elementHandlers = gatherElementHandlers(request);

        // Gather feature types required by transaction elements and validate
        // the elements
        // finally gather FeatureStores required by Transaction Elements
        // and configure them with our transaction
        //
        // (I am using element rather than transaction sub request
        // to agree with the spec docs)
        for (Iterator it = elementHandlers.entrySet().iterator(); it.hasNext();) {
            Map.Entry entry = (Map.Entry) it.next();
            TransactionElement element = (TransactionElement) entry.getKey();
            TransactionElementHandler handler = (TransactionElementHandler) entry.getValue();
            Map featureTypeInfos = new HashMap();

            
            QName[] typeNames = handler.getTypeNames(element);

            for (int i = 0; i < typeNames.length; i++) {
                final QName typeName = typeNames[i];
                final String name = typeName.getLocalPart();
                final String namespaceURI;

                if (typeName.getNamespaceURI() != null) {
                    namespaceURI = typeName.getNamespaceURI();
                } else {
                    namespaceURI = catalog.getDefaultNamespace().getURI();
                }

                LOGGER.fine("Locating FeatureSource uri:'" + namespaceURI + "' name:'" + name + "'");

                final FeatureTypeInfo meta = catalog.getFeatureTypeByName(namespaceURI, name);

                if (meta == null) {
                    String msg = "Feature type '" + name + "' is not available: ";
                    throw new WFSTransactionException(msg, (String) null, element.getHandle());
                }

                featureTypeInfos.put(typeName, meta);
            }

            // check element validity
            handler.checkValidity(element, featureTypeInfos);

            // go through all feature type infos data objects, and load feature
            // stores
            for (Iterator m = featureTypeInfos.values().iterator(); m.hasNext();) {
                FeatureTypeInfo meta = (FeatureTypeInfo) m.next();
                String typeRef = meta.getStore().getName() + ":" + meta.getName();

                String URI = meta.getNamespace().getURI();
                QName elementName = new QName(URI, meta.getName(),
                        meta.getNamespace().getPrefix());
                QName elementNameDefault = null;

                if (catalog.getDefaultNamespace().getURI().equals(URI)) {
                    elementNameDefault = new QName(meta.getName());
                }

                LOGGER.fine("located FeatureType w/ typeRef '" + typeRef + "' and elementName '"
                    + elementName + "'");

                if (stores.containsKey(elementName)) {
                    // typeName already loaded
                    continue;
                }

                try {
                    FeatureSource<? extends FeatureType, ? extends Feature> source = meta.getFeatureSource(null,null);

                    if (source instanceof FeatureStore) {
                        FeatureStore<? extends FeatureType, ? extends Feature> store;
                        store = (FeatureStore<? extends FeatureType, ? extends Feature>) source;
                        store.setTransaction(transaction);
                        stores.put(elementName, source);

                        if (elementNameDefault != null) {
                            stores.put(elementNameDefault, source);
                        }

                        stores2.put(typeRef, source);
                    } else {
                        String msg = elementName + " is read-only";
                        throw new WFSTransactionException(msg, (String) null, element.getHandle());
                    }
                } catch (IOException ioException) {
                    String msg = elementName + " is not available: "
                        + ioException.getLocalizedMessage();
                    throw new WFSTransactionException(msg, ioException, element.getHandle());
                }
            }
        }

        // provide authorization for transaction
        // 
        String authorizationID = request.getLockId();

        if (authorizationID != null) {
            if (!wfs.getServiceLevel().getOps().contains( WFSInfo.Operation.LOCKFEATURE)) {
                throw new WFSException("Lock support is not enabled");
            }

            LOGGER.finer("got lockId: " + authorizationID);

            if (!lockExists(authorizationID)) {
                String mesg = "Attempting to use a lockID that does not exist"
                    + ", it has either expired or was entered wrong.";
                throw new WFSException(mesg, "InvalidParameterValue");
            }

            try {
                transaction.addAuthorization(authorizationID);
            } catch (IOException ioException) {
                // This is a real failure - not associated with a element
                //
                throw new WFSException("Authorization ID '" + authorizationID + "' not useable",
                    ioException);
            }
        }

        // result
        TransactionResponse result = request.createResponse();
        result.setHandle(request.getHandle());
        
        // execute elements in order, recording results as we go
        // I will need to record the damaged area for pre commit validation
        // checks
        // Envelope envelope = new Envelope();
        boolean exception = false;

        try {
            for (Iterator it = elementHandlers.entrySet().iterator(); it.hasNext();) {
                Map.Entry entry = (Map.Entry) it.next();
                TransactionElement element = (TransactionElement) entry.getKey();
                TransactionElementHandler handler = (TransactionElementHandler) entry.getValue();

                handler.execute(element, request, stores, result, multiplexer);
            }
        } catch (WFSTransactionException e) {
            exception = true;
            LOGGER.log(Level.SEVERE, "Transaction failed", e);

            result.addAction(e.getCode() != null ? e.getCode() : "InvalidParameterValue", 
                e.getLocator(), e.getMessage());
        }

        // commit
        boolean committed = false;

        try {
            if (exception) {
                transaction.rollback();
            } else {
                // inform plugins we're about to commit
                for (Iterator it = transactionPlugins.iterator(); it.hasNext();) {
                    TransactionPlugin tp = (TransactionPlugin) it.next();
                    fireBeforeCommit(request, tp);
                }

                transaction.commit();
                committed = true;

                //                  
                // Lets deal with the locks
                //
                // Q: Why talk to Data you ask
                // A: Only class that knows all the DataStores
                //
                // We really need to ask all DataStores to release/refresh
                // because we may have locked Features with this Authorizations
                // on them, even though we did not refer to them in this
                // transaction.
                //
                // Q: Why here, why now?
                // A: The opperation was a success, and we have completed the
                // opperation
                //
                // We also need to do this if the opperation is not a success,
                // you can find this same code in the abort method
                // 
                String lockId = request.getLockId();
                if (lockId != null) {
                    if (request.isReleaseActionAll()) {
                        lockRelease(lockId);
                    } else if (request.isReleaseActionSome()) {
                        lockRefresh(lockId);
                    }
                }
            }
        } finally {
            transaction.close();
            transaction = null;
        }

        // inform plugins we're done
        for (Iterator it = transactionPlugins.iterator(); it.hasNext();) {
            TransactionPlugin tp = (TransactionPlugin) it.next();
            fireAfterTransaction(request, committed, tp);
        }

        //        
        // if ( result.getTransactionResult().getStatus().getPARTIAL() != null )
        // {
        // throw new WFSException("Canceling PARTIAL response");
        // }
        //        
        // try {
        // if ( result.getTransactionResult().getStatus().getFAILED() != null )
        // {
        // //transaction failed, roll it back
        // transaction.rollback();
        // }
        // else {
        // transaction.commit();
        // result.getTransactionResult().getStatus().setSUCCESS(
        // WfsFactory.eINSTANCE.createEmptyType() );
        // }
        //        	
        // }
        // finally {
        // transaction.close();
        // transaction = null;
        // }

        // JD: this is an issue with the spec, InsertResults must be present,
        // even if no insert
        // occured, howwever insert results needs to have at least one
        // "FeatureId" eliement, sp
        // we create an FeatureId with an empty fid
        List insertedFeatures = result.getInsertedFeatures();
        if (insertedFeatures != null && insertedFeatures.isEmpty()) {
            result.addInsertedFeature(null, filterFactory.featureId("none"));
        }

        return result;

        // we will commit in the writeTo method
        // after user has got the response
        // response = build;
    }

    void fireAfterTransaction(Object request, boolean committed, TransactionPlugin tp) {
        TransactionType tx = toTransaction11(request);
        if (tx != null) tp.afterTransaction(tx, committed);
    }

    void fireBeforeCommit(Object request, TransactionPlugin tp) {
        TransactionType tx = toTransaction11(request);
        if (tx != null) tp.beforeCommit(tx);
    }

    void fireBeforeTransaction(Object request, TransactionPlugin tp) {
        TransactionType tx = toTransaction11(request);
        if (tx != null) tp.beforeTransaction(tx);
    }

    /**
     * Looks up the element handlers to be used for each element
     *
     * @param group
     * @return
     */
    private Map gatherElementHandlers(TransactionRequest request)
        throws WFSTransactionException {
        //JD: use a linked hashmap since the order of elements in a transaction
        // must be respected
        Map map = new LinkedHashMap();

        List<TransactionElement> elements = request.getElements();
        for (TransactionElement element : elements) {
            map.put(element, findElementHandler(element.getClass()));
        }

        return map;
    }

    /**
     * Finds the best transaction element handler for the specified element type
     * (the one matching the most specialized superclass of type)
     *
     * @param type
     * @return
     */
    protected final TransactionElementHandler findElementHandler(Class type)
        throws WFSTransactionException {
        List matches = new ArrayList();

        for (Iterator it = transactionElementHandlers.iterator(); it.hasNext();) {
            TransactionElementHandler handler = (TransactionElementHandler) it.next();

            if (handler.getElementClass().isAssignableFrom(type)) {
                matches.add(handler);
            }
        }

        if (matches.isEmpty()) {
            // try to instantiate one
            String msg = "No transaction element handler for : ( " + type + " )";
            throw new WFSTransactionException(msg);
        }

        if (matches.size() > 1) {
            // sort by class hierarchy
            Comparator comparator = new Comparator() {
                    public int compare(Object o1, Object o2) {
                        TransactionElementHandler h1 = (TransactionElementHandler) o1;
                        TransactionElementHandler h2 = (TransactionElementHandler) o2;

                        if (h2.getElementClass().isAssignableFrom(h1.getElementClass())) {
                            return -1;
                        }

                        return 1;
                    }
                };

            Collections.sort(matches, comparator);
        }

        return (TransactionElementHandler) matches.get(0);
    }

    /**
     * Creates a gt2 transaction used to execute the transaction call
     *
     * @return
     */
    protected DefaultTransaction getDatastoreTransaction(TransactionRequest request)
    throws IOException {
        DefaultTransaction transaction = new DefaultTransaction();
        // use handle as the log messages
        String username = "anonymous";
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if(authentication != null) {
            Object principal = authentication.getPrincipal();
            if(principal instanceof UserDetails) {
                username = ((UserDetails) principal).getUsername(); 
            }
        }
        
        // Ok, this is a hack. We assume there is only one versioning datastore, the postgis one,
        // and that we can the following properties won't hurt transactio processing anyways...
        transaction.putProperty("VersioningCommitAuthor", username);
        transaction.putProperty("VersioningCommitMessage", request.getHandle());
    
        return transaction;
    }

    /*
     * (non-Javadoc)
     *
     * @see org.vfny.geoserver.responses.Response#abort()
     */
    public void abort(TransactionRequest request) {
        if (transaction == null) {
            return; // no transaction to rollback
        }

        try {
            transaction.rollback();
            transaction.close();
        } catch (IOException ioException) {
            // nothing we can do here
            LOGGER.log(Level.SEVERE, "Failed trying to rollback a transaction:" + ioException);
        }

        String lockId = request.getLockId();
        if (lockId != null) {
            if (request.isReleaseActionSome()) {
                try {
                    lockRefresh(lockId);
                } catch (Exception e) {
                    LOGGER.log(Level.WARNING, "Error occured refreshing lock", e);
                }
            } else if (request.isReleaseActionAll()) {
                try {
                    lockRelease(lockId);
                } catch (Exception e) {
                    LOGGER.log(Level.WARNING, "Error occured releasing lock", e);
                }
            }
        }
    }

    void lockRelease(String lockId) throws WFSException {
        LockFeature lockFeature = new LockFeature(wfs, catalog);
        lockFeature.release(lockId);
    }

    /**
     * Implement lockExists.
     *
     * @param lockID
     *
     * @return true if lockID exists
     *
     * @see org.geotools.data.Data#lockExists(java.lang.String)
     */
    private boolean lockExists(String lockId) throws Exception {
        LockFeature lockFeature = new LockFeature(wfs, catalog);

        return lockFeature.exists(lockId);
    }

    /**
     * Refresh lock by authorization
     *
     * <p>
     * Should use your own transaction?
     * </p>
     *
     * @param lockID
     */
    private void lockRefresh(String lockId) throws Exception {
        LockFeature lockFeature = new LockFeature(wfs, catalog);
        lockFeature.refresh(lockId);
    }

    TransactionType toTransaction11(Object request) {
        if (request instanceof TransactionType) {
            return (TransactionType) request;
        }
        
        //TODO: copy the new transactionType object into the old
        return null;
    }
    /**
     * Bounces the single callback we got from transaction event handlers to all
     * registered listeners
     *
     * @author Andrea Aime - TOPP
     *
     */
    private class TransactionListenerMux implements TransactionListener {
        public void dataStoreChange(List listeners, TransactionEvent event)
            throws WFSException {
            for (Iterator it = listeners.iterator(); it.hasNext();) {
                TransactionListener listener = (TransactionListener) it.next();
                listener.dataStoreChange(event);
            }
        }

        public void dataStoreChange(TransactionEvent event)
            throws WFSException {
            dataStoreChange(transactionPlugins, event);
            dataStoreChange(transactionListeners, event);
        }
    }
}
