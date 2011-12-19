/* Copyright (c) 2001 - 2008 TOPP - www.openplans.org. All rights reserved.
 * This code is licensed under the GPL 2.0 license, available at the root
 * application directory.
 */

package org.geoserver.security.validation;

import java.io.IOException;
import java.util.Map;
import java.util.Properties;
import java.util.SortedSet;

import org.geoserver.security.GeoServerSecurityManager;
import org.geoserver.security.GeoserverRoleService;
import org.geoserver.security.GeoserverRoleStore;
import org.geoserver.security.GeoserverUserGroupService;
import org.geoserver.security.config.SecurityNamedServiceConfig;
import org.geoserver.security.event.RoleLoadedListener;
import org.geoserver.security.impl.GeoserverRole;



/**
 * 
 * This class is a validation wrapper for {@link GeoserverRoleService}
 * 
 * Usage:
 * <code>
 * GeoserverRoleService valService = new RoleServiceValidationWrapper(service);
 * valService.getRoles();
 * </code>
 * 
 * Since the {@link GeoserverRoleService} interface does not allow to 
 * throw {@link RoleServiceException} objects directly, these objects
 * a wrapped into an IOException. Use {@link IOException#getCause()} to
 * get the proper exception.
 * 
 * 
 * @author christian
 *
 */
public class RoleServiceValidationWrapper extends AbstractSecurityValidator implements GeoserverRoleService{

    protected GeoserverRoleService service;
    protected GeoserverUserGroupService[] services;
    
    /**
     * Creates a wrapper object. Optionally, {@link GeoserverUserGroupService} objects
     * can be passed if validation of user names and group names is required
     * 
     * @param service
     * @param services
     */    
    public RoleServiceValidationWrapper(GeoserverRoleService service, GeoserverUserGroupService ...services) {
        this.service=service;
        this.services=services;
    }


    public GeoserverRoleService getWrappedService() {
        return service;
    }
    
    /**
     * Checks if a user name is valid
     * if this validator was constructed with {@link GeoserverUserGroupService}
     * objects, a cross check is done 
     * 
     * @param userName
     * @throws RoleServiceException
     */
    protected void checkValidUserName(String userName) throws IOException{
        if (isNotEmpty(userName)==false)
            throw createSecurityException(RoleServiceValidationErrors.ROLE_ERR_04);
        
        if (services.length==0) return;
        for (GeoserverUserGroupService service : services) {
            if (service.getUserByUsername(userName)!=null)
                return;
        }
        throw createSecurityException(RoleServiceValidationErrors.ROLE_ERR_06,userName);
    }
    
    /**
     * Checks if a group name is valid
     * if this validator was constructed with {@link GeoserverUserGroupService}
     * objects, a cross check is done 
     * 
     * @param groupName
     * @throws RoleServiceException
     */
    protected void checkValidGroupName(String groupName) throws  IOException{
        if (isNotEmpty(groupName)==false)
            throw createSecurityException(RoleServiceValidationErrors.ROLE_ERR_05);
        
        if (services.length==0) return;
        for (GeoserverUserGroupService service : services) {
            if (service.getGroupByGroupname(groupName)!=null)
                return;
        }
        throw createSecurityException(RoleServiceValidationErrors.ROLE_ERR_07,groupName);
    }

    protected void checkRoleName(String roleName) throws IOException{
        if (isNotEmpty(roleName)==false)
            throw createSecurityException(RoleServiceValidationErrors.ROLE_ERR_01);        
    }
    
    protected void checkExistingRoleName(String roleName) throws IOException{
        checkRoleName(roleName);
        if (service.getRoleByName(roleName)==null)
            throw createSecurityException(RoleServiceValidationErrors.ROLE_ERR_02,roleName);
    }
    
    protected void checkNotExistingRoleName(String roleName) throws IOException{
        checkRoleName(roleName);
        if (service.getRoleByName(roleName)!=null)
            throw createSecurityException(RoleServiceValidationErrors.ROLE_ERR_03,roleName);
    }
    
    // start wrapper methods
    
    public void initializeFromConfig(SecurityNamedServiceConfig config) throws IOException {
        service.initializeFromConfig(config);
    }


    public boolean canCreateStore() {
        return service.canCreateStore();
    }


    public GeoserverRoleStore createStore() throws IOException {
        return service.createStore();
    }


    public String getName() {
        return service.getName();
    }


    public void setName(String name) {
        service.setName(name);
    }


    public void setSecurityManager(GeoServerSecurityManager securityManager) {
        service.setSecurityManager(securityManager);
    }


    public void registerRoleLoadedListener(RoleLoadedListener listener) {
        service.registerRoleLoadedListener(listener);
    }


    public GeoServerSecurityManager getSecurityManager() {
        return service.getSecurityManager();
    }

    public void unregisterRoleLoadedListener(RoleLoadedListener listener) {
        service.unregisterRoleLoadedListener(listener);
    }


    public SortedSet<String> getGroupNamesForRole(GeoserverRole role) throws IOException {
        checkExistingRoleName(role.getAuthority());
        return service.getGroupNamesForRole(role);
    }


    public SortedSet<String> getUserNamesForRole(GeoserverRole role) throws IOException {
        checkExistingRoleName(role.getAuthority());
        return service.getUserNamesForRole(role);
    }


    public SortedSet<GeoserverRole> getRolesForUser(String username) throws IOException {
        checkValidUserName(username);
        return service.getRolesForUser(username);
    }


    public SortedSet<GeoserverRole> getRolesForGroup(String groupname) throws IOException {
        checkValidGroupName(groupname);
        return service.getRolesForGroup(groupname);
    }


    public SortedSet<GeoserverRole> getRoles() throws IOException {
        return service.getRoles();
    }



    public Map<String, String> getParentMappings() throws IOException {
        return service.getParentMappings();
    }


    public GeoserverRole createRoleObject(String role) throws IOException {
        checkRoleName(role);
        return service.createRoleObject(role);
    }


    public GeoserverRole getParentRole(GeoserverRole role) throws IOException {
        checkExistingRoleName(role.getAuthority());
        return service.getParentRole(role);
    }


    public GeoserverRole getRoleByName(String role) throws IOException {
        return service.getRoleByName(role);
    }


    public void load() throws IOException {
        service.load();
    }


    public Properties personalizeRoleParams(String roleName, Properties roleParams,
            String userName, Properties userProps) throws IOException {
        return service.personalizeRoleParams(roleName, roleParams, userName, userProps);
    }


    public GeoserverRole getAdminRole() {
        return service.getAdminRole();
    }


    
    
    @Override
    protected AbstractSecurityValidationErrors getSecurityErrors() {
        return new RoleServiceValidationErrors();
    }


    
    /**
     * Helper method for creating a proper
     * {@link SecurityConfigException} object
     * 
     * @param errorid
     * @param args
     * @return
     */
    protected IOException createSecurityException (String errorid, Object ...args) {
        String message = getSecurityErrors().formatErrorMsg(errorid, args);
        RoleServiceException ex =  new RoleServiceException(errorid,message,args);
        return new IOException("Details are in the nested excetpion",ex);
    }
        
}
