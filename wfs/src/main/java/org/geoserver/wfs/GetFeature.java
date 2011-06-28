/* Copyright (c) 2001 - 2007 TOPP - www.openplans.org.  All rights reserved.
 * This code is licensed under the GPL 2.0 license, availible at the root
 * application directory.
 */
package org.geoserver.wfs;

import java.io.IOException;
import java.math.BigInteger;
import java.net.URI;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

import javax.xml.namespace.QName;

import net.opengis.wfs.AllSomeType;
import net.opengis.wfs.LockFeatureType;
import net.opengis.wfs.LockType;
import net.opengis.wfs.WfsFactory;
import net.opengis.wfs.XlinkPropertyNameType;
import net.opengis.wfs20.StoredQueryType;

import org.geoserver.catalog.AttributeTypeInfo;
import org.geoserver.catalog.Catalog;
import org.geoserver.catalog.FeatureTypeInfo;
import org.geoserver.catalog.NamespaceInfo;
import org.geoserver.catalog.ResourcePool;
import org.geoserver.feature.TypeNameExtractingVisitor;
import org.geoserver.wfs.request.FeatureCollectionResponse;
import org.geoserver.wfs.request.GetFeatureRequest;
import org.geoserver.wfs.request.Lock;
import org.geoserver.wfs.request.LockFeatureRequest;
import org.geoserver.wfs.request.LockFeatureResponse;
import org.geoserver.wfs.request.Query;
import org.geotools.data.DataUtilities;
import org.geotools.data.FeatureSource;
import org.geotools.data.Join;
import org.geotools.data.simple.SimpleFeatureCollection;
import org.geotools.factory.Hints;
import org.geotools.feature.FeatureCollection;
import org.geotools.feature.FeatureTypes;
import org.geotools.feature.NameImpl;
import org.geotools.feature.SchemaException;
import org.geotools.filter.FilterCapabilities;
import org.geotools.filter.expression.AbstractExpressionVisitor;
import org.geotools.filter.visitor.AbstractFilterVisitor;
import org.geotools.filter.visitor.IsFullySupportedFilterVisitor;
import org.geotools.filter.visitor.PostPreProcessFilterSplittingVisitor;
import org.geotools.filter.visitor.SimplifyingFilterVisitor;
import org.geotools.geometry.GeneralEnvelope;
import org.geotools.geometry.jts.LiteCoordinateSequenceFactory;
import org.geotools.referencing.CRS;
import org.geotools.referencing.crs.DefaultGeographicCRS;
import org.opengis.feature.Feature;
import org.opengis.feature.simple.SimpleFeatureType;
import org.opengis.feature.type.AttributeDescriptor;
import org.opengis.feature.type.FeatureType;
import org.opengis.feature.type.GeometryDescriptor;
import org.opengis.filter.And;
import org.opengis.filter.ExcludeFilter;
import org.opengis.filter.Filter;
import org.opengis.filter.FilterFactory2;
import org.opengis.filter.Id;
import org.opengis.filter.IncludeFilter;
import org.opengis.filter.Not;
import org.opengis.filter.PropertyIsBetween;
import org.opengis.filter.PropertyIsLike;
import org.opengis.filter.PropertyIsNull;
import org.opengis.filter.expression.Add;
import org.opengis.filter.expression.Divide;
import org.opengis.filter.expression.ExpressionVisitor;
import org.opengis.filter.expression.Multiply;
import org.opengis.filter.expression.PropertyName;
import org.opengis.filter.expression.Subtract;
import org.opengis.filter.sort.SortBy;
import org.opengis.filter.spatial.BBOX;
import org.opengis.filter.spatial.Beyond;
import org.opengis.filter.spatial.BinarySpatialOperator;
import org.opengis.filter.spatial.Contains;
import org.opengis.filter.spatial.Crosses;
import org.opengis.filter.spatial.DWithin;
import org.opengis.filter.spatial.Disjoint;
import org.opengis.filter.spatial.Equals;
import org.opengis.filter.spatial.Intersects;
import org.opengis.filter.spatial.Overlaps;
import org.opengis.filter.spatial.Touches;
import org.opengis.filter.spatial.Within;
import org.opengis.metadata.extent.GeographicBoundingBox;
import org.opengis.referencing.crs.CoordinateReferenceSystem;
import org.xml.sax.helpers.NamespaceSupport;

/**
 * Web Feature Service GetFeature operation.
 * <p>
 * This operation returns an array of {@link org.geotools.feature.FeatureCollection}
 * instances.
 * </p>
 *
 * @author Rob Hranac, TOPP
 * @author Justin Deoliveira, The Open Planning Project, jdeolive@openplans.org
 *
 * @version $Id$
 */
public class GetFeature {
    public static final String SQL_VIEW_PARAMS = "GS_SQL_VIEW_PARAMS";
    
    /** Standard logging instance for class */
    private static final Logger LOGGER = org.geotools.util.logging.Logging.getLogger("org.vfny.geoserver.requests");

    /**
     * Describes the allowed filters we support for join queries.
     */
    private final static FilterCapabilities joinFilterCapabilities;
    static {
        joinFilterCapabilities = new FilterCapabilities();
        
        //simple comparisons
        joinFilterCapabilities.addAll(FilterCapabilities.SIMPLE_COMPARISONS_OPENGIS);
        
        //simple comparisons
        joinFilterCapabilities.addType(PropertyIsNull.class);
        joinFilterCapabilities.addType(PropertyIsBetween.class);
        joinFilterCapabilities.addType(Id.class);
        joinFilterCapabilities.addType(IncludeFilter.class);
        joinFilterCapabilities.addType(ExcludeFilter.class);
        joinFilterCapabilities.addType(PropertyIsLike.class);

        //spatial
        joinFilterCapabilities.addType(BBOX.class);
        joinFilterCapabilities.addType(Contains.class);
        joinFilterCapabilities.addType(Crosses.class);
        joinFilterCapabilities.addType(Disjoint.class);
        joinFilterCapabilities.addType(Equals.class);
        joinFilterCapabilities.addType(Intersects.class);
        joinFilterCapabilities.addType(Overlaps.class);
        joinFilterCapabilities.addType(Touches.class);
        joinFilterCapabilities.addType(Within.class);
        joinFilterCapabilities.addType(DWithin.class);
        joinFilterCapabilities.addType(Beyond.class);

        //we only support simple filters, and any of them And'ed together.
        joinFilterCapabilities.addType(And.class);
    }

    /** The catalog */
    protected Catalog catalog;

    /** The wfs configuration */
    protected WFSInfo wfs;

    /** filter factory */
    protected FilterFactory2 filterFactory;

    /** stored query provider */
    StoredQueryProvider storedQueryProvider;

    /**
     * Creates the WFS 1.0/1.1 GetFeature operation.
     */
    public GetFeature(WFSInfo wfs, Catalog catalog) {
        this.wfs = wfs;
        this.catalog = catalog;
    }

    /**
     * @return The reference to the GeoServer catalog.
     */
    public Catalog getCatalog() {
        return catalog;
    }
    
    /**      
     * @return NamespaceSupport from Catalog
     */
    public NamespaceSupport getNamespaceSupport() {
        NamespaceSupport ns = new NamespaceSupport();
        Iterator<NamespaceInfo> it = getCatalog().getNamespaces().iterator();
        while (it.hasNext()) {
            NamespaceInfo ni = it.next();
            ns.declarePrefix(ni.getPrefix(), ni.getURI());
        }
        return ns;
    }

    /**
     * @return The reference to the WFS configuration.
     */
    public WFSInfo getWFS() {
        return wfs;
    }

    /**
     * Sets the filter factory to use to create filters.
     *
     * @param filterFactory
     */
    public void setFilterFactory(FilterFactory2 filterFactory) {
        this.filterFactory = filterFactory;
    }
    
    /**
     * Sets the stored query provider
     */
    public void setStoredQueryProvider(StoredQueryProvider storedQueryProvider) {
        this.storedQueryProvider = storedQueryProvider;
    }

    public FeatureCollectionResponse run(GetFeatureRequest request)
        throws WFSException {
        List<Query> queries = request.getQueries();

        if (queries.isEmpty()) {
            throw new WFSException(request, "No query specified");
        }

        //stored queries, preprocess compile any stored queries into actual query objects
        processStoredQueries(request);
        queries = request.getQueries();
        
        if (request.isQueryTypeNamesUnset()) {
            //do a check for FeatureId filters in the queries and update the type names for the 
            // queries accordingly
            for (Query q : queries) {
                if (!q.getTypeNames().isEmpty()) continue;
                
                if (q.getFilter() != null) {
                    TypeNameExtractingVisitor v = new TypeNameExtractingVisitor(catalog);
                    q.getFilter().accept(v, null);
                    q.getTypeNames().addAll(v.getTypeNames());
                }

                if (q.getTypeNames().isEmpty()) {
                    String msg = "No feature types specified";
                    throw new WFSException(request, msg);
                }
            }
        }

        // Optimization Idea
        //
        // We should be able to reduce this to a two pass opperations.
        //
        // Pass #1 execute
        // - Attempt to Locks Fids during the first pass
        // - Also collect Bounds information during the first pass
        //
        // Pass #2 writeTo
        // - Using the Bounds to describe our FeatureCollections
        // - Iterate through FeatureResults producing GML
        //
        // And allways remember to release locks if we are failing:
        // - if we fail to aquire all the locks we will need to fail and
        //   itterate through the the FeatureSources to release the locks
        //
        BigInteger bi = request.getMaxFeatures();
        if (bi == null) {
            request.setMaxFeatures(BigInteger.valueOf(Integer.MAX_VALUE));
        }

        // take into consideration the wfs max features
        int maxFeatures = Math.min(request.getMaxFeatures().intValue(), wfs.getMaxFeatures());

        // grab the view params is any
        List<Map<String, String>> viewParams = null;
        if(request.getMetadata() != null) {
            viewParams = (List<Map<String, String>>) request.getMetadata().get(SQL_VIEW_PARAMS);
        }

        int count = 0; //should probably be long

        // total count represents the total count of the features matched for this query in cases
        // where the client has limited the result set size, as an optimization we only calculate
        // this if the following conditions hold
        // 1. the request is wfs 2.0
        // 2. maxFeatures != Integer.MAX_VALUE
        //TODO: we could actually add a third a optimization that when the count of features is 
        // less than maxFeatures we don't have to calculate it since it is the same as count, but 
        // this requires that we do that check post query loop which requires a bit of code 
        // refactoring

        int totalCount = 0;
        if (!request.getVersion().startsWith("2")) {
            totalCount = -1;
        }
        if (totalCount > -1 && maxFeatures == Integer.MAX_VALUE) {
            totalCount = -1;
        }

        //offset into result set in which to return features
        int offset = request.getStartIndex() != null ? request.getStartIndex().intValue() : -1;

        List results = new ArrayList();
        try {
            for (int i = 0; (i < queries.size()) && (count < maxFeatures); i++) {
                Query query = queries.get(i);

                //alias sanity check
                if (!query.getAliases().isEmpty()) {
                    if (query.getAliases().size() != query.getTypeNames().size()) {
                        throw new WFSException(request, String.format("Query specifies %d type names and %d " +
                            "aliases, must be equal", query.getTypeNames().size(), query.getAliases().size())); 
                    }
                }

                List<FeatureTypeInfo> metas = new ArrayList();
                for (QName typeName : query.getTypeNames()) {
                    metas.add(featureTypeInfo(typeName, request));
                }

                //first is the primary feature type
                FeatureTypeInfo meta = metas.get(0);

                // parse the requested property names and distribute among requested types
                List<List<String>> reqPropertyNames = parsePropertyNames(query, metas);

                NamespaceSupport ns = getNamespaceSupport();
                
                List<List<PropertyName>> propNames = new ArrayList();
                List<List<PropertyName>> allPropNames = new ArrayList();
                
                for (int j = 0; j < metas.size(); j++) {
                    List<String> propertyNames = reqPropertyNames.get(j);
                    List<PropertyName> metaPropNames = null;
                    List<PropertyName> metaAllPropNames = null;
                    if (!propertyNames.isEmpty()){
                        
                         metaPropNames = new ArrayList<PropertyName>();
                        
                        for (Iterator iter = propertyNames.iterator(); iter.hasNext();) {
                            PropertyName propName = createPropertyName((String) iter.next(), ns);
        
                            if ( propName.evaluate(meta.getFeatureType()) == null) {
                                String mesg = "Requested property: " + propName + " is " + "not available "
                                    + "for " + meta.getPrefixedName() + ".  ";
                                
                                if (meta.getFeatureType() instanceof SimpleFeatureType) {
                                    List<AttributeTypeInfo> atts = meta.attributes();
                                    List attNames = new ArrayList( atts.size() );
                                    for ( AttributeTypeInfo att : atts ) {
                                        attNames.add( att.getName() );
                                    }
                                    mesg += "The possible propertyName values are: " + attNames;
                                }
        
                                throw new WFSException(request, mesg, "InvalidParameterValue");
                            }
                            
                            metaPropNames.add(propName);
                        }
                        
                        // if we need to force feature bounds computation, we have to load 
                        // all of the geometries, but we'll have to remove them in the 
                        // returned feature type
                        if(wfs.isFeatureBounding()) {
                            metaAllPropNames = addGeometryProperties(meta, metaPropNames);
                        } else {
                            metaAllPropNames = metaPropNames;
                        }     
                        
                        //we must also include any properties that are mandatory ( even if not requested ),
                        // ie. those with minOccurs > 0
                        //only do this for simple features, complex mandatory features are handled by app-schema
                        if (meta.getFeatureType() instanceof SimpleFeatureType) {
                            metaAllPropNames = 
                                DataUtilities.addMandatoryProperties((SimpleFeatureType) meta.getFeatureType(), metaAllPropNames);
                            metaPropNames = 
                                DataUtilities.addMandatoryProperties((SimpleFeatureType) meta.getFeatureType(), metaPropNames);
                        }
                        //for complex features, mandatory properties need to be handled by datastore.
                    }
                    allPropNames.add(metaAllPropNames);
                    propNames.add(metaPropNames);
                }

                //set up joins (if specified)
                List<Join> joins = null;
                
                //make sure filters are sane
                //
                // Validation of filters on non-simple feature types is not yet supported.
                // FIXME: Support validation of filters on non-simple feature types:
                // need to consider xpath properties and how to configure namespace prefixes in
                // GeoTools app-schema FeaturePropertyAccessorFactory.
                Filter filter = query.getFilter();
                
                if (filter == null && metas.size() > 1) {
                    throw new WFSException(request, "Join query must specify a filter");
                }

                if (filter != null && meta.getFeatureType() instanceof SimpleFeatureType) {
                    if (metas.size() > 1) {
                        //ensure that the filter is allowable
                        if (!isValidJoinFilter(filter)) {
                            throw new WFSException(request, 
                                "Unable to preform join with specified filter: " + filter);
                        }
                        //join, need to separate the joining filter from other filters
                        JoinExtractingVisitor extractor = 
                            new JoinExtractingVisitor(metas, query.getAliases());
                        filter.accept(extractor, null);

                        joins = extractor.getJoins();
                        if (joins.size() != metas.size()-1) {
                            throw new WFSException(request, String.format("Query specified %d types but %d " +
                                "join filters were found", metas.size(), extractor.getJoins().size()));
                        }

                        //validate the filter for each join
                        for (int j = 1; j < metas.size(); j++) {
                            Join join = joins.get(j-1);
                            if (join.getFilter() != null) {
                                validateFilter(join.getFilter(), query, metas.get(j), request);
                            }
                        }

                        filter = extractor.getPrimaryFilter();
                        if (filter != null) {
                            validateFilter(filter, query, meta, request);
                        }
                    }
                    else {
                        validateFilter(filter, query, meta, request);
                    }
                }

                // load primary feature source
                Hints hints = null;
                if (joins != null) {
                    hints = new Hints(ResourcePool.JOINS, joins);
                }
                FeatureSource<? extends FeatureType, ? extends Feature> source = 
                    metas.get(0).getFeatureSource(null, hints);

                // handle local maximum
                int queryMaxFeatures = maxFeatures - count;
                int metaMaxFeatures = maxFeatures(metas);
                if (metaMaxFeatures > 0 && metaMaxFeatures < queryMaxFeatures) {
                    queryMaxFeatures = metaMaxFeatures;
                }
                Map<String, String> viewParam = viewParams != null ? viewParams.get(i) : null;
                org.geotools.data.Query gtQuery = toDataQuery(query, filter, offset, queryMaxFeatures, 
                    source, request, allPropNames.get(0), viewParam, joins);

                LOGGER.fine("Query is " + query + "\n To gt2: " + gtQuery);

                FeatureCollection<? extends FeatureType, ? extends Feature> features = getFeatures(request, source, gtQuery);

                // For complex features, we need the targetCrs and version in scenario where we have
                // a top level feature that does not contain a geometry(therefore no crs) and has a
                // nested feature that contains geometry as its property.Furthermore it is possible
                // for each nested feature to have different crs hence we need to reproject on each
                // feature accordingly.
                if (!(meta.getFeatureType() instanceof SimpleFeatureType)) {
                    features.getSchema().getUserData().put("targetCrs", query.getSrsName());
                    features.getSchema().getUserData().put("targetVersion", request.getVersion());
                }

                //feature collection size, we may need to calculate it
                boolean calculateSize = true;

                // optimization: WFS 1.0 does not require count unless we have multiple query elements
                // and we are asked to perform a global limit on the results returned
                calculateSize = !(("1.0".equals(request.getVersion()) || "1.0.0".equals(request.getVersion())) && 
                    (queries.size() == 1 || maxFeatures == Integer.MAX_VALUE));
                
                if (!calculateSize) {
                    //if offset was specified and we have more queries left in this request then we 
                    // must calculate size in order to adjust the offset 
                    calculateSize = offset > 0 && i < queries.size() - 1; 
                }

                int size = 0;
                if (calculateSize) {
                    size = features.size();
                }
                
                //update the count
                count += size;
                
                //if offset is present we need to check the size of this returned feature collection
                // and adjust the offset for the next feature collection accordingly
                if (offset > 0) {
                    if (size > 0) {
                        //features returned, offset can be set to zero
                        offset = 0;
                    }
                    else {
                        //no features might have been because of the offset that was specified, check 
                        // the size of the same query but with no offset
                        org.geotools.data.Query q2 = toDataQuery(query, filter, 0, queryMaxFeatures, 
                            source, request, allPropNames.get(0), viewParam, joins);
                        
                        //int size2 = getFeatures(request, source, q2).size();
                        int size2 = source.getCount(q2);
                        if (size2 > 0) {
                            //adjust the offset for the next query
                            offset = Math.max(0, offset - size2);
                        }
                    }
                }

                //numberMatched/totalSize
                if (totalCount > -1) {
                    //check maxFeatures and offset, if they are unset we can use the size we 
                    // calculated above
                    if (calculateSize && queryMaxFeatures == Integer.MAX_VALUE && offset == 0) {
                        totalCount += size;
                    }
                    else {
                        org.geotools.data.Query q2 = toDataQuery(query, filter, 0, Integer.MAX_VALUE, 
                            source, request, allPropNames.get(0), viewParam, joins);
                        totalCount += source.getFeatures(q2).size();
                    }
                }

                // we may need to shave off geometries we did load only to make bounds
                // computation happy
                // TODO: support non-SimpleFeature geometry shaving
                List<PropertyName> metaPropNames = propNames.get(0);
                if(features.getSchema() instanceof SimpleFeatureType && metaPropNames!=null && metaPropNames.size() < allPropNames.get(0).size()) {
                    String[] residualNames = new String[metaPropNames.size()];
                    Iterator<PropertyName> it = metaPropNames.iterator();
                    int j =0;
                    while (it.hasNext()) {
                        residualNames[j] = it.next().getPropertyName();
                        j++;
                    }
                    SimpleFeatureType targetType = DataUtilities.createSubType((SimpleFeatureType) features.getSchema(), residualNames);
                    features = new FeatureBoundsFeatureCollection((SimpleFeatureCollection) features, targetType);
                }

                //JD: TODO reoptimize
                //                if ( i == request.getQuery().size() - 1 ) { 
                //                	//DJB: dont calculate feature count if you dont have to. The MaxFeatureReader will take care of the last iteration
                //                	maxFeatures -= features.getCount();
                //                }

                //GR: I don't know if the featuresults should be added here for later
                //encoding if it was a lock request. may be after ensuring the lock
                //succeed?
                results.add(features);
            }
        } catch (IOException e) {
            throw new WFSException(request, "Error occurred getting features", e, request.getHandle());
        } catch (SchemaException e) {
            throw new WFSException(request, "Error occurred getting features", e, request.getHandle());
        }

        //locking
        String lockId = null;
        if (request.isLockRequest()) {
            LockFeatureRequest lockRequest = request.createLockRequest();
            lockRequest.setExpiry(request.getExpiry());
            lockRequest.setHandle(request.getHandle());
            lockRequest.setLockActionAll();
            
            for (int i = 0; i < queries.size(); i++) {
                Query query = queries.get(i);

                Lock lock = lockRequest.createLock();
                lock.setFilter(query.getFilter());
                lock.setHandle(query.getHandle());

                //TODO: joins?
                List<QName> typeNames = query.getTypeNames();
                lock.setTypeName(typeNames.get(0));
                lockRequest.addLock(lock);
            }

            LockFeature lockFeature = new LockFeature(wfs, catalog);
            lockFeature.setFilterFactory(filterFactory);

            LockFeatureResponse response = lockFeature.lockFeature(lockRequest);
            lockId = response.getLockId();
        }

        return buildResults(request, count, totalCount, results, lockId);
    }

    protected void processStoredQueries(GetFeatureRequest request) {
        List queries = request.getAdaptedQueries();
        for (int i = 0; i < queries.size(); i++) {
            Object obj = queries.get(i);
            if (obj instanceof StoredQueryType) {
                
                if (storedQueryProvider == null) {
                    throw new WFSException(request, "Stored query not supported");
                }

                StoredQueryType sq = (StoredQueryType) obj;

                //look up the store query
                StoredQuery storedQuery = storedQueryProvider.getStoredQuery(sq.getId());
                if (storedQuery == null) {
                    throw new WFSException(request, "Stored query '" + sq.getId() + "' does not exist.");
                }

                List<net.opengis.wfs20.QueryType> compiled = storedQuery.compile(sq);
                queries.remove(i);
                queries.addAll(i, compiled);
                i += compiled.size();
            }
        }
    }
    
    /**
     * Allows subclasses to alter the result generation
     */
    protected FeatureCollectionResponse buildResults(GetFeatureRequest request, int count, int total, 
        List results, String lockId) {

        FeatureCollectionResponse result = request.createResponse();
        result.setNumberOfFeatures(BigInteger.valueOf(count));
        result.setTotalNumberOfFeatures(BigInteger.valueOf(total));
        result.setTimeStamp(Calendar.getInstance());
        result.setLockId(lockId);
        result.getFeature().addAll(results);
        return result;
    }

    /**
     * Allows subclasses to poke with the feature collection extraction
     * @param source
     * @param gtQuery
     * @return
     * @throws IOException
     */
    protected FeatureCollection<? extends FeatureType, ? extends Feature> getFeatures(
            Object request, FeatureSource<? extends FeatureType, ? extends Feature> source,
            org.geotools.data.Query gtQuery)
            throws IOException {
        return source.getFeatures(gtQuery);
    }

    /**
     * Get this query as a geotools Query.
     *
     * <p>
     * if maxFeatures is a not positive value Query.DEFAULT_MAX will be
     * used.
     * </p>
     *
     * <p>
     * The method name is changed to toDataQuery since this is a one way
     * conversion.
     * </p>
     *
     * @param maxFeatures number of features, or 0 for Query.DEFAULT_MAX
     *
     * @return A Query for use with the FeatureSource interface
     *
     */
    public org.geotools.data.Query toDataQuery(Query query, Filter filter, int offset, int maxFeatures,
        FeatureSource<? extends FeatureType, ? extends Feature> source, GetFeatureRequest request, 
        List<PropertyName> props, Map<String, String> viewParams, List<Join> joins) throws WFSException {
        
        String wfsVersion = request.getVersion();
        
        if (maxFeatures <= 0) {
            maxFeatures = org.geotools.data.Query.DEFAULT_MAX;
        }

        if (filter == null) {
            filter = Filter.INCLUDE;
        } else {
            // Gentlemen, we can rebuild it. We have the technology!
            SimplifyingFilterVisitor visitor = new SimplifyingFilterVisitor();
            filter = (Filter) filter.accept(visitor, null);
        }
        
        //figure out the crs the data is in
        CoordinateReferenceSystem crs = source.getSchema().getCoordinateReferenceSystem();
            
        // gather declared CRS
        CoordinateReferenceSystem declaredCRS = WFSReprojectionUtil.getDeclaredCrs(crs, wfsVersion);
        
        // make sure every bbox and geometry that does not have an attached crs will use
        // the declared crs, and then reproject it to the native crs
        Filter transformedFilter = filter;
        if(declaredCRS != null)
            transformedFilter = WFSReprojectionUtil.normalizeFilterCRS(filter, source.getSchema(), declaredCRS);

        //only handle non-joins for now
        QName typeName = query.getTypeNames().get(0);
        org.geotools.data.Query dataQuery = new org.geotools.data.Query(typeName.getLocalPart(), 
            transformedFilter, maxFeatures, props, query.getHandle());

        //handle reprojection
        CoordinateReferenceSystem target;
        URI srsName = query.getSrsName();
        if (srsName != null) {
            try {
                target = CRS.decode(srsName.toString());
            } catch (Exception e) {
                String msg = "Unable to support srsName: " + srsName;
                throw new WFSException(request, msg, e);
            }
        } else {
            target = declaredCRS;
        }
        //if the crs are not equal, then reproject
        if (target != null && declaredCRS != null && !CRS.equalsIgnoreMetadata(crs, target)) {
            dataQuery.setCoordinateSystemReproject(target);
        }
        
        //handle sorting
        List<SortBy> sortBy = query.getSortBy();
        if (sortBy != null) {
            dataQuery.setSortBy((SortBy[]) sortBy.toArray(new SortBy[sortBy.size()]));
        }

        //handle version, datastore may be able to use it
        String featureVersion = query.getFeatureVersion();
        if (featureVersion != null) {
            dataQuery.setVersion(featureVersion);
        }

        //handle offset / start index
        if (offset > 0) {
            dataQuery.setStartIndex(offset);
        }
        
        //create the Hints to set at the end
        final Hints hints = new Hints();
                
        //handle xlink traversal depth
        String traverseXlinkDepth = request.getTraverseXlinkDepth();
        if (traverseXlinkDepth != null) {
            //TODO: make this an integer in the model, and have hte NumericKvpParser
            // handle '*' as max value
            Integer depth = traverseXlinkDepth( traverseXlinkDepth );
            
            //set the depth as a hint on the query
            hints.put(Hints.ASSOCIATION_TRAVERSAL_DEPTH, depth);
        }
        
        //handle xlink properties
        List<XlinkPropertyNameType> xlinkProperties = query.getXlinkPropertyNames();
        if (!xlinkProperties.isEmpty() ) {
            for ( Iterator x = xlinkProperties.iterator(); x.hasNext(); ) {
                XlinkPropertyNameType xlinkProperty = (XlinkPropertyNameType) x.next();
                
                Integer xlinkDepth = traverseXlinkDepth( xlinkProperty.getTraverseXlinkDepth() );
                
                //set the depth and property as hints on the query
                hints.put(Hints.ASSOCIATION_TRAVERSAL_DEPTH, xlinkDepth );
                
                PropertyName xlinkPropertyName = filterFactory.property( xlinkProperty.getValue() );
                hints.put(Hints.ASSOCIATION_PROPERTY, xlinkPropertyName );
                
                dataQuery.setHints( hints );
                
                //TODO: support multiple properties
                break;
            }
        }
        
        //tell the datastore to use a lite coordinate sequence factory, if possible
        hints.put(Hints.JTS_COORDINATE_SEQUENCE_FACTORY, new LiteCoordinateSequenceFactory());
        
        // check for sql view parameters
        if(viewParams != null) {
            hints.put(Hints.VIRTUAL_TABLE_PARAMETERS, viewParams);
        }
        
        Map metadata = request.getMetadata();
        if(metadata != null && metadata.containsKey(SQL_VIEW_PARAMS)) {
            hints.put(Hints.VIRTUAL_TABLE_PARAMETERS, metadata.get(SQL_VIEW_PARAMS));
        }
        
        //currently only used by app-schema, produce mandatory properties
        hints.put(org.geotools.data.Query.INCLUDE_MANDATORY_PROPS, true);

        //add the joins, if specified
        if (joins != null) {
            dataQuery.getJoins().addAll(joins);
        }

        //finally, set the hints
        dataQuery.setHints(hints);

        return dataQuery;
    }

    static Integer traverseXlinkDepth( String raw ) {
        Integer traverseXlinkDepth = null;
        try {
            traverseXlinkDepth = new Integer( raw );
        }
        catch( NumberFormatException nfe ) {
            //try handling *
            if ( "*".equals( raw ) ) {
                //TODO: JD: not sure what this value should be? i think it 
                // might be reported in teh acapabilitis document, using 
                // INteger.MAX_VALUE will result in stack overflow... for now
                // we just use 10
                traverseXlinkDepth = new Integer( 2 );
            }
            else {
                //not wildcard case, throw original exception
                throw nfe;
            }
        }
        
        return traverseXlinkDepth;
    }

    boolean isValidJoinFilter(Filter filter) {
        PostPreProcessFilterSplittingVisitor visitor = 
            new PostPreProcessFilterSplittingVisitor(joinFilterCapabilities, null, null);
        filter.accept(visitor, null);
        return visitor.getFilterPost() == null || visitor.getFilterPost() == Filter.INCLUDE;
    }

    FeatureTypeInfo featureTypeInfo(QName name, GetFeatureRequest request) throws WFSException, IOException {
        FeatureTypeInfo meta = catalog.getFeatureTypeByName(name.getNamespaceURI(), name.getLocalPart());

        if (meta == null) {
            String msg = "Could not locate " + name + " in catalog.";
            throw new WFSException(request, msg);
        }

        return meta;
    }

    List<List<String>> parsePropertyNames(Query query, List<FeatureTypeInfo> featureTypes) {
        List<List<String>> propNames = new ArrayList();
        for (FeatureTypeInfo featureType: featureTypes) {
            propNames.add(new ArrayList());
        }

        if (featureTypes.size() == 1) {
            //non join
            propNames.get(0).addAll(query.getPropertyNames());
            return propNames;
        }

        //go through all property names and distribute based on prefix accordingly
O:      for (String propName : query.getPropertyNames()) {
            //check for a full typename prefix
            for (int j = 0; j < featureTypes.size(); j++) {
                FeatureTypeInfo featureType = featureTypes.get(j);
                if (propName.startsWith(featureType.getPrefixedName()+"/")) {
                    propNames.get(j).add(propName.substring((featureType.getPrefixedName()+"/").length()));
                    continue O;
                }
                if (propName.startsWith(featureType.getName()+"/")) {
                    propNames.get(j).add(propName.substring((featureType.getName()+"/").length()));
                    continue O;
                }
            }

            if (query.getAliases().isEmpty()) {
                //check for aliases
                for (int j = 0; j < query.getAliases().size(); j++) {
                    String alias = query.getAliases().get(j);
                    if (propName.startsWith(alias+"/")) {
                        propNames.get(j).add(propName.substring((alias+"/").length()));
                        continue O;
                    }
                }
            }

            //fallback on first
            propNames.get(0).add(propName);
        }
        
        return propNames;
    }

    void validateFilter(Filter filter, Query query, FeatureTypeInfo meta, final GetFeatureRequest request) 
        throws IOException {
      //1. ensure any property name refers to a property that 
        // actually exists
        final FeatureType featureType = meta.getFeatureType();
        ExpressionVisitor visitor = new AbstractExpressionVisitor() {
                public Object visit(PropertyName name, Object data) {
                    // case of multiple geometries being returned
                    if (name.evaluate(featureType) == null) {
                        throw new WFSException(request, "Illegal property name: "
                            + name.getPropertyName(), "InvalidParameterValue");
                    }

                    return name;
                }
                ;
            };
        filter.accept(new AbstractFilterVisitor(visitor), null);
        
        //2. ensure any spatial predicate is made against a property 
        // that is actually special
        AbstractFilterVisitor fvisitor = new AbstractFilterVisitor() {
          
            protected Object visit( BinarySpatialOperator filter, Object data ) {
                PropertyName name = null;
                if ( filter.getExpression1() instanceof PropertyName ) {
                    name = (PropertyName) filter.getExpression1();
                }
                else if ( filter.getExpression2() instanceof PropertyName ) {
                    name = (PropertyName) filter.getExpression2();
                }
                
                if ( name != null ) {
                    //check against fetaure type to make sure its
                    // a geometric type
                    AttributeDescriptor att = (AttributeDescriptor) name.evaluate(featureType);
                    if ( !( att instanceof GeometryDescriptor ) ) {
                        throw new WFSException(request, "Property " + name + " is not geometric", "InvalidParameterValue");
                    }
                }
                
                return filter;
            }
        };
        filter.accept(fvisitor, null);
        
        //3. ensure that any bounds specified as part of the query
        // are valid with respect to the srs defined on the query
        if ( wfs.isCiteCompliant() ) {
            
            if ( query.getSrsName() != null ) {
                final Query fquery = query;
                fvisitor = new AbstractFilterVisitor() {
                    public Object visit(BBOX filter, Object data) {
                        if ( filter.getSRS() != null && 
                                !fquery.getSrsName().toString().equals( filter.getSRS() ) ) {
                            
                            //back project bounding box into geographic coordinates
                            CoordinateReferenceSystem geo = DefaultGeographicCRS.WGS84;
                            
                            GeneralEnvelope e = new GeneralEnvelope( 
                                new double[] { filter.getMinX(), filter.getMinY()},
                                new double[] { filter.getMaxX(), filter.getMaxY()}
                            );
                            CoordinateReferenceSystem crs = null;
                            try {
                                crs = CRS.decode( filter.getSRS() );
                                e = CRS.transform(CRS.findMathTransform(crs, geo, true), e);
                            } 
                            catch( Exception ex ) {
                                throw new WFSException( request, ex );
                            }
                            
                            //ensure within bounds defined by srs specified on 
                            // query
                            try {
                                crs = CRS.decode( fquery.getSrsName().toString() );
                            } 
                            catch( Exception ex ) {
                                throw new WFSException( request, ex );
                            }
                            
                            GeographicBoundingBox valid = 
                                (GeographicBoundingBox) crs.getDomainOfValidity()
                                .getGeographicElements().iterator().next();
                            
                            if ( e.getMinimum(0) < valid.getWestBoundLongitude() || 
                                e.getMinimum(0) > valid.getEastBoundLongitude() || 
                                e.getMaximum(0) < valid.getWestBoundLongitude() || 
                                e.getMaximum(0) > valid.getEastBoundLongitude() ||
                                e.getMinimum(1) < valid.getSouthBoundLatitude() || 
                                e.getMinimum(1) > valid.getNorthBoundLatitude() || 
                                e.getMaximum(1) < valid.getSouthBoundLatitude() || 
                                e.getMaximum(1) > valid.getNorthBoundLatitude() ) {
                                    
                                throw new WFSException(request, "bounding box out of valid range of crs", "InvalidParameterValue");
                            }
                        }
                        
                        return data;
                    } 
                };
                
                filter.accept(fvisitor, null);
            }
        }   
    }
    
    int maxFeatures(List<FeatureTypeInfo> metas) {
        int maxFeatures = Integer.MAX_VALUE;
        for (FeatureTypeInfo meta : metas) {
            if (meta.getMaxFeatures() > 0) {
                maxFeatures = Math.min(maxFeatures, meta.getMaxFeatures());
            }
        }
        return maxFeatures;
    }

    protected PropertyName createPropertyName (String path, NamespaceSupport namespaceContext) {
        if (path.contains("/")) {
            return filterFactory.property(path, namespaceContext);
        } else {
            if (path.contains(":")) {
                int i = path.indexOf(":");
                return filterFactory.property(new NameImpl(namespaceContext.getURI(path.substring(0, i)), path.substring(i+1) ));
            } else {
                return filterFactory.property(path);
            }
        }
        
    }
     
    protected List<PropertyName> addGeometryProperties (FeatureTypeInfo meta, List<PropertyName> oldProperties) throws IOException {
        List<AttributeTypeInfo> atts = meta.attributes();
        Iterator ii = atts.iterator();
        
        List<PropertyName> properties = new ArrayList<PropertyName>(oldProperties);

        while (ii.hasNext()) {
            AttributeTypeInfo ati = (AttributeTypeInfo) ii.next();
            PropertyName propName = filterFactory.property (ati.getName());
            
            if(meta.getFeatureType().getDescriptor(ati.getName()) instanceof GeometryDescriptor
                    && !properties.contains(propName) ) {
                properties.add(propName);
            }
        }
        
        return properties;
    }
}
