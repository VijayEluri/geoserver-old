/* Copyright (c) 2001 - 2007 TOPP - www.openplans.org. All rights reserved.
 * This code is licensed under the GPL 2.0 license, available at the root
 * application directory.
 */
package org.geoserver.security;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.acegisecurity.AccessDeniedException;
import org.acegisecurity.AcegiSecurityException;
import org.acegisecurity.Authentication;
import org.acegisecurity.InsufficientAuthenticationException;
import org.acegisecurity.context.SecurityContextHolder;
import org.geoserver.catalog.Catalog;
import org.geoserver.catalog.CatalogDAO;
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
import org.geoserver.catalog.WorkspaceInfo;
import org.geoserver.catalog.event.CatalogListener;
import org.geoserver.catalog.impl.AbstractDecorator;
import org.geoserver.ows.Dispatcher;
import org.geoserver.ows.Request;
import org.geoserver.platform.GeoServerExtensions;
import org.geoserver.platform.GeoServerResourceLoader;
import org.geoserver.security.DataAccessManager.CatalogMode;
import org.geoserver.security.decorators.SecuredCoverageInfo;
import org.geoserver.security.decorators.SecuredCoverageStoreInfo;
import org.geoserver.security.decorators.SecuredDataStoreInfo;
import org.geoserver.security.decorators.SecuredFeatureTypeInfo;
import org.geoserver.security.decorators.SecuredLayerGroupInfo;
import org.geoserver.security.decorators.SecuredLayerInfo;
import org.opengis.feature.type.Name;

/**
 * 
 * @author Andrea Aime - TOPP TODO: - docs - uniform argument order in helper
 *         methods - create wrappers around the returned values so that they can
 *         be used only within the limits of the current user power - move the
 *         layer group checks into this class out of DataAccessManager? - does
 *         administration goes thru this catalog, or directly accesses the
 *         insecured one? Option one, admin directly accesses. Option two, admin
 *         users get a special role that make them uber-powerful (root like) and
 *         everything goes thru the secured catalog
 */
public class SecureCatalogImpl extends AbstractDecorator<Catalog> implements Catalog {

    /**
     * The kind of access we can give the user for a given resource
     */
    public enum AccessLevel {
        HIDDEN,
        METADATA,
        READ_ONLY,
        READ_WRITE
    }

    /**
     * The response to be used when the user tries to go beyond the level
     * that he's authorized to see
     */
    public enum Response {
        HIDE,
        CHALLENGE
    }
    
    /** 
     * The combination of access level granted and response policy (lists only possible cases)
     */
    public enum WrapperPolicy {
        HIDE(AccessLevel.HIDDEN, Response.HIDE),
        METADATA(AccessLevel.METADATA, Response.CHALLENGE),
        RO_CHALLENGE(AccessLevel.READ_ONLY, Response.CHALLENGE),
        RO_HIDE(AccessLevel.READ_ONLY, Response.HIDE),
        RW(AccessLevel.READ_WRITE, Response.HIDE);
        
        public final AccessLevel level;
        public final Response response;
        
        WrapperPolicy(AccessLevel level, Response response) {
            this.level = level;
            this.response = response;
        }
    }
    
    protected DataAccessManager accessManager;

    public SecureCatalogImpl(Catalog catalog) throws Exception {
        this(catalog, lookupDataAccessManager(catalog));
    }
    
    public String getId() {
        return delegate.getId();
    }

    static DataAccessManager lookupDataAccessManager(Catalog catalog) throws Exception {
        DataAccessManager manager = GeoServerExtensions.bean(DataAccessManager.class);
        if (manager == null) {
            manager = new DefaultDataAccessManager(GeoServerExtensions.bean(DataAccessRuleDAO.class));
        }
        else {
            if (manager instanceof DataAccessManagerWrapper) {
                ((DataAccessManagerWrapper)manager).setDelegate(
                    new DefaultDataAccessManager(GeoServerExtensions.bean(DataAccessRuleDAO.class)));
            }
        }
        return manager;
    }

    protected SecureCatalogImpl(Catalog catalog, DataAccessManager manager) {
        super(catalog);
        this.accessManager = manager;
    }

    // -------------------------------------------------------------------
    // SECURED METHODS
    // -------------------------------------------------------------------

    public CoverageInfo getCoverage(String id) {
        return (CoverageInfo) checkAccess(user(), delegate.getCoverage(id));
    }

    public CoverageInfo getCoverageByName(String ns, String name) {
        return (CoverageInfo) checkAccess(user(), delegate.getCoverageByName(ns, name));
    }

    public CoverageInfo getCoverageByName(NamespaceInfo ns, String name) {
        return (CoverageInfo) checkAccess(user(), delegate.getCoverageByName(ns, name));
    }
    
    public CoverageInfo getCoverageByName(Name name) {
        return (CoverageInfo) checkAccess(user(), delegate.getCoverageByName(name));
    }
    
    public CoverageInfo getCoverageByName(String name) {
        return (CoverageInfo) checkAccess(user(), delegate.getCoverageByName(name));
    }

    public List<CoverageInfo> getCoverages() {
        return filterResources(user(), delegate.getCoverages());
    }

    public List<CoverageInfo> getCoveragesByNamespace(NamespaceInfo namespace) {
        return filterResources(user(), delegate.getCoveragesByNamespace(namespace));
    }
    
    public List<CoverageInfo> getCoveragesByCoverageStore(
            CoverageStoreInfo store) {
        return filterResources(user(), delegate.getCoveragesByCoverageStore(store));
    }
    
    public CoverageInfo getCoverageByCoverageStore(
            CoverageStoreInfo coverageStore, String name) {
        return checkAccess(user(), delegate.getCoverageByCoverageStore(coverageStore, name));
    }

    public List<CoverageInfo> getCoveragesByStore(CoverageStoreInfo store) {
        return filterResources(user(), delegate.getCoveragesByStore(store));
    }
    
    public CoverageStoreInfo getCoverageStore(String id) {
        return checkAccess(user(), delegate.getCoverageStore(id));
    }

    public CoverageStoreInfo getCoverageStoreByName(String name) {
        return checkAccess(user(), delegate.getCoverageStoreByName(name));
    }
    
    public CoverageStoreInfo getCoverageStoreByName(String workspaceName,
            String name) {
        return checkAccess(user(), delegate.getCoverageStoreByName(workspaceName,name));
    }
    
    public CoverageStoreInfo getCoverageStoreByName(WorkspaceInfo workspace,
            String name) {
        return checkAccess(user(), delegate.getCoverageStoreByName(workspace,name));
    }
    
    public List<CoverageStoreInfo> getCoverageStoresByWorkspace(
            String workspaceName) {
        return filterStores(user(),delegate.getCoverageStoresByWorkspace(workspaceName));
    }
    
    public List<CoverageStoreInfo> getCoverageStoresByWorkspace(
            WorkspaceInfo workspace) {
        return filterStores(user(),delegate.getCoverageStoresByWorkspace(workspace));
    }

    public List<CoverageStoreInfo> getCoverageStores() {
        return filterStores(user(), delegate.getCoverageStores());
    }

    public DataStoreInfo getDataStore(String id) {
        return checkAccess(user(), delegate.getDataStore(id));
    }

    public DataStoreInfo getDataStoreByName(String name) {
        return checkAccess(user(), delegate.getDataStoreByName(name));
    }
    
    public DataStoreInfo getDataStoreByName(String workspaceName, String name) {
        return checkAccess(user(), delegate.getDataStoreByName(workspaceName,name));
    }
    
    public DataStoreInfo getDataStoreByName(WorkspaceInfo workspace, String name) {
        return checkAccess(user(), delegate.getDataStoreByName(workspace,name)) ;
    }
    
    public List<DataStoreInfo> getDataStoresByWorkspace(String workspaceName) {
        return filterStores(user(), delegate.getDataStoresByWorkspace(workspaceName));
    }

    public List<DataStoreInfo> getDataStoresByWorkspace(WorkspaceInfo workspace) {
        return filterStores(user(), delegate.getDataStoresByWorkspace(workspace));
    }

    public List<DataStoreInfo> getDataStores() {
        return filterStores(user(), delegate.getDataStores());
    }

    public NamespaceInfo getDefaultNamespace() {
        return delegate.getDefaultNamespace();
    }

    public WorkspaceInfo getDefaultWorkspace() {
        return delegate.getDefaultWorkspace();
    }

    public FeatureTypeInfo getFeatureType(String id) {
        return checkAccess(user(), delegate.getFeatureType(id));
    }

    public FeatureTypeInfo getFeatureTypeByName(String ns, String name) {
        return checkAccess(user(), delegate.getFeatureTypeByName(ns, name));
    }
    
    public FeatureTypeInfo getFeatureTypeByName(NamespaceInfo ns, String name) {
        return checkAccess(user(), delegate.getFeatureTypeByName(ns,name));
    }

    public FeatureTypeInfo getFeatureTypeByName(Name name) {
        return checkAccess(user(), delegate.getFeatureTypeByName(name));
    }
    
    public FeatureTypeInfo getFeatureTypeByName(String name) {
        return checkAccess(user(), delegate.getFeatureTypeByName(name));
    }

    public List<FeatureTypeInfo> getFeatureTypes() {
        return filterResources(user(), delegate.getFeatureTypes());
    }

    public List<FeatureTypeInfo> getFeatureTypesByNamespace(NamespaceInfo namespace) {
        return filterResources(user(), delegate.getFeatureTypesByNamespace(namespace));
    }

    public FeatureTypeInfo getFeatureTypeByStore(DataStoreInfo dataStore,
            String name) {
        return checkAccess(user(), delegate.getFeatureTypeByStore(dataStore , name));
    }
    public FeatureTypeInfo getFeatureTypeByDataStore(DataStoreInfo dataStore,
            String name) {
        return checkAccess(user(), delegate.getFeatureTypeByDataStore(dataStore , name));
    }
    
    public List<FeatureTypeInfo> getFeatureTypesByStore(DataStoreInfo store) {
        return filterResources(user(), delegate.getFeatureTypesByStore(store));
    }
    public List<FeatureTypeInfo> getFeatureTypesByDataStore(DataStoreInfo store) {
        return filterResources(user(), delegate.getFeatureTypesByDataStore(store));
    }

    public LayerInfo getLayer(String id) {
        return checkAccess(user(), delegate.getLayer(id));
    }

    public LayerInfo getLayerByName(String name) {
        return checkAccess(user(), delegate.getLayerByName(name));
    }
    
    public LayerInfo getLayerByName(Name name) {
        return checkAccess(user(), delegate.getLayerByName(name));
    }

    public LayerGroupInfo getLayerGroup(String id) {
        return checkAccess(user(), delegate.getLayerGroup(id));
    }

    public LayerGroupInfo getLayerGroupByName(String name) {
        return checkAccess(user(), delegate.getLayerGroupByName(name));
    }

    public List<LayerGroupInfo> getLayerGroups() {
        return filterGroups(user(), delegate.getLayerGroups());
    }

    public List<LayerInfo> getLayers() {
        return filterLayers(user(), delegate.getLayers());
    }

    public List<LayerInfo> getLayers(ResourceInfo resource) {
        return filterLayers(user(), delegate.getLayers(unwrap(resource)));
    }
    
    public List<LayerInfo> getLayers(StyleInfo style) {
        return filterLayers(user(), delegate.getLayers(style));
    }

    public NamespaceInfo getNamespace(String id) {
        return checkAccess(user(), delegate.getNamespace(id));
    }

    public NamespaceInfo getNamespaceByPrefix(String prefix) {
        return checkAccess(user(), delegate.getNamespaceByPrefix(prefix));
    }

    public NamespaceInfo getNamespaceByURI(String uri) {
        return checkAccess(user(), delegate.getNamespaceByURI(uri));
    }

    public List<NamespaceInfo> getNamespaces() {
        return filterNamespaces(user(), delegate.getNamespaces());
    }

    public <T extends ResourceInfo> T getResource(String id, Class<T> clazz) {
        return checkAccess(user(), delegate.getResource(id, clazz));
    }

    public <T extends ResourceInfo> T getResourceByName(Name name, Class<T> clazz) {
        return checkAccess(user(), delegate.getResourceByName(name, clazz));
    }
    
    public <T extends ResourceInfo> T getResourceByName(String name, Class<T> clazz) {
        return checkAccess(user(), delegate.getResourceByName(name, clazz));
    }

    public <T extends ResourceInfo> T getResourceByName(NamespaceInfo ns,
            String name, Class<T> clazz) {
        return checkAccess(user(), delegate.getResourceByName(ns, name, clazz)) ;
    }

    public <T extends ResourceInfo> T getResourceByName(String ns, String name, Class<T> clazz) {
        return checkAccess(user(), delegate.getResourceByName(ns, name, clazz));
    }

    public <T extends ResourceInfo> List<T> getResources(Class<T> clazz) {
        return filterResources(user(), delegate.getResources(clazz));
    }

    public <T extends ResourceInfo> List<T> getResourcesByNamespace(NamespaceInfo namespace,
            Class<T> clazz) {
        return filterResources(user(), delegate.getResourcesByNamespace(namespace, clazz));
    }

    public <T extends ResourceInfo> List<T> getResourcesByNamespace(
            String namespace, Class<T> clazz) {
        return filterResources(user(), delegate.getResourcesByNamespace(namespace, clazz));
    }
    
    public <T extends ResourceInfo> T getResourceByStore(StoreInfo store,
            String name, Class<T> clazz) {
        return checkAccess(user(), delegate.getResourceByStore(store, name, clazz));
    }
    
    public <T extends ResourceInfo> List<T> getResourcesByStore(
            StoreInfo store, Class<T> clazz) {
        return filterResources(user(), delegate.getResourcesByStore(store, clazz));
    }

    public <T extends StoreInfo> T getStore(String id, Class<T> clazz) {
        return checkAccess(user(), delegate.getStore(id, clazz));
    }

    public <T extends StoreInfo> T getStoreByName(String name, Class<T> clazz) {
        return checkAccess(user(), delegate.getStoreByName(name, clazz));
    }

    public <T extends StoreInfo> T getStoreByName(String workspaceName,
            String name, Class<T> clazz) {
        return checkAccess(user(), delegate.getStoreByName(workspaceName, name, clazz));
    }
    
    public <T extends StoreInfo> T getStoreByName(WorkspaceInfo workspace,
            String name, Class<T> clazz) {
        return checkAccess(user(), delegate.getStoreByName(workspace, name, clazz));
    }

    public <T extends StoreInfo> List<T> getStores(Class<T> clazz) {
        return filterStores(user(), delegate.getStores(clazz));
    }

    public <T extends StoreInfo> List<T> getStoresByWorkspace(
            String workspaceName, Class<T> clazz) {
        return filterStores(user(), delegate.getStoresByWorkspace(workspaceName , clazz));
    }

    public <T extends StoreInfo> List<T> getStoresByWorkspace(WorkspaceInfo workspace,
            Class<T> clazz) {
        return filterStores(user(), delegate.getStoresByWorkspace(workspace, clazz));
    }

    public WorkspaceInfo getWorkspace(String id) {
        return checkAccess(user(), delegate.getWorkspace(id));
    }

    public WorkspaceInfo getWorkspaceByName(String name) {
        return checkAccess(user(), delegate.getWorkspaceByName(name));
    }

    public List<WorkspaceInfo> getWorkspaces() {
        return filterWorkspaces(user(), delegate.getWorkspaces());
    }

    // -------------------------------------------------------------------
    // Security support method
    // -------------------------------------------------------------------

    protected static Authentication user() {
        return SecurityContextHolder.getContext().getAuthentication();
    }

    /**
     * Given a {@link FeatureTypeInfo} and a user, returns it back if the user
     * can access it in write mode, makes it read only if the user can access it
     * in read only mode, returns null otherwise
     * @return
     */
    protected <T extends ResourceInfo> T checkAccess(Authentication user,
            T info) {
        // handle null case
        if (info == null)
            return null;
        
        // first off, handle the case where the user cannot even read the data
        boolean canRead = accessManager.canAccess(user, info, AccessMode.READ);
        boolean canWrite = accessManager.canAccess(user, info, AccessMode.WRITE);
        WrapperPolicy policy = checkWrapperPolicy(user, canRead, canWrite, info.getName());
        
        // handle the modes that do not require wrapping
        if(policy == WrapperPolicy.HIDE)
            return null;
        else if(policy.level == AccessLevel.READ_WRITE || 
                (policy.level == AccessLevel.READ_ONLY && info instanceof CoverageInfo))
            return info;
        
        // otherwise we are in a mixed case where the user can read but not write, or
        // cannot read but is allowed by the operation mode to access the metadata
        if(info instanceof FeatureTypeInfo) { 
            return (T) new SecuredFeatureTypeInfo((FeatureTypeInfo) info, policy);
        } else if(info instanceof CoverageInfo) {
            return (T) new SecuredCoverageInfo((CoverageInfo) info, policy);
        } else {
            throw new RuntimeException("Unknown resource type " + info.getClass());
        }
   }

    /**
     * Given a store and a user, returns it back if the user can access its
     * workspace in read mode, null otherwise
     * @return
     */
    protected <T extends StoreInfo> T checkAccess(Authentication user, T store) {
        if (store == null)
            return null;
        
        // first off, handle the case where the user cannot even read the data
        boolean canRead = accessManager.canAccess(user, store.getWorkspace(), AccessMode.READ);
        boolean canWrite = accessManager.canAccess(user, store.getWorkspace(), AccessMode.WRITE);
        WrapperPolicy policy = checkWrapperPolicy(user, canRead, canWrite, store.getName());
        
        // handle the modes that do not require wrapping
        if(policy == WrapperPolicy.HIDE)
            return null;
        else if(policy.level == AccessLevel.READ_WRITE || 
                (policy.level == AccessLevel.READ_ONLY && store instanceof CoverageStoreInfo))
            return store;

        // otherwise we are in a mixed case where the user can read but not write, or
        // cannot read but is allowed by the operation mode to access the metadata
        if(store instanceof DataStoreInfo) { 
            return (T) new SecuredDataStoreInfo((DataStoreInfo) store, policy);
        } else if(store instanceof CoverageStoreInfo) {
            return (T) new SecuredCoverageStoreInfo((CoverageStoreInfo) store, policy);
        } else {
            throw new RuntimeException("Unknown store type " + store.getClass());
        }
    }

    /**
     * Given a layer and a user, returns it back if the user can access it, null
     * otherwise
     * @return
     */
    protected LayerInfo checkAccess(Authentication user, LayerInfo layer) {
        if (layer == null)
            return null;
        
        // first off, handle the case where the user cannot even read the data
        boolean canRead = accessManager.canAccess(user, layer, AccessMode.READ);
        boolean canWrite = accessManager.canAccess(user, layer, AccessMode.WRITE);
        WrapperPolicy policy = checkWrapperPolicy(user, canRead, canWrite, layer.getName());
        
        // handle the modes that do not require wrapping
        if(policy == WrapperPolicy.HIDE)
            return null;
        else if(policy.level == AccessLevel.READ_WRITE)
            return layer;

        // otherwise we are in a mixed case where the user can read but not write, or
        // cannot read but is allowed by the operation mode to access the metadata
        return new SecuredLayerInfo(layer, policy);
    }

    /**
     * Given a layer group and a user, returns it back if the user can access
     * it, null otherwise
     * @return
     */
    protected LayerGroupInfo checkAccess(Authentication user, LayerGroupInfo group) {
        if (group == null)
            return null;

        // scan thru the layers, if any cannot be accessed, we hide the group, otherwise
        // we return the group back, eventually wrapping the read only layers
        final List<LayerInfo> layers = group.getLayers();
        ArrayList<LayerInfo> wrapped = new ArrayList<LayerInfo>(layers.size());
        boolean needsWrapping = false;
        for (LayerInfo layer : layers) {
            LayerInfo checked = checkAccess(user, layer);
            if(checked == null)
                return null;
            else if(checked != null && checked != layer) 
                needsWrapping = true;
            wrapped.add(checked);
        }
        
        if(needsWrapping)
            return new SecuredLayerGroupInfo(group, wrapped);
        else
            return group;
    }

    /**
     * Given a namespace and user, returns it back if the user can access it,
     * null otherwise
     * @return
     */
    protected <T extends NamespaceInfo> T checkAccess(Authentication user, T ns) {
        if(ns == null)
            return null;
        
        // route the security check thru the associated workspace info
        WorkspaceInfo ws = delegate.getWorkspaceByName(ns.getPrefix());
        if(ws == null) {
            // temporary workaround, build a fake workspace, as we're probably
            // in between a change of workspace/namespace name
            ws = delegate.getFactory().createWorkspace();
            ws.setName(ns.getPrefix());
        }
        WorkspaceInfo info = checkAccess(user, ws);
        if (info == null)
            return null;
        else
            return ns;
    }

    /**
     * Given a workspace and user, returns it back if the user can access it,
     * null otherwise
     * @return
     */
    protected <T extends WorkspaceInfo> T checkAccess(Authentication user, T ws) {
        if (ws == null)
            return null;
        
        // first off, handle the case where the user cannot even read the data
        boolean canRead = accessManager.canAccess(user, ws, AccessMode.READ);
        boolean canWrite = accessManager.canAccess(user, ws, AccessMode.WRITE);
        WrapperPolicy policy = checkWrapperPolicy(user, canRead, canWrite, ws.getName());
        
        // if we don't need to hide it, then we can return it as is since it
        // can only provide metadata.
        if(policy == WrapperPolicy.HIDE)
            return null;
        else 
            return ws;
    }
    
    /**
     * Factors out the policy that decides what access level the current user
     * has to a specific resource considering the read/write access, the security
     * mode, and the filtering status
     * @param user
     * @param canRead
     * @param canWrite
     * @param resourceName
     * @return
     */
    WrapperPolicy checkWrapperPolicy(Authentication user,
            boolean canRead, boolean canWrite, String resourceName) {
        if (!canRead) {
            // if in hide mode, we just hide the resource
            if (accessManager.getMode() == CatalogMode.HIDE) {
                return WrapperPolicy.HIDE;
            } else if (accessManager.getMode() == CatalogMode.MIXED) {
                // if request is a get capabilities and mixed, we hide again
                Request request = Dispatcher.REQUEST.get();
                if(request != null && "GetCapabilities".equalsIgnoreCase(request.getRequest()))
                    return WrapperPolicy.HIDE;
                // otherwise challenge the user for credentials
                else
                    throw unauthorizedAccess(resourceName);
            } else {
                // for challenge mode we agree to show freely only the metadata, every
                // other access will trigger a security exception
                return WrapperPolicy.METADATA;
            }
        } else if (!canWrite) {
            if (accessManager.getMode() == CatalogMode.HIDE) {
                return WrapperPolicy.RO_HIDE;
            } else {
                return WrapperPolicy.RO_CHALLENGE;
            }
        }
        return WrapperPolicy.RW;
    }

    public static AcegiSecurityException unauthorizedAccess(String resourceName) {
        // not hide, and not filtering out a list, this
        // is an unauthorized direct resource access, complain
        Authentication user = user();
        if (user == null || user.getAuthorities().length == 0)
            return new InsufficientAuthenticationException("Cannot access "
                    + resourceName + " as anonymous");
        else
            return new AccessDeniedException("Cannot access "
                    + resourceName + " with the current privileges");
    }
    
    public static AcegiSecurityException unauthorizedAccess() {
        // not hide, and not filtering out a list, this
        // is an unauthorized direct resource access, complain
        Authentication user = user();
        if (user == null || user.getAuthorities().length == 0)
            return new InsufficientAuthenticationException("Operation unallowed with the current privileges");
        else
            return new AccessDeniedException("Operation unallowed with the current privileges");
    }

    /**
     * Given a list of resources, returns a copy of it containing only the
     * resources the user can access
     * 
     * @param user
     * @param resources
     * 
     * @return
     */
    protected <T extends ResourceInfo> List<T> filterResources(Authentication user,
            List<T> resources) {
        List<T> result = new ArrayList<T>();
        for (T original : resources) {
            T secured = checkAccess(user, original);
            if (secured != null)
                result.add(secured);
        }
        return result;
    }

    /**
     * Given a list of stores, returns a copy of it containing only the
     * resources the user can access
     * 
     * @param user
     * @param resources
     * 
     * @return
     */
    protected <T extends StoreInfo> List<T> filterStores(Authentication user, List<T> resources) {
        List<T> result = new ArrayList<T>();
        for (T original : resources) {
            T secured = checkAccess(user, original);
            if (secured != null)
                result.add(secured);
        }
        return result;
    }

    /**
     * Given a list of layer groups, returns a copy of it containing only the
     * groups the user can access
     * 
     * @param user
     * @param groups
     * 
     * @return
     */
    protected List<LayerGroupInfo> filterGroups(Authentication user, List<LayerGroupInfo> groups) {
        List<LayerGroupInfo> result = new ArrayList<LayerGroupInfo>();
        for (LayerGroupInfo original : groups) {
            LayerGroupInfo secured = checkAccess(user, original);
            if (secured != null)
                result.add(secured);
        }
        return result;
    }

    /**
     * Given a list of layers, returns a copy of it containing only the layers
     * the user can access
     * 
     * @param user
     * @param layers
     * 
     * @return
     */
    protected List<LayerInfo> filterLayers(Authentication user, List<LayerInfo> layers) {
        List<LayerInfo> result = new ArrayList<LayerInfo>();
        for (LayerInfo original : layers) {
            LayerInfo secured = checkAccess(user, original);
            if (secured != null)
                result.add(secured);
        }
        return result;
    }

    /**
     * Given a list of namespaces, returns a copy of it containing only the
     * namespaces the user can access
     * 
     * @param user
     * @param namespaces
     * 
     * @return
     */
    protected <T extends NamespaceInfo> List<T> filterNamespaces(Authentication user,
            List<T> namespaces) {
        List<T> result = new ArrayList<T>();
        for (T original : namespaces) {
            T secured = checkAccess(user, original);
            if (secured != null)
                result.add(secured);
        }
        return result;
    }

    /**
     * Given a list of workspaces, returns a copy of it containing only the
     * workspaces the user can access
     * 
     * @param user
     * @param namespaces
     * 
     * @return
     */
    protected <T extends WorkspaceInfo> List<T> filterWorkspaces(Authentication user,
            List<T> workspaces) {
        List<T> result = new ArrayList<T>();
        for (T original : workspaces) {
            T secured = checkAccess(user, original);
            if (secured != null)
                result.add(secured);
        }
        return result;
    }
    
    // -------------------------------------------------------------------
    // Unwrappers, used to make sure the lower level does not get hit by
    // read only wrappers
    // -------------------------------------------------------------------
    static LayerGroupInfo unwrap(LayerGroupInfo layerGroup) {
        if(layerGroup instanceof SecuredLayerGroupInfo)
            return ((SecuredLayerGroupInfo) layerGroup).unwrap(LayerGroupInfo.class);
        return layerGroup;
    }
    
    static LayerInfo unwrap(LayerInfo layer) {
        if(layer instanceof SecuredLayerInfo)
            return ((SecuredLayerInfo) layer).unwrap(LayerInfo.class);
        return layer;
    }
    
    static ResourceInfo unwrap(ResourceInfo info) {
        if(info instanceof SecuredFeatureTypeInfo)
            return ((SecuredFeatureTypeInfo) info).unwrap(ResourceInfo.class);
        return info;
    }
    
    static StoreInfo unwrap(StoreInfo info) {
        if(info instanceof SecuredDataStoreInfo)
            return ((SecuredDataStoreInfo) info).unwrap(StoreInfo.class);
        return info;
    }

    public static Object unwrap( Object obj ) {
        if ( obj instanceof LayerGroupInfo ) {
            return unwrap((LayerGroupInfo)obj);
        }
        if ( obj instanceof LayerInfo ) {
            return unwrap((LayerInfo)obj);
        }
        if ( obj instanceof ResourceInfo ) {
            return unwrap((ResourceInfo)obj);
        }
        if ( obj instanceof StoreInfo ) {
            return unwrap((StoreInfo)obj);
        }
        if ( obj instanceof SecureCatalogImpl ) {
            return ((SecureCatalogImpl)obj).delegate;
        }
        
        return obj;
    }
    // -------------------------------------------------------------------
    // PURE DELEGATING METHODS
    // (MapInfo being here since its role in the grand scheme of things
    // is still undefined)
    // -------------------------------------------------------------------

    public MapInfo getMap(String id) {
        return delegate.getMap(id);
    }

    public MapInfo getMapByName(String name) {
        return delegate.getMapByName(name);
    }

    public List<MapInfo> getMaps() {
        return delegate.getMaps();
    }

    public void add(LayerGroupInfo layerGroup) {
        delegate.add(unwrap(layerGroup));
    }

    

    public void add(LayerInfo layer) {
        delegate.add(unwrap(layer));
    }

    public void add(MapInfo map) {
        delegate.add(map);
    }

    public void add(NamespaceInfo namespace) {
        delegate.add(namespace);
    }

    public void add(ResourceInfo resource) {
        delegate.add(unwrap(resource));
    }

    public void add(StoreInfo store) {
        delegate.add(unwrap(store));
    }

    public void add(StyleInfo style) {
        delegate.add(style);
    }

    public void add(WorkspaceInfo workspace) {
        delegate.add(workspace);
    }

    public void addListener(CatalogListener listener) {
        delegate.addListener(listener);
    }

    public void dispose() {
        delegate.dispose();
    }

    public CatalogDAO getDAO() {
        return delegate.getDAO();
    }
    
    public CatalogFactory getFactory() {
        return delegate.getFactory();
    }

    public Collection<CatalogListener> getListeners() {
        return delegate.getListeners();
    }

    public void fireAdded(CatalogInfo object) {
        delegate.fireAdded(object);
    }
    
    public void fireModified(CatalogInfo object, List<String> propertyNames, List oldValues,
            List newValues) {
        delegate.fireModified(object, propertyNames, oldValues, newValues);
    }
    
    public void firePostModified(CatalogInfo object) {
        delegate.firePostModified(object);
    }
    
    public void fireRemoved(CatalogInfo object) {
        delegate.fireRemoved(object);
    }
    
    // TODO: why is resource pool being exposed???
    public ResourcePool getResourcePool() {
        return delegate.getResourcePool();
    }

    public StyleInfo getStyle(String id) {
        return delegate.getStyle(id);
    }

    public StyleInfo getStyleByName(String name) {
        return delegate.getStyleByName(name);
    }

    public List<StyleInfo> getStyles() {
        return delegate.getStyles();
    }

    public void remove(LayerGroupInfo layerGroup) {
        delegate.remove(unwrap(layerGroup));
    }

    public void remove(LayerInfo layer) {
        delegate.remove(unwrap(layer));
    }

    public void remove(MapInfo map) {
        delegate.remove(map);
    }

    public void remove(NamespaceInfo namespace) {
        delegate.remove(namespace);
    }

    public void remove(ResourceInfo resource) {
        delegate.remove(unwrap(resource));
    }

    public void remove(StoreInfo store) {
        delegate.remove(unwrap(store));
    }

    public void remove(StyleInfo style) {
        delegate.remove(style);
    }

    public void remove(WorkspaceInfo workspace) {
        delegate.remove(workspace);
    }

    public void removeListener(CatalogListener listener) {
        delegate.removeListener(listener);
    }

    public void save(LayerGroupInfo layerGroup) {
        delegate.save(unwrap(layerGroup));
    }

    public void save(LayerInfo layer) {
        delegate.save(unwrap(layer));
    }

    public void save(MapInfo map) {
        delegate.save(map);
    }

    public void save(NamespaceInfo namespace) {
        delegate.save(namespace);
    }

    public void save(ResourceInfo resource) {
        delegate.save(unwrap(resource));
    }

    public void save(StoreInfo store) {
        delegate.save(unwrap(store));
    }

    public void save(StyleInfo style) {
        delegate.save(style);
    }

    public void save(WorkspaceInfo workspace) {
        delegate.save(workspace);
    }

    public void setDefaultNamespace(NamespaceInfo defaultNamespace) {
        delegate.setDefaultNamespace(defaultNamespace);
    }

    public void setDefaultWorkspace(WorkspaceInfo workspace) {
        delegate.setDefaultWorkspace(workspace);
    }

    public void setResourcePool(ResourcePool resourcePool) {
        delegate.setResourcePool(resourcePool);
    }
    
    public GeoServerResourceLoader getResourceLoader() {
        return delegate.getResourceLoader();
    }
    
    public void setResourceLoader(GeoServerResourceLoader resourceLoader) {
        delegate.setResourceLoader(resourceLoader);
    }
    
    public void accept(CatalogVisitor visitor) {
        delegate.accept(visitor);
    }

    public DataStoreInfo getDefaultDataStore(WorkspaceInfo workspace) {
        return checkAccess(user(), delegate.getDefaultDataStore(workspace));
    }

    public void setDefaultDataStore(WorkspaceInfo workspace, DataStoreInfo defaultStore) {
        delegate.setDefaultDataStore(workspace, defaultStore);
    }

}
