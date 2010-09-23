/* Copyright (c) 2001 - 2008 TOPP - www.openplans.org. All rights reserved.
 * This code is licensed under the GPL 2.0 license, available at the root
 * application directory.
 */
package org.geoserver.catalog.impl;

import java.net.URI;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.geoserver.catalog.Catalog;
import org.geoserver.catalog.CatalogDAO;
import org.geoserver.catalog.CatalogException;
import org.geoserver.catalog.CatalogFactory;
import org.geoserver.catalog.CatalogInfo;
import org.geoserver.catalog.CatalogVisitor;
import org.geoserver.catalog.CoverageInfo;
import org.geoserver.catalog.CoverageStoreInfo;
import org.geoserver.catalog.DataStoreInfo;
import org.geoserver.catalog.FeatureTypeInfo;
import org.geoserver.catalog.LayerGroupInfo;
import org.geoserver.catalog.LayerInfo;
import org.geoserver.catalog.MapInfo;
import org.geoserver.catalog.NamespaceInfo;
import org.geoserver.catalog.ResourceInfo;
import org.geoserver.catalog.ResourcePool;
import org.geoserver.catalog.StoreInfo;
import org.geoserver.catalog.StyleInfo;
import org.geoserver.catalog.WMSLayerInfo;
import org.geoserver.catalog.WorkspaceInfo;
import org.geoserver.catalog.event.CatalogAddEvent;
import org.geoserver.catalog.event.CatalogEvent;
import org.geoserver.catalog.event.CatalogListener;
import org.geoserver.catalog.event.CatalogModifyEvent;
import org.geoserver.catalog.event.CatalogPostModifyEvent;
import org.geoserver.catalog.event.CatalogRemoveEvent;
import org.geoserver.catalog.event.impl.CatalogAddEventImpl;
import org.geoserver.catalog.event.impl.CatalogModifyEventImpl;
import org.geoserver.catalog.event.impl.CatalogPostModifyEventImpl;
import org.geoserver.catalog.event.impl.CatalogRemoveEventImpl;
import org.geoserver.platform.GeoServerResourceLoader;
import org.geotools.util.logging.Logging;
import org.opengis.feature.type.Name;

/**
 * A default catalog implementation that is memory based.
 * 
 * @author Justin Deoliveira, The Open Planning Project
 * 
 * TODO: remove synchronized blocks, make setting of default workspace/namespace part
 *   of dao contract
 * TODO: move resolve() to dao 
 *   
 */
public class CatalogImpl implements Catalog {
    
    /**
     * logger
     */
    private static final Logger LOGGER = Logging.getLogger(CatalogImpl.class);

    /**
     * dao
     */
    protected CatalogDAO dao;
    
    /**
     * listeners
     */
    protected List listeners = new ArrayList();

    /** 
     * resources
     */
    protected ResourcePool resourcePool;
    protected GeoServerResourceLoader resourceLoader;

    public CatalogImpl() {
        dao = new DefaultCatalogDAO(this);
        resourcePool = new ResourcePool(this);
    }
    
    public CatalogDAO getDAO() {
        return dao;
    }
    
    public void setDAO(CatalogDAO dao) {
        this.dao = dao;
        dao.setCatalog(this);
    }
    
    public String getId() {
        return "catalog";
    }
    
    public CatalogFactory getFactory() {
        return new CatalogFactoryImpl( this );
    }

    // Store methods
    public void add(StoreInfo store) {
        
        if ( store.getWorkspace() == null ) {
            store.setWorkspace( getDefaultWorkspace() );
        }

        validate(store, true);
        
        //TODO: remove synchronized block, need transactions
        synchronized (dao) {
            dao.add(store);
            
            // if there is no default store use this one as the default
            if(getDefaultDataStore(store.getWorkspace()) == null && store instanceof DataStoreInfo) {
                setDefaultDataStore(store.getWorkspace(), (DataStoreInfo) store);
            }
        }
        added(store);
    }

    void validate(StoreInfo store, boolean isNew) {
        if ( isNull(store.getName()) ) {
            throw new IllegalArgumentException( "Store name must not be null");
        }
        if ( store.getWorkspace() == null ) {
            throw new IllegalArgumentException( "Store must be part of a workspace");
        }
        
        WorkspaceInfo workspace = store.getWorkspace();
        StoreInfo existing = getStoreByName( workspace, store.getName(), StoreInfo.class );
        if ( existing != null && !existing.getId().equals( store.getId() )) {
            String msg = "Store '"+ store.getName() +"' already exists in workspace '"+workspace.getName()+"'";
            throw new IllegalArgumentException( msg );
        }    
    }
    
    public void remove(StoreInfo store) {
        if ( !getResourcesByStore(store, ResourceInfo.class).isEmpty() ) {
            throw new IllegalArgumentException( "Unable to delete non-empty store.");
        }
        
        //TODO: remove synchronized block, need transactions
        synchronized(dao) {
            dao.remove(store);
            
            WorkspaceInfo workspace = store.getWorkspace();
            DataStoreInfo defaultStore = getDefaultDataStore(workspace);
            if (store.equals(defaultStore)) {
                //TODO: this will fire multiple events, we want to fire only one
                setDefaultDataStore(workspace, null);
                
                // default removed, choose another store to become default if possible
                List dstores = getStoresByWorkspace(workspace, DataStoreInfo.class);
                if (!dstores.isEmpty()) {
                    setDefaultDataStore(workspace, (DataStoreInfo) dstores.get(0));
                }
            }
        }
        
        removed(store);
    }

    public void save(StoreInfo store) {
        validate(store, false);
        dao.save(store);
    }

    public <T extends StoreInfo> T getStore(String id, Class<T> clazz) {
        return dao.getStore(id, clazz);
    }

    public <T extends StoreInfo> T getStoreByName(String name, Class<T> clazz) {
        return getStoreByName((WorkspaceInfo) null, name, clazz);
    }

    public <T extends StoreInfo> T getStoreByName(WorkspaceInfo workspace,
            String name, Class<T> clazz) {
        
        WorkspaceInfo ws = workspace;
        if (ws == null) {
            ws = getDefaultWorkspace();
        }
        
        if(DataStoreInfo.class == clazz && (name == null || name.equals(Catalog.DEFAULT))) {
            return (T) getDefaultDataStore(workspace);
        }
        
        T store = dao.getStoreByName(ws, name, clazz);
        if (store == null && workspace == null) {
            store = dao.getStoreByName(DefaultCatalogDAO.ANY_WORKSPACE, name, clazz);
        }
        return store;
    }

    public <T extends StoreInfo> T getStoreByName(String workspaceName,
           String name, Class<T> clazz) {
        if (workspaceName == null) {
            return getStoreByName((WorkspaceInfo)null, name, clazz);
        }
        
        WorkspaceInfo workspace = getWorkspaceByName(workspaceName);
        if (workspace != null) {
            return getStoreByName(workspace, name, clazz);
        }
        return null;
    }
    
    public <T extends StoreInfo> List<T> getStoresByWorkspace(
            String workspaceName, Class<T> clazz) {
        
        WorkspaceInfo workspace = null;
        if ( workspaceName != null ) {
            workspace = getWorkspaceByName(workspaceName);
            if ( workspace == null ) {
                return Collections.EMPTY_LIST;
            }
        }
        
        return getStoresByWorkspace(workspace, clazz);
        
    }

    public <T extends StoreInfo> List<T> getStoresByWorkspace(
            WorkspaceInfo workspace, Class<T> clazz) {

        return dao.getStoresByWorkspace(workspace, clazz);
    }

    public List getStores(Class clazz) {
        return dao.getStores(clazz);
    }

    public DataStoreInfo getDataStore(String id) {
        return (DataStoreInfo) getStore(id, DataStoreInfo.class);
    }

    public DataStoreInfo getDataStoreByName(String name) {
        return (DataStoreInfo) getStoreByName(name,DataStoreInfo.class);
    }

    public DataStoreInfo getDataStoreByName(String workspaceName, String name) {
        return (DataStoreInfo) getStoreByName(workspaceName, name, DataStoreInfo.class);
    }

    public DataStoreInfo getDataStoreByName(WorkspaceInfo workspace, String name) {
        return (DataStoreInfo) getStoreByName(workspace, name, DataStoreInfo.class);
    }

    public List<DataStoreInfo> getDataStoresByWorkspace(String workspaceName) {
        return getStoresByWorkspace( workspaceName, DataStoreInfo.class );
    }
    
    public List<DataStoreInfo> getDataStoresByWorkspace(WorkspaceInfo workspace) {
        return getStoresByWorkspace( workspace, DataStoreInfo.class );
    }

    public List getDataStores() {
        return getStores(DataStoreInfo.class);
    }
    
    public DataStoreInfo getDefaultDataStore(WorkspaceInfo workspace) {
        return dao.getDefaultDataStore(workspace);
    }
    
    public void setDefaultDataStore(WorkspaceInfo workspace, DataStoreInfo store) {
        if (store != null) {
            // basic sanity check
            if (store.getWorkspace() == null) {
                throw new IllegalArgumentException("The store has not been assigned a workspace");
            }
    
            if (!store.getWorkspace().equals(workspace)) {
                throw new IllegalArgumentException("Trying to mark as default " + "for workspace "
                        + workspace.getName() + " a store that " + "is contained in "
                        + store.getWorkspace().getName());
            }
        }
        dao.setDefaultDataStore(workspace, store);
    }

    public CoverageStoreInfo getCoverageStore(String id) {
        return (CoverageStoreInfo) getStore(id, CoverageStoreInfo.class);
    }

    public CoverageStoreInfo getCoverageStoreByName(String name) {
        return (CoverageStoreInfo) getStoreByName(name, CoverageStoreInfo.class);
    }
    
    public CoverageStoreInfo getCoverageStoreByName(String workspaceName,
            String name) {
        return getStoreByName(workspaceName,name,CoverageStoreInfo.class);
    }
    
    public CoverageStoreInfo getCoverageStoreByName(WorkspaceInfo workspace,
            String name) {
        return getStoreByName(workspace, name,CoverageStoreInfo.class);
    }
    
    public List<CoverageStoreInfo> getCoverageStoresByWorkspace(
            String workspaceName) {
        return getStoresByWorkspace( workspaceName, CoverageStoreInfo.class );
    }
    
    public List<CoverageStoreInfo> getCoverageStoresByWorkspace(
            WorkspaceInfo workspace) {
        return getStoresByWorkspace( workspace, CoverageStoreInfo.class );
    }

    public List getCoverageStores() {
        return getStores(CoverageStoreInfo.class);
    }

    // Resource methods
    public void add(ResourceInfo resource) {
        if ( resource.getNamespace() == null ) {
            //default to default namespace
            resource.setNamespace( getDefaultNamespace() );
        }
        if ( resource.getNativeName() == null ) {
            resource.setNativeName(resource.getName());
        }
        validate(resource,true);
        
        dao.add(resource);
        added(resource);
    }

    void validate(ResourceInfo resource, boolean isNew) {
        if ( isNull(resource.getName()) ) {
            throw new NullPointerException( "Resource name must not be null");
        }
        if ( isNull(resource.getNativeName())) {
            throw new NullPointerException( "Resource native name must not be null");
        }
        if ( resource.getStore() == null ) {
            throw new IllegalArgumentException( "Resource must be part of a store");
        }
        if ( resource.getNamespace() == null ) {
            throw new IllegalArgumentException( "Resource must be part of a namespace");
        }
        
        StoreInfo store = resource.getStore();
        ResourceInfo existing = getResourceByStore( store, resource.getName(), ResourceInfo.class);
        if ( existing != null && !existing.getId().equals( resource.getId() ) ) {
            String msg = "Resource named '"+resource.getName()+"' already exists in store: '"+ store.getName()+"'";
            throw new IllegalArgumentException( msg );
        }
        
        NamespaceInfo namespace = resource.getNamespace();
        existing =  getResourceByName( namespace, resource.getName(), ResourceInfo.class);
        if ( existing != null && !existing.getId().equals( resource.getId() ) ) {
            String msg = "Resource named '"+resource.getName()+"' already exists in namespace: '"+ namespace.getPrefix()+"'";
            throw new IllegalArgumentException( msg );
        }
        
    }
    
    public void remove(ResourceInfo resource) {
        //ensure no references to the resource
        if ( !getLayers( resource ).isEmpty() ) {
            throw new IllegalArgumentException( "Unable to delete resource referenced by layer");
        }
        dao.remove(resource);
        removed(resource);
    }

    public void save(ResourceInfo resource) {
        validate(resource,false);
        dao.save(resource);
    }

    public <T extends ResourceInfo> T getResource(String id, Class<T> clazz) {
        return dao.getResource(id, clazz);
    }

    public <T extends ResourceInfo> T getResourceByName(String ns, String name, Class<T> clazz) {
        if ("".equals( ns ) ) {
              ns = null;
        }

        if (ns != null) {
            NamespaceInfo namespace = getNamespaceByPrefix(ns);
            if (namespace == null) {
                namespace = getNamespaceByURI(ns); 
            }
            
            if (namespace != null) {
                return getResourceByName(namespace, name, clazz);
            }
            
            return null;
        }

        return getResourceByName((NamespaceInfo) null, name, clazz);

    }
    
    public <T extends ResourceInfo> T getResourceByName(NamespaceInfo ns,
            String name, Class<T> clazz) {
        
        NamespaceInfo namespace = ns;
        if (namespace == null) {
            namespace = getDefaultNamespace();
        }
        T resource = dao.getResourceByName(namespace, name, clazz);
        if (resource == null && ns == null) {
            resource = dao.getResourceByName(DefaultCatalogDAO.ANY_NAMESPACE, name, clazz);
        }
        return resource;
    }

    public <T extends ResourceInfo> T getResourceByName(Name name, Class<T> clazz) {
        return getResourceByName( name.getNamespaceURI(), name.getLocalPart(), clazz );
    }
    
    public <T extends ResourceInfo> T getResourceByName( String name, Class<T> clazz ) {
        ResourceInfo resource;
        
        // check is the name is a fully qualified one
        int colon = name.indexOf( ':' );
        if ( colon != -1 ) {
            String ns = name.substring(0, colon);
            String localName = name.substring(colon + 1);
            return getResourceByName(ns, localName, clazz);
        }
        else {
            return getResourceByName((String)null,name,clazz);
        }
    }
    
    public List getResources(Class clazz) {
        return dao.getResources(clazz);
    }

    public List getResourcesByNamespace(NamespaceInfo namespace, Class clazz) {
        return dao.getResourcesByNamespace(namespace, clazz);
    }
    
    public <T extends ResourceInfo> List<T> getResourcesByNamespace(
            String namespace, Class<T> clazz) {
        if ( namespace == null ) {
            return getResourcesByNamespace((NamespaceInfo)null,clazz); 
        }
        
        NamespaceInfo ns = getNamespaceByPrefix(namespace);
        if ( ns == null ) {
            ns = getNamespaceByURI(namespace);
        }
        if ( ns == null ) {
            return Collections.EMPTY_LIST;
        }
        
        return getResourcesByNamespace(ns, clazz);
    }

    public <T extends ResourceInfo> T getResourceByStore(StoreInfo store,
            String name, Class<T> clazz) {
        return dao.getResourceByStore(store, name, clazz);
    }

    public <T extends ResourceInfo> List<T> getResourcesByStore(
            StoreInfo store, Class<T> clazz) {
        return dao.getResourcesByStore(store, clazz); 
    }

    public FeatureTypeInfo getFeatureType(String id) {
        return (FeatureTypeInfo) getResource(id, FeatureTypeInfo.class);
    }

    public FeatureTypeInfo getFeatureTypeByName(String ns, String name) {
        return (FeatureTypeInfo) getResourceByName(ns, name,
                FeatureTypeInfo.class);
    }

    public FeatureTypeInfo getFeatureTypeByName(NamespaceInfo ns, String name) {
        return getResourceByName(ns, name, FeatureTypeInfo.class );
    }

    public FeatureTypeInfo getFeatureTypeByName(Name name) {
        return getResourceByName(name, FeatureTypeInfo.class);
    }
    
    public FeatureTypeInfo getFeatureTypeByName(String name) {
        return (FeatureTypeInfo) getResourceByName(name, FeatureTypeInfo.class);
    }
    
    public List getFeatureTypes() {
        return getResources(FeatureTypeInfo.class);
    }

    public List getFeatureTypesByNamespace(NamespaceInfo namespace) {
        return getResourcesByNamespace(namespace, FeatureTypeInfo.class);
    }
    
    public FeatureTypeInfo getFeatureTypeByStore(DataStoreInfo dataStore,
            String name) {
        return getFeatureTypeByDataStore(dataStore, name);
    }
    
    public FeatureTypeInfo getFeatureTypeByDataStore(DataStoreInfo dataStore,
            String name) {
        return getResourceByStore( dataStore, name, FeatureTypeInfo.class );
    }
    
    public List<FeatureTypeInfo> getFeatureTypesByStore(DataStoreInfo store) {
        return getFeatureTypesByDataStore(store);
    }

    public List<FeatureTypeInfo> getFeatureTypesByDataStore(DataStoreInfo store) {
        return getResourcesByStore(store, FeatureTypeInfo.class);
    }

    public CoverageInfo getCoverage(String id) {
        return (CoverageInfo) getResource(id, CoverageInfo.class);
    }

    public CoverageInfo getCoverageByName(String ns, String name) {
        return (CoverageInfo) getResourceByName(ns, name, CoverageInfo.class);
    }
    
    public CoverageInfo getCoverageByName(NamespaceInfo ns, String name) {
        return (CoverageInfo) getResourceByName(ns, name, CoverageInfo.class);
    }
    
    public CoverageInfo getCoverageByName(Name name) {
        return getResourceByName(name, CoverageInfo.class);
    }
    
    public CoverageInfo getCoverageByName(String name) {
        return (CoverageInfo) getResourceByName( name, CoverageInfo.class );
    }

    public List getCoverages() {
        return getResources(CoverageInfo.class);
    }

    public List getCoveragesByNamespace(NamespaceInfo namespace) {
        return getResourcesByNamespace(namespace, CoverageInfo.class);
    }
    
