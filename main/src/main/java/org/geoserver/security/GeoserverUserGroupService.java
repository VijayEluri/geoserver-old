/* Copyright (c) 2001 - 2007 TOPP - www.openplans.org. All rights reserved.
 * This code is licensed under the GPL 2.0 license, availible at the root
 * application directory.
 */
package org.geoserver.security;

import java.io.IOException;
import java.util.SortedSet;

import org.geoserver.security.event.UserGroupLoadedEvent;
import org.geoserver.security.event.UserGroupLoadedListener;
import org.geoserver.security.impl.GeoserverUser;
import org.geoserver.security.impl.GeoserverUserGroup;
import org.geoserver.security.password.GeoserverDigestPasswordEncoder;
import org.geoserver.security.password.GeoserverPasswordEncoder;
import org.geoserver.security.password.PasswordValidator;
import org.springframework.dao.DataAccessException;
import org.springframework.security.core.userdetails.UserDetailsService;

/**
 * This interface is an extenstion to {@link UserDetailsService}
 * 
 * A class implementing this interface implements a read only backend for
 * user and group management
 * 
 * @author christian
 *
 */
public interface GeoserverUserGroupService extends GeoServerSecurityService,UserDetailsService {

    /**
     * Creates the user group store that corresponds to this service, or null if creating a store
     * is not supported.
     * <p>
     * Implementations that do not support a store should ensure that {@link #canCreateStore()} 
     * returns <code>false</code>.
     * </p>
     */
    GeoserverUserGroupStore createStore() throws IOException;

    /**
     * Register for notifications on load
     * 
     * @param listener
     */
    void registerUserGroupLoadedListener (UserGroupLoadedListener listener);
    
    /**
     * Unregister for notifications on store/load
     * 
     * @param listener
     */
    void unregisterUserGroupLoadedListener (UserGroupLoadedListener listener);


    /**
     * Returns the the group object, null if not found
     * 
     * @param groupname
     * @return null if group not found
     * @throws DataAccessException
     */
    GeoserverUserGroup getGroupByGroupname(String groupname) throws IOException;
    
    /**
     * Returns the the user object, null if not found
     * 
     * @param username
     * @return null if user not found
     * @throws DataAccessException
     */
    GeoserverUser getUserByUsername(String username) throws IOException;

   

    /**
     * Create a user object. Implementations can use subclasses of {@link GeoserverUser}
     * 
     * @param username
     * @param password
     * @param isEnabled
     * @return
     */
    GeoserverUser createUserObject(String username,String password, boolean isEnabled)  throws IOException;
    
    /**
     * Create a user object. Implementations can use classes implementing  {@link GeoserverUserGroup}
     * 
     * @param groupname
     * @param password
     * @param isEnabled
     * @return
     */
    GeoserverUserGroup createGroupObject(String groupname, boolean isEnabled)  throws IOException;
    
    /**
     * Returns the list of users. 
     * 
     * @return a collection which cannot be modified
     */
    SortedSet<GeoserverUser> getUsers()  throws IOException;
    
    /**
     * Returns the list of GeoserverUserGroups. 
     * 
     * @return a collection which cannot be modified
     */
    SortedSet<GeoserverUserGroup> getUserGroups()  throws IOException;

          
    
    /**
     * get users for a group
     * 
     * @param group
     * @return a collection which cannot be modified
     */
    SortedSet<GeoserverUser> getUsersForGroup (GeoserverUserGroup group)  throws IOException;
    
    /**
     * get the groups for a user, an implementation not 
     * supporting user groups returns an empty collection
     * 
     * @param user
     * @return a collection which cannot be modified
     */
    SortedSet<GeoserverUserGroup> getGroupsForUser (GeoserverUser user)  throws IOException;

                
    /**
     * load from backendstore. On success,
     * a  {@link UserGroupLoadedEvent} should  be triggered 
     */
    void load() throws IOException;

    
    /**
     * @return the Spring name of the {@link GeoserverPasswordEncoder} object.
     * mandatory, default is 
     * {@link GeoserverDigestPasswordEncoder#BeanName}.
     *     
     */
    String getPasswordEncoderName();
    
    /**
     * @return the  name of the {@link PasswordValidator} object.
     * mandatory, default is {@link PasswordValidator#DEFAULT_NAME}
     * Validators can be loaded using 
     * {@link GeoServerSecurityManager#loadPasswordValidator(String)
     * 
     */
    String getPasswordValidatorName();

}