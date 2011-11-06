/* Copyright (c) 2001 - 2007 TOPP - www.openplans.org. All rights reserved.
 * This code is licensed under the GPL 2.0 license, availible at the root
 * application directory.
 */
package org.geoserver.security.impl;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.logging.Logger;

import org.geoserver.platform.GeoServerExtensions;
import org.geoserver.security.GeoServerSecurityManager;
import org.geoserver.security.GeoserverUserGroupService;
import org.geoserver.security.GeoserverUserGroupStore;
import org.geoserver.security.config.SecurityNamedServiceConfig;
import org.geoserver.security.event.UserGroupLoadedListener;
import org.geoserver.security.password.GeoserverUserPasswordEncoder;
import org.geoserver.security.password.PasswordValidationException;
import org.springframework.dao.DataAccessException;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

/**
 * A base implementation for {@link GeoserverUserGroupStore}
 * 
 * @author christian
 *
 */
public abstract class AbstractUserGroupStore  implements GeoserverUserGroupStore{

    /** logger */
    static Logger LOGGER = org.geotools.util.logging.Logging.getLogger("org.geoserver.security");
    
    private boolean modified=false;
    protected AbstractUserGroupService service;

    protected UserGroupStoreHelper helper;
    
    protected AbstractUserGroupStore() {
        helper=new UserGroupStoreHelper();
    }

    
    public String getName() {
        return service.getName();
    }

    public void setName(String name) {
        service.setName(name);
    }


    public GeoServerSecurityManager getSecurityManager() {
        return service.getSecurityManager();
    }

    public void setSecurityManager(GeoServerSecurityManager securityManager) {
        service.setSecurityManager(securityManager);
    }

    public boolean canCreateStore() {
        return service.canCreateStore();
    }

    public String getPasswordEncoderName() {
        return service.getPasswordEncoderName();
    }

    public String getPasswordValidatorName() {
        return service.getPasswordValidatorName();
    }

    public GeoserverUserGroupStore createStore() throws IOException {
        return service.createStore();
    }

    public void registerUserGroupLoadedListener(UserGroupLoadedListener listener) {
        service.registerUserGroupLoadedListener(listener);
    }


    public void unregisterUserGroupLoadedListener(UserGroupLoadedListener listener) {
        service.unregisterUserGroupLoadedListener(listener);
    }

    public GeoserverUser getUserByUsername(String username) throws IOException {
        return  helper.getUserByUsername(username);
    }

    public GeoserverUserGroup getGroupByGroupname(String groupname) throws IOException {
        return helper.getGroupByGroupname(groupname);
    }

    public SortedSet<GeoserverUser> getUsers() throws IOException {
        return helper.getUsers();
    }

    public SortedSet<GeoserverUserGroup> getUserGroups() throws IOException {
        return helper.getUserGroups();

    }

    public GeoserverUserGroup createGroupObject(String groupname, boolean isEnabled)
            throws IOException {
        return service.createGroupObject(groupname, isEnabled);
    }

    public SortedSet<GeoserverUserGroup> getGroupsForUser(GeoserverUser user) throws IOException {
        return helper.getGroupsForUser(user);
    }

    public SortedSet<GeoserverUser> getUsersForGroup(GeoserverUserGroup group) throws IOException {
        return helper.getUsersForGroup(group);
    }

    public void load() throws IOException {
        deserialize();
    }

    public File getConfigRoot() throws IOException {
        return service.getConfigRoot();
    }

    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException,
            DataAccessException {        
        // this is only need at runtime
        return service.loadUserByUsername(username);
    }


    
    /* (non-Javadoc)
     * @see org.geoserver.security.GeoserverUserGroupStore#isModified()
     */
    public boolean isModified() {
        return modified;
    }

    /**
     * Setter for modified flag
     * @param value
     */
    public void setModified(Boolean value) {
        modified=value;
    }
    
    /**
     * validates and encodes the password. Do nothing
     * for a not changed password of an existing user
     * 
     * @param user
     * @throws IOException
     * @throws PasswordValidationException
     */
    protected void preparePassword(GeoserverUser user) throws IOException,PasswordValidationException {
        
        GeoserverUserPasswordEncoder enc  = (GeoserverUserPasswordEncoder) 
                GeoServerExtensions.bean(getPasswordEncoderName());
        
        if (enc.isResponsibleForEncoding(user.getPassword()))
            return; // do nothing, already encoded
            
        // we have a plain text password
        // validate it
        getSecurityManager().loadPasswordValidator(getPasswordValidatorName()).
        validatePassword(user.getPassword());    

        // validation ok, initializer encoder and set encoded password
        enc.initializeFor(this);
        user.setPassword(enc.encodePassword(user.getPassword(), null));
        
    }
    