    public List<CoverageInfo> getCoveragesByStore(CoverageStoreInfo store) {
        return getResourcesByStore(store,CoverageInfo.class);
    }
    
    public CoverageInfo getCoverageByCoverageStore(
            CoverageStoreInfo coverageStore, String name) {
        return getResourceByStore( coverageStore, name, CoverageInfo.class );
    }
    public List<CoverageInfo> getCoveragesByCoverageStore(
            CoverageStoreInfo store) {
        return getResourcesByStore( store, CoverageInfo.class );
    }

    // Layer methods
    public void add(LayerInfo layer) {
        validate(layer,true);
        
        if ( layer.getType() == null ) {
            if ( layer.getResource() instanceof FeatureTypeInfo ) {
                layer.setType( LayerInfo.Type.VECTOR );
            } else if ( layer.getResource() instanceof CoverageInfo ) {
                layer.setType( LayerInfo.Type.RASTER );
            } else if ( layer.getResource() instanceof WMSLayerInfo ) {
                layer.setType( LayerInfo.Type.WMS );
            } else {
                String msg = "Layer type not set and can't be derived from resource";
                throw new IllegalArgumentException( msg );
            }
        }
        
        dao.add(layer);
        added(layer);
    }

    void validate( LayerInfo layer, boolean isNew) {
        // TODO: bring back when the layer/publishing split is in act
//        if ( isNull(layer.getName()) ) {
//            throw new NullPointerException( "Layer name must not be null" );
//        }
        
        LayerInfo existing = getLayerByName( layer.getName() );
        if ( existing != null && !existing.getId().equals( layer.getId() ) ) {
            //JD: since layers are not qualified by anything (yet), check 
            // namespace of the resource, if they are different then allow the 
            // layer to be added
            if ( existing.getResource().getNamespace().equals( layer.getName() ) ) {
                throw new IllegalArgumentException( "Layer named '"+layer.getName()+"' already exists.");
            }
        }
        
        if ( layer.getResource() == null ) {
            throw new NullPointerException( "Layer resource must not be null" );
        }
        //(JD): not sure if default style should be mandatory
        //if ( layer.getDefaultStyle() == null ){
        //    throw new NullPointerException( "Layer default style must not be null" );
        //}
    }
   
    public void remove(LayerInfo layer) {
        //ensure no references to the layer
        for ( LayerGroupInfo lg : dao.getLayerGroups() ) {
            if ( lg.getLayers().contains( layer ) ) {
                String msg = "Unable to delete layer referenced by layer group '"+lg.getName()+"'";
                throw new IllegalArgumentException( msg );
            }
        }
        dao.remove(layer);
        removed(layer);
    }

    public void save(LayerInfo layer) {
        validate( layer, false );
        dao.save(layer);
    }

    public LayerInfo getLayer(String id) {
        return dao.getLayer(id);
    }
    
    
    public LayerInfo getLayerByName(Name name) {
        if ( name.getNamespaceURI() != null ) {
            NamespaceInfo ns = getNamespaceByURI( name.getNamespaceURI() );
            if ( ns != null ) {
                return getLayerByName( ns.getPrefix() + ":" + name.getLocalPart() );
            }
        }
        
        return getLayerByName( name.getLocalPart() );
    }
    
    public LayerInfo getLayerByName(String name) {
        String prefix = null;
        String resource = null;
        
        int colon = name.indexOf( ':' );
        if ( colon != -1 ) {
            //search by resource name
            prefix = name.substring( 0, colon );
            resource = name.substring( colon + 1 );
            
            ResourceInfo r = getResourceByName(prefix, resource, ResourceInfo.class);
            if (r != null) {
                List<LayerInfo> layers = getLayers(r);
                if (layers.size() == 1) {
                    return layers.get(0);
                }
                
            }
            return null;
        }
        else {
            return dao.getLayerByName(name);
        }

    }

    public List<LayerInfo> getLayers(ResourceInfo resource) {
        return dao.getLayers(resource);
    }
    
    public List<LayerInfo> getLayers(StyleInfo style) {
        return dao.getLayers(style);
    }
    
    public List getLayers() {
        return dao.getLayers();
    }

    // Map methods
    public MapInfo getMap(String id) {
        return dao.getMap(id);
    }

    public MapInfo getMapByName(String name) {
        return dao.getMapByName(name);
    }
    
    public List<MapInfo> getMaps() {
        return dao.getMaps();
    }

    public void add(LayerGroupInfo layerGroup) {
        validate(layerGroup,true);

        layerGroup = dao.add(layerGroup);
        added( layerGroup );
        
        if ( layerGroup.getStyles().isEmpty() ) {
            for ( LayerInfo l : layerGroup.getLayers() ) {
                // default style
                layerGroup.getStyles().add(null);
            }
            dao.save(layerGroup);
        }
    }
    
    void validate( LayerGroupInfo layerGroup, boolean isNew ) {
        if( isNull(layerGroup.getName()) ) {
            throw new NullPointerException( "Layer group name must not be null");
        }
        
        LayerGroupInfo existing = getLayerGroupByName( layerGroup.getName() );
        if ( existing != null && !existing.getId().equals( layerGroup.getId() ) ) {
            throw new IllegalArgumentException( "Layer group named '" + layerGroup.getName() + "' already exists." );
        }
        
        if ( layerGroup.getLayers() == null || layerGroup.getLayers().isEmpty() ) {
            throw new IllegalArgumentException( "Layer group must not be empty");
        }
       
        if ( layerGroup.getStyles() != null && !layerGroup.getStyles().isEmpty() && 
                !(layerGroup.getStyles().size() == layerGroup.getLayers().size()) ) {
            throw new IllegalArgumentException( "Layer group has different number of styles than layers");
        }
    }
    
    public void remove(LayerGroupInfo layerGroup) {
        dao.remove(layerGroup);
        removed( layerGroup );
    }
    
    public void save(LayerGroupInfo layerGroup) {
        validate(layerGroup,false);
        dao.save(layerGroup);
    }
    
    public List<LayerGroupInfo> getLayerGroups() {
        return dao.getLayerGroups();
    }
    
    public LayerGroupInfo getLayerGroup(String id) {
        return dao.getLayerGroup(id);
    }
    
    public LayerGroupInfo getLayerGroupByName(String name) {
        return dao.getLayerGroupByName(name);
    }
    
    public void add(MapInfo map) {
        dao.add(map);
        added(map);
    }

    public void remove(MapInfo map) {
        dao.remove(map);
        removed(map);
    }

    public void save(MapInfo map) {
        dao.save(map);
    }
    
    // Namespace methods
    public NamespaceInfo getNamespace(String id) {
        return dao.getNamespace(id);
    }

    public NamespaceInfo getNamespaceByPrefix(String prefix) {
        if (prefix == null || Catalog.DEFAULT.equals(prefix)) {
            NamespaceInfo ns = getDefaultNamespace();
            if (ns != null) {
                prefix = ns.getPrefix();
            }
        }
        
        return dao.getNamespaceByPrefix(prefix);
    }

    public NamespaceInfo getNamespaceByURI(String uri) {
       return dao.getNamespaceByURI(uri);
    }

    public List getNamespaces() {
        return dao.getNamespaces();
    }

    public void add(NamespaceInfo namespace) {
        validate(namespace,true);
        
        synchronized (dao) {
            dao.add(namespace);
            if ( getDefaultNamespace() == null ) {
                setDefaultNamespace(namespace);
            }
        }
        
        added(namespace);
    }

    void validate(NamespaceInfo namespace, boolean isNew) {
        if ( isNull(namespace.getPrefix()) ) {
            throw new NullPointerException( "Namespace prefix must not be null");
        }
        
        if(namespace.getPrefix().equals(DEFAULT)) {
            throw new IllegalArgumentException(DEFAULT + " is a reserved keyword, can't be used as the namespace prefix");
        }
        
        NamespaceInfo existing = getNamespaceByPrefix( namespace.getPrefix() );
        if ( existing != null && !existing.getId().equals( namespace.getId() ) ) {
            throw new IllegalArgumentException( "Namespace with prefix '" + namespace.getPrefix() + "' already exists.");
        }
        
        existing = getNamespaceByURI( namespace.getURI() );
        if ( existing != null && !existing.getId().equals( namespace.getId() ) ) {
            throw new IllegalArgumentException( "Namespace with URI '" + namespace.getURI() + "' already exists.");
        }
    
        if ( isNull(namespace.getURI()) ) {
            throw new NullPointerException( "Namespace uri must not be null");
        }
        
        try {
            new URI(namespace.getURI());
        } catch(Exception e) {
            throw new IllegalArgumentException("Invalid URI syntax for '" + namespace.getURI() 
                    + "' in namespace '" + namespace.getPrefix() + "'");
        }
    }
    
    public void remove(NamespaceInfo namespace) {
        if ( !getResourcesByNamespace(namespace, ResourceInfo.class ).isEmpty() ) {
            throw new IllegalArgumentException( "Unable to delete non-empty namespace.");
        }

        dao.remove(namespace);
        removed(namespace);
    }

    public void save(NamespaceInfo namespace) {
        validate(namespace,false);
        
        dao.save(namespace);
    }

    public NamespaceInfo getDefaultNamespace() {
        return dao.getDefaultNamespace();
    }

    public void setDefaultNamespace(NamespaceInfo defaultNamespace) {
        NamespaceInfo ns = getNamespaceByPrefix( defaultNamespace.getPrefix() );
        if ( ns == null ) {
            throw new IllegalArgumentException( "No such namespace: '" + defaultNamespace.getPrefix() + "'" );
        }
        dao.setDefaultNamespace(defaultNamespace);
    }

    // Workspace methods
    public void add(WorkspaceInfo workspace) {
        validate(workspace,true);
        
        if ( getWorkspaceByName(workspace.getName()) != null ) {
            throw new IllegalArgumentException( "Workspace with name '" + workspace.getName() + "' already exists.");
        }
        
        synchronized (dao) {
            dao.add(workspace);
            // if there is no default workspace use this one as the default
            if ( getDefaultWorkspace() == null ) {
                setDefaultWorkspace(workspace);
            }
        }
        
        added( workspace );
    }
    
    void validate(WorkspaceInfo workspace, boolean isNew) {
        if ( isNull(workspace.getName()) ) {
            throw new NullPointerException( "workspace name must not be null");
        }
        
        if(workspace.getName().equals(DEFAULT)) {
            throw new IllegalArgumentException(DEFAULT + " is a reserved keyword, can't be used as the workspace name");
        }
        
        WorkspaceInfo existing = getWorkspaceByName( workspace.getName() );
        if ( existing != null && !existing.getId().equals( workspace.getId() ) ) {
            throw new IllegalArgumentException( "Workspace named '"+ workspace.getName() +"' already exists.");
        }
        
    }
    
    public void remove(WorkspaceInfo workspace) {
        //JD: maintain the link between namespace and workspace, remove this when this is no 
        // longer necessary
        if ( getNamespaceByPrefix( workspace.getName() ) != null ) {
            throw new IllegalArgumentException ( "Cannot delete workspace with linked namespace");
        }
        if ( !getStoresByWorkspace( workspace, StoreInfo.class).isEmpty() ) {
            throw new IllegalArgumentException( "Cannot delete non-empty workspace.");
        }
       
        //TODO: remove synchronized block, need transactions
        synchronized(dao) {
            dao.remove(workspace);
         
            WorkspaceInfo defaultWorkspace = getDefaultWorkspace();
            if (workspace.equals(defaultWorkspace) || defaultWorkspace == null) {
                List<WorkspaceInfo> workspaces = dao.getWorkspaces(); 
                
                defaultWorkspace = null;
                if (!workspaces.isEmpty()) {
                    defaultWorkspace = workspaces.get(0);
                }
                
                setDefaultWorkspace(defaultWorkspace);
            }
        }

        removed( workspace );
    }
    
    public void save(WorkspaceInfo workspace) {
        validate(workspace,false);
        
        dao.save(workspace);
    }
    
    public WorkspaceInfo getDefaultWorkspace() {
        return dao.getDefaultWorkspace();
    }
    
    public void setDefaultWorkspace(WorkspaceInfo workspace) {
        if (workspace != null && dao.getWorkspaceByName(workspace.getName()) == null) {
            dao.add(workspace);
        }
        dao.setDefaultWorkspace(workspace);
    }
    
    public List<WorkspaceInfo> getWorkspaces() {
        return dao.getWorkspaces(); 
    }
    
    public WorkspaceInfo getWorkspace(String id) {
        return dao.getWorkspace(id);
    }
    
    public WorkspaceInfo getWorkspaceByName(String name) {
        if (name == null || Catalog.DEFAULT.equals(name)) {
            WorkspaceInfo ws = getDefaultWorkspace();
            if (ws != null) {
                name = ws.getName();
            }
        }
        return dao.getWorkspaceByName(name);
    }
    
    // Style methods
    public StyleInfo getStyle(String id) {
        return dao.getStyle(id);
    }

    public StyleInfo getStyleByName(String name) {
        return dao.getStyleByName(name);
    }

    public List getStyles() {
        return dao.getStyles();
    }

    public void add(StyleInfo style) {
        validate(style,true);
        dao.add(style);
        added(style);
    }

    void validate( StyleInfo style, boolean isNew ) {
        if ( isNull(style.getName()) ) {
            throw new NullPointerException( "Style name must not be null");
        }
        if ( isNull(style.getFilename()) ) {
            throw new NullPointerException( "Style fileName must not be null");
        }
        
        StyleInfo existing = getStyleByName( style.getName() );
        if ( existing != null && !existing.getId().equals( style.getId() )) {
            throw new IllegalArgumentException( "Style named '" +  style.getName() +"' already exists.");
        }
    }
    
    public void remove(StyleInfo style) {
        //ensure no references to the style
        for ( LayerInfo l : dao.getLayers() ) {
            if ( style.equals( l.getDefaultStyle() ) || l.getStyles().contains( style )) {
                throw new IllegalArgumentException( "Unable to delete style referenced by '"+ l.getName()+"'");
            }
        }

        dao.remove(style);
        removed(style);
    }

    public void save(StyleInfo style) {
        validate(style,false);
        dao.save(style);
    }

    // Event methods
    public Collection getListeners() {
        return Collections.unmodifiableCollection(listeners);
    }

    public void addListener(CatalogListener listener) {
        listeners.add(listener);

    }

    public void removeListener(CatalogListener listener) {
        listeners.remove(listener);
    }

    public Iterator search(String cql) {
        // TODO Auto-generated method stub
        return null;
    }

    public ResourcePool getResourcePool() {
        return resourcePool;
    }
    
    public void setResourcePool(ResourcePool resourcePool) {
        this.resourcePool = resourcePool;
    }
    
    public GeoServerResourceLoader getResourceLoader() {
        return resourceLoader;
    }
    public void setResourceLoader(GeoServerResourceLoader resourceLoader) {
        this.resourceLoader = resourceLoader;
    }
    public void dispose() {
        dao.dispose();
        if ( listeners != null ) listeners.clear();
        if ( resourcePool != null ) resourcePool.dispose();
    }
    
    protected void added(CatalogInfo object) {
        fireAdded( object );
    }
    
    protected void removed(CatalogInfo object) {
        fireRemoved( object );
    }
    
    public void fireAdded(CatalogInfo object) {
        CatalogAddEventImpl event = new CatalogAddEventImpl();
        event.setSource(object);

        event(event);
    }
    
    public void fireModified(CatalogInfo object, List propertyNames, List oldValues,
            List newValues) {
        CatalogModifyEventImpl event = new CatalogModifyEventImpl();

        event.setSource(object);
        event.setPropertyNames(propertyNames);
        event.setOldValues(oldValues);
        event.setNewValues(newValues);

        event(event);
    }

    public void firePostModified(CatalogInfo object) {
        CatalogPostModifyEventImpl event = new CatalogPostModifyEventImpl();
        event.setSource( object);
        
        event(event);
    }
    
    public void fireRemoved(CatalogInfo object) {
        CatalogRemoveEventImpl event = new CatalogRemoveEventImpl();
        event.setSource(object);

        event(event);
    }

    protected void event(CatalogEvent event) {
        CatalogException toThrow = null;
        
        for (Iterator l = listeners.iterator(); l.hasNext();) {
            try {
                CatalogListener listener = (CatalogListener) l.next();
                if (event instanceof CatalogAddEvent) {
                    listener.handleAddEvent((CatalogAddEvent) event);
                } else if (event instanceof CatalogRemoveEvent) {
                    listener.handleRemoveEvent((CatalogRemoveEvent) event);
                } else if (event instanceof CatalogModifyEvent) {
                    listener.handleModifyEvent((CatalogModifyEvent) event);
                } else if (event instanceof CatalogPostModifyEvent) {
                    listener.handlePostModifyEvent((CatalogPostModifyEvent)event);
                }
            } catch(Throwable t) {
                if ( t instanceof CatalogException && toThrow == null) {
                    toThrow = (CatalogException) t;
                }
                else {
                    LOGGER.log(Level.WARNING, "Catalog listener threw exception handling event.", t);
                }
            }
        }
        
        if (toThrow != null) {
            throw toThrow;
        }
    }
    
    public static Object unwrap(Object obj) {
        return obj;
    }
    
    /**
     * Implementation method for resolving all {@link ResolvingProxy} instances.
     */
    public void resolve() {
        dao.setCatalog(this);
        dao.resolve();
        
        if ( listeners == null ) {
            listeners = new ArrayList<CatalogListener>();
        }
        
        if ( resourcePool == null ) {
            resourcePool = new ResourcePool(this);
        }
    }
    
    protected boolean isNull( String string ) {
        return string == null || "".equals( string.trim() );
    }
    
    public void sync( CatalogImpl other ) {
        dao.sync(other.dao);
        listeners = other.listeners;
        
        if ( resourcePool != other.resourcePool ) {
            resourcePool.dispose();
            resourcePool = other.resourcePool;
        }
        
        resourceLoader = other.resourceLoader;
    }
    
    public void accept(CatalogVisitor visitor) {
        visitor.visit(this);
    }
    
}
