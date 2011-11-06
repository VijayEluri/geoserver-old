/* Copyright (c) 2001 - 2011 TOPP - www.openplans.org.  All rights reserved.
 * This code is licensed under the GPL 2.0 license, availible at the root
 * application directory.
 */

package org.geoserver.security.impl;

import java.io.IOException;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

import junit.framework.Assert;

import org.geoserver.security.GeoserverRoleService;
import org.geoserver.security.GeoserverRoleStore;
import org.geoserver.security.GeoserverUserGroupService;
import org.geoserver.security.GeoserverUserGroupStore;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

public abstract class AbstractUserDetailsServiceTest extends AbstractSecurityServiceTest {

    
    protected GeoserverRoleService roleService;
    protected GeoserverUserGroupService usergroupService;
    protected GeoserverRoleStore roleStore;
    protected GeoserverUserGroupStore usergroupStore;
    

    
    
    protected void setServices(String serviceName) throws IOException{
        roleService=createRoleService(serviceName);
        usergroupService=createUserGroupService(serviceName);
        roleStore = createStore(roleService);
        usergroupStore =createStore(usergroupService);
        getSecurityManager().setActiveRoleService(roleService);
        //getSecurityManager().saveSecurityConfig(config)setActiveUserGroupService(usergroupService);
    }
    
    public void testConfiguration() {
        try {
            setServices("config");
            assertEquals(roleService,getSecurityManager().getActiveRoleService());
            //assertEquals(usergroupService,getSecurityManager().getActiveUserGroupService());
            assertEquals(usergroupService.getName(),
                    getSecurityManager().loadUserGroupService("config").getName());
            assertTrue(roleService.canCreateStore());
            assertTrue(usergroupService.canCreateStore());
        } catch (IOException ex) {
            Assert.fail(ex.getMessage());
        }
    }
    
    public void testRoleCalculation() {
        try {

            setServices("rolecalulation");
            // populate with values
            insertValues(roleStore);
            insertValues(usergroupStore);
            
            String username = "theUser";
            GeoserverUser theUser = null;
            boolean fail=true;
            try {
                theUser = (GeoserverUser) usergroupService.loadUserByUsername(username);
            } catch (UsernameNotFoundException ex) {
                fail = false;
            }
            if (fail) {
                Assert.fail("No UsernameNotFoundException thrown");
            }
            
            theUser=usergroupStore.createUserObject(username, "", true);
            usergroupStore.addUser(theUser);
                                               
            GeoserverRole role = null;
            Set<GeoserverRole> roles = new HashSet<GeoserverRole>();
            
           // no roles
            checkRoles(username, roles);
                        
            // first direct role
            role=roleStore.createRoleObject("userrole1");
            roleStore.addRole(role);
            roleStore.associateRoleToUser(role, username);
            roles.add(role);
            checkRoles(username, roles);
            
            // second direct role
            role=roleStore.createRoleObject("userrole2");
            roleStore.addRole(role);
            roleStore.associateRoleToUser(role, username);
            roles.add(role);
            checkRoles(username, roles);

            // first role inherited by first group
            GeoserverUserGroup theGroup1=usergroupStore.createGroupObject("theGroup1",true);
            usergroupStore.addGroup(theGroup1);
            usergroupStore.associateUserToGroup(theUser, theGroup1);
            role=roleStore.createRoleObject("grouprole1a");
            roleStore.addRole(role);
            roleStore.associateRoleToGroup(role, "theGroup1");
            roles.add(role);
            checkRoles(username, roles);
            
            // second role inherited by first group
            role=roleStore.createRoleObject("grouprole1b");
            roleStore.addRole(role);
            roleStore.associateRoleToGroup(role, "theGroup1");
            roles.add(role);
            checkRoles(username, roles);

            // first role inherited by second group, but the group is disabled
            GeoserverUserGroup theGroup2=usergroupStore.createGroupObject("theGroup2",false);
            usergroupStore.addGroup(theGroup2);
            usergroupStore.associateUserToGroup(theUser, theGroup2);
            role=roleStore.createRoleObject("grouprole2a");
            roleStore.addRole(role);
            roleStore.associateRoleToGroup(role, "theGroup2");            
            checkRoles(username, roles);

            // enable the group
            theGroup2.setEnabled(true);
            usergroupStore.updateGroup(theGroup2);
            roles.add(role);
            checkRoles(username, roles);

            // check inheritance, first level
            GeoserverRole tmp = role;
            role=roleStore.createRoleObject("grouprole2aa");
            roleStore.addRole(role);
            roleStore.setParentRole(tmp, role);
            roles.add(role);
            checkRoles(username, roles);
            
            // check inheritance, second level
            tmp = role;
            role=roleStore.createRoleObject("grouprole2aaa");
            roleStore.addRole(role);
            roleStore.setParentRole(tmp, role);
            roles.add(role);
            checkRoles(username, roles);

            // remove second level
            tmp=roleStore.getRoleByName("grouprole2aa");
            roleStore.setParentRole(tmp, null);
            roles.remove(role);
            checkRoles(username, roles);
            
            // delete first level role
            roleStore.removeRole(tmp);
            roles.remove(tmp);
            checkRoles(username, roles);
            
            // delete second group
            usergroupStore.removeGroup(theGroup2);
            tmp=roleStore.getRoleByName("grouprole2a");
            roles.remove(tmp);
            checkRoles(username, roles);
            
            // remove role from first group
            tmp=roleStore.getRoleByName("grouprole1b");
            roleStore.disAssociateRoleFromGroup(tmp, theGroup1.getGroupname());
            roles.remove(tmp);
            checkRoles(username, roles);
            
            // remove role from user
            tmp=roleStore.getRoleByName("userrole2");
            roleStore.disAssociateRoleFromUser(tmp, theUser.getUsername());
            roles.remove(tmp);
            checkRoles(username, roles);
            
        } catch (Exception ex) {
            Assert.fail(ex.getMessage());
        }
    }
    
    public void testPersonalizedRoles() {
       try {
    
            setServices("personalizedRoles");
            // populate with values
            insertValues(roleStore);
            insertValues(usergroupStore);
            
            String username = "persUser";
            GeoserverUser theUser = null;
            
            theUser=usergroupStore.createUserObject(username, "", true);
            theUser.getProperties().put("propertyA", "A");
            theUser.getProperties().put("propertyB", "B");
            theUser.getProperties().put("propertyC", "C");
            usergroupStore.addUser(theUser);
                                               
            GeoserverRole role = null;
                        
            role=roleStore.createRoleObject("persrole1");
            role.getProperties().put("propertyA", "");
            role.getProperties().put("propertyX", "X");
            roleStore.addRole(role);
            roleStore.associateRoleToUser(role, username);
            
            role=roleStore.createRoleObject("persrole2");
            role.getProperties().put("propertyB", "");
            role.getProperties().put("propertyY", "Y");
            roleStore.addRole(role);
            roleStore.associateRoleToUser(role, username);
            
            syncbackends();
            
            UserDetails details = usergroupService.loadUserByUsername(username);
            
            Collection<GrantedAuthority> authColl = details.getAuthorities();
            
            for (GrantedAuthority auth : authColl) {
                role = (GeoserverRole) auth;
                if ("persrole1".equals(role.getAuthority())) {
                    assertEquals("A", role.getProperties().get("propertyA"));
                    assertEquals("X", role.getProperties().get("propertyX"));
                    
                    GeoserverRole anonymousRole = 
                        roleStore.getRoleByName(role.getAuthority());
                    
                    assertFalse(role.isAnonymous());
                    assertTrue(anonymousRole.isAnonymous());
                    assertFalse(role==anonymousRole);
                    assertFalse(role.equals(anonymousRole));
                    assertTrue(theUser.getUsername().equals(role.getUserName()));
                    assertNull(anonymousRole.getUserName());
                    
                } else if ("persrole2".equals(role.getAuthority())) {
                    assertEquals("B", role.getProperties().get("propertyB"));
                    assertEquals("Y", role.getProperties().get("propertyY"));                                        
                } else {
                    Assert.fail("Unknown role "+role.getAuthority() + "for user " + username);
                }                                        
            }
            
       } catch (Exception ex) {       
           Assert.fail(ex.getMessage());
       }                
    }
    
    protected void checkRoles(String username, Set<GeoserverRole> roles) throws IOException{
        syncbackends();
        UserDetails details = usergroupService.loadUserByUsername(username);
        Collection<GrantedAuthority> authColl = details.getAuthorities();
        assertEquals(roles.size(), authColl.size());
        for (GeoserverRole role : roles) {
            assertTrue(authColl.contains(role));
        }
    }
    
    protected void syncbackends() throws IOException{
        roleStore.store();
        usergroupStore.store();

    }
}