    /* (non-Javadoc)
     * @see org.geoserver.security.GeoserverUserGroupStore#addUser(org.geoserver.security.impl.GeoserverUser)
     */
    public void addUser(GeoserverUser user) throws IOException,PasswordValidationException{
        
        if(helper.userMap.containsKey(user.getUsername()))
            throw new IllegalArgumentException("The user " + user.getUsername() + " already exists");
        
        preparePassword(user);
        helper.userMap.put(user.getUsername(), user);
        setModified(true);
    }
    
    /* (non-Javadoc)
     * @see org.geoserver.security.GeoserverUserGroupStore#addGroup(org.geoserver.security.impl.GeoserverUserGroup)
     */
    public void addGroup(GeoserverUserGroup group) throws IOException{
                
        if(helper.groupMap.containsKey(group.getGroupname()))
            throw new IllegalArgumentException("The group " + group.getGroupname() + " already exists");
        else {
            helper.groupMap.put(group.getGroupname(), group);
            setModified(true);
        }
    }

    
    /* (non-Javadoc)
     * @see org.geoserver.security.GeoserverUserGroupStore#updateUser(org.geoserver.security.impl.GeoserverUser)
     */
    public void updateUser(GeoserverUser user) throws IOException,PasswordValidationException{
        
       if(helper.userMap.containsKey(user.getUsername())==false) {
            throw new IllegalArgumentException("The user " + user.getUsername() + " does not exist");
       }
       preparePassword(user);     
       helper.userMap.put(user.getUsername(), user);
       setModified(true);
    }
    
    /* (non-Javadoc)
     * @see org.geoserver.security.GeoserverUserGroupStore#updateGroup(org.geoserver.security.impl.GeoserverUserGroup)
     */
    public void updateGroup(GeoserverUserGroup group) throws IOException{
        
        if(helper.groupMap.containsKey(group.getGroupname())) {
            helper.groupMap.put(group.getGroupname(), group);
            setModified(true);
        }
        else
            throw new IllegalArgumentException("The group " + group.getGroupname() + " does not exist");
    }

    
    /* (non-Javadoc)
     * @see org.geoserver.security.GeoserverUserGroupStore#removeUser(org.geoserver.security.impl.GeoserverUser)
     */
    public boolean removeUser(GeoserverUser user) throws IOException{
        
        Collection<GeoserverUserGroup> groups = helper.user_groupMap.get(user);        
        if (groups!=null) {
            Collection<GeoserverUserGroup> toBeRemoved = new ArrayList<GeoserverUserGroup>();
            toBeRemoved.addAll(groups);
            for (GeoserverUserGroup group : toBeRemoved) {
                disAssociateUserFromGroup(user, group);
            }
        }
        
        boolean retValue = helper.userMap.remove(user.getUsername()) != null;
        if (retValue)
            setModified(true);
        return retValue;
    }
    
    /* (non-Javadoc)
     * @see org.geoserver.security.GeoserverUserGroupStore#removeGroup(org.geoserver.security.impl.GeoserverUserGroup)
     */
    public boolean removeGroup(GeoserverUserGroup group) throws IOException{
        Collection<GeoserverUser> users = helper.group_userMap.get(group);;
        if (users !=null) {
            Collection<GeoserverUser> toBeRemoved = new ArrayList<GeoserverUser>();
            toBeRemoved.addAll(users);
            for (GeoserverUser user : toBeRemoved) {
                disAssociateUserFromGroup(user, group);
            }
        }
        
        boolean retval = helper.groupMap.remove(group.getGroupname()) != null;
        if (retval) { 
            setModified(true);
        }    
        return retval;
    }


    /* (non-Javadoc)
     * @see org.geoserver.security.GeoserverUserGroupStore#store()
     *  
     */
    public void store() throws IOException {
        if (isModified()) {
            LOGGER.info("Start storing user/grous for service named "+getName());
            // prevent concurrent write from store and
            // read from service
            synchronized (service) { 
                serialize();
            }
            setModified(false);
            LOGGER.info("Storing user/grous successful for service named "+getName());
            service.load(); // service must reload
        }  else {
            LOGGER.info("Storing unnecessary, no change for user and groups");
        }
    }

    /* (non-Javadoc)
     * @see org.geoserver.security.GeoserverUserGroupStore#associateUserToGroup(org.geoserver.security.impl.GeoserverUser, org.geoserver.security.impl.GeoserverUserGroup)
     */
    public void associateUserToGroup(GeoserverUser user, GeoserverUserGroup group) throws IOException{
        checkUser(user);
        checkGroup(group);
        
        boolean changed = false;
        
        
        SortedSet<GeoserverUser> users = helper.group_userMap.get(group);
        if (users == null) {
            users = new TreeSet<GeoserverUser>();
            helper.group_userMap.put(group,users);
        }
        if (users.contains(user)==false) {
            users.add(user);
            changed=true;
        }
        
        SortedSet<GeoserverUserGroup> groups = helper.user_groupMap.get(user);
        if (groups == null) {
            groups = new TreeSet<GeoserverUserGroup>();
            helper.user_groupMap.put(user,groups);
        }
        if (groups.contains(group)==false) {            
            groups.add(group);
            changed=true;            
        }
        if (changed) {
            setModified(true);
        }
    }
    
    /* (non-Javadoc)
     * @see org.geoserver.security.GeoserverUserDetailsService#disAssociateUserFromGroup(org.geoserver.security.impl.GeoserverUser, org.geoserver.security.UserGroup)
     */
    public void disAssociateUserFromGroup(GeoserverUser user, GeoserverUserGroup group) throws IOException{
        checkUser(user);
        checkGroup(group);
        boolean changed = false;
        
        SortedSet<GeoserverUser> users = helper.group_userMap.get(group);        
        if (users!=null) {
            changed |=users.remove(user);
            if (users.isEmpty()) {
                helper.group_userMap.remove(group);
            }                
        }
        SortedSet<GeoserverUserGroup> groups = helper.user_groupMap.get(user);
        if (groups!=null) {
            changed |= groups.remove(group);
            if (groups.isEmpty())
                helper.user_groupMap.remove(user);
        }
        if (changed) {
            setModified(true);
        }
    }
    
    /**
     * Subclasses must implement this method 
     * Save user/groups  to backend
     */
    protected abstract void serialize() throws IOException;

    /* (non-Javadoc)
     * @see org.geoserver.security.GeoserverUserGroupStore#clear()
     */
    public void clear() throws IOException {
        clearMaps();
        setModified(true);
    }

    @Override
    public void initializeFromService(GeoserverUserGroupService service)
            throws IOException {
        this.service=(AbstractUserGroupService)service;        
        load();
    }

    /**
     * internal use, clear the maps
     */
    protected void clearMaps() {
        helper.clearMaps();
    }

    /**
     * Make a deep copy (using serialization) from the
     * service to the store.
    */    
    @SuppressWarnings("unchecked")
    protected void deserialize() throws IOException {
        // deepcopy from service, using serialization 
        
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ObjectOutputStream oout = new ObjectOutputStream(out);
        oout.writeObject(service.helper.userMap);
        oout.writeObject(service.helper.groupMap);
        oout.writeObject(service.helper.user_groupMap);
        oout.writeObject(service.helper.group_userMap);
        byte[] bytes =out.toByteArray();
        oout.close();            

        clearMaps();
        ByteArrayInputStream in = new ByteArrayInputStream(bytes);
        ObjectInputStream oin = new ObjectInputStream(in);
        try {
            helper.userMap = (TreeMap<String,GeoserverUser>) oin.readObject();
            helper.groupMap =(TreeMap<String,GeoserverUserGroup>) oin.readObject();
            helper.user_groupMap = (TreeMap<GeoserverUser,SortedSet<GeoserverUserGroup>>)oin.readObject();
            helper.group_userMap = (TreeMap<GeoserverUserGroup,SortedSet<GeoserverUser>>)oin.readObject();
        } catch (ClassNotFoundException e) {
            throw new IOException(e);
        }
        setModified(false);
    }
    
    /* (non-Javadoc)
     * @see org.geoserver.security.GeoserverUserGroupService#initializeFromConfig(org.geoserver.security.config.SecurityNamedServiceConfig)
     */
    @Override
    public void initializeFromConfig(SecurityNamedServiceConfig config) throws IOException {
        service.initializeFromConfig(config);
    }
    
    /** 
     * Delegates to the {@link GeoserverUserGroupService} backend
     */
    @Override
    public GeoserverUser createUserObject(String username,String password, boolean isEnabled) throws IOException{        
        return service.createUserObject(username, password, isEnabled);
     }

    protected void checkUser(GeoserverUser user) throws IOException{
        if (helper.userMap.containsKey(user.getUsername())==false)
            throw new IOException("User: " +  user.getUsername()+ " does not exist");
    }
    
    protected void checkGroup(GeoserverUserGroup group) throws IOException{
        if (helper.groupMap.containsKey(group.getGroupname())==false)
            throw new IOException("Group: " +  group.getGroupname()+ " does not exist");
    }

}