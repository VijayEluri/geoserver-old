package org.geoserver.security.jdbc;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.logging.Logger;


import org.geoserver.security.GeoserverRoleService;
import org.geoserver.security.GeoserverRoleStore;
import org.geoserver.security.config.SecurityNamedServiceConfig;
import org.geoserver.security.config.SecurityRoleServiceConfig;
import org.geoserver.security.event.RoleLoadedEvent;
import org.geoserver.security.event.RoleLoadedListener;
import org.geoserver.security.impl.GeoserverRole;
import org.geoserver.security.impl.Util;
import org.geoserver.security.jdbc.config.JDBCSecurityServiceConfig;

/**
 * JDBC implementation of {@link GeoserverRoleService}
 * 
 * @author christian
 *
 */
public  class JDBCRoleService extends AbstractJDBCService implements GeoserverRoleService {
    
    public final static String DEFAULT_DML_FILE="rolesdml.xml";
    public final static String DEFAULT_DDL_FILE="rolesddl.xml";
    
    
    /** logger */
    static Logger LOGGER = org.geotools.util.logging.Logging.getLogger("org.geoserver.security.jdbc");
    protected Set<RoleLoadedListener> listeners = 
        Collections.synchronizedSet(new HashSet<RoleLoadedListener>());
    
    protected GeoserverRole adminRole;
    
    
    public JDBCRoleService() {
    }

    
    @Override
    public GeoserverRole getAdminRole() {
        if (adminRole!=null) return adminRole;
        return GeoserverRole.ADMIN_ROLE;
    }

    
    @Override
    public boolean canCreateStore() {
        return true;
    }

    @Override
    public GeoserverRoleStore createStore() throws IOException {
        JDBCRoleStore store = new JDBCRoleStore();        
        store.initializeFromService(this);
        return store;
    }

    /**
     * Uses {@link #initializeDSFromConfig(SecurityNamedServiceConfig)}
     * and {@link #checkORCreateJDBCPropertyFile(String, File, String)} 
     *  
     * @see org.geoserver.security.GeoserverRoleService#initializeFromConfig(org.geoserver.security.config.SecurityNamedServiceConfig)
     */
    @Override
    public void initializeFromConfig(SecurityNamedServiceConfig config) throws IOException {
        
        this.name=config.getName();
        initializeDSFromConfig(config);

        if (config instanceof JDBCSecurityServiceConfig) {
            JDBCSecurityServiceConfig jdbcConfig =
                (JDBCSecurityServiceConfig) config;
                        
            String fileNameDML =jdbcConfig.getPropertyFileNameDML();
            File file = checkORCreateJDBCPropertyFile(fileNameDML,getConfigRoot(),DEFAULT_DML_FILE);
            dmlProps = Util.loadUniversal(new FileInputStream(file));
            
            String fileNameDDL =jdbcConfig.getPropertyFileNameDDL();
            file = checkORCreateJDBCPropertyFile(fileNameDDL, getConfigRoot(), DEFAULT_DDL_FILE);

            ddlProps = Util.loadUniversal(new FileInputStream(file));            
        }
        if (((SecurityRoleServiceConfig)config).getAdminRoleName()!=null) {
            adminRole = createRoleObject(GeoserverRole.ADMIN_ROLE.getAuthority());
        }

    }

    
    
    /**
     * @see org.geoserver.security.jdbc.AbstractJDBCService#getOrderedNamesForCreate()
     */
    protected  String[] getOrderedNamesForCreate() {
        return new String[] {
            "roles.create","roleprops.create","userroles.create","userroles.indexcreate",
            "grouproles.create","grouproles.indexcreate"
        };
                
    }
    /** 
     * @see org.geoserver.security.jdbc.AbstractJDBCService#getOrderedNamesForDrop()
     */
    protected  String[] getOrderedNamesForDrop() {
        return new String[] {
            "grouproles.drop","userroles.drop","roleprops.drop","roles.drop"
        };

    }

    
    /**
     * @see org.geoserver.security.GeoserverRoleService#getRoleByName(java.lang.String)
     */
    public GeoserverRole getRoleByName(String role)
            throws IOException {
        
        Connection con=null;
        PreparedStatement ps = null,ps2=null;
        ResultSet rs = null,rs2=null;
        GeoserverRole  roleObject  = null;
        try {
            con = getConnection();
            ps = getDMLStatement("roles.keyed",con);            
            ps.setString(1, role);
            rs = ps.executeQuery();
            if (rs.next()) {                
                roleObject  = createRoleObject(role);
                ps2 = getDMLStatement("roleprops.selectForRole",con);            
                ps2.setString(1, role);
                rs2 = ps2.executeQuery();
                while (rs2.next()) {
                    String propName = rs2.getString(1);
                    Object propValue = rs2.getObject(2);
                    roleObject.getProperties().put(propName, propValue==null ? "" : propValue );
                }                
            }                
        } catch (SQLException ex) {
            throw new IOException(ex);
        } finally {
            closeFinally(null, ps2, rs2);
            closeFinally(con, ps, rs);
        }
        return roleObject;
 
    }
    

    /**
     * @see org.geoserver.security.GeoserverRoleService#getRoles()
     */
    public  SortedSet<GeoserverRole> getRoles() throws IOException {
        Connection con=null;
        PreparedStatement ps = null;
        ResultSet rs = null;
        Map<String,GeoserverRole> map = new HashMap<String,GeoserverRole>();
        try {
            con = getConnection();
            ps = getDMLStatement("roles.all",con);            
            rs = ps.executeQuery();
            while (rs.next()) {                
                String rolename = rs.getString(1);                
                GeoserverRole roleObject = createRoleObject(rolename);            
                map.put(rolename, roleObject);
            }
            
            ps.close();
            rs.close();
            
            ps = getDMLStatement("roleprops.all",con);
            rs = ps.executeQuery();
            while (rs.next()) {    
                String roleName = rs.getString(1);
                String propName = rs.getString(2);
                Object propValue = rs.getString(3);
                GeoserverRole roleObject = map.get(roleName);
                if (roleObject!=null) {
                    roleObject.getProperties().put(propName, propValue==null ? "" : propValue);
                }
            }
        } catch (SQLException ex) {
            throw new IOException(ex);
        } finally {
            closeFinally(con, ps, rs);
        }
                                        
        SortedSet<GeoserverRole> roles = new TreeSet<GeoserverRole>();
        roles.addAll(map.values());               
        return Collections.unmodifiableSortedSet(roles);
                
    }
    
    /**
     * @see org.geoserver.security.GeoserverRoleService#getParentMappings()
     */
    public  Map<String,String> getParentMappings() throws IOException {
        Connection con=null;
        PreparedStatement ps = null;
        ResultSet rs = null;
        Map<String,String> map = new HashMap<String,String>();
        try {
            con = getConnection();
            ps = getDMLStatement("roles.all",con);            
            rs = ps.executeQuery();
            while (rs.next()) {                
                String rolename = rs.getString(1);
                String parentname = rs.getString(2);
                map.put(rolename, parentname);
            }
        } catch (SQLException ex) {
            throw new IOException(ex);
        } finally {
            closeFinally(con, ps, rs);
        }                
        return Collections.unmodifiableMap(map);
    }
    

    /**
     * @see org.geoserver.security.GeoserverRoleService#createRoleObject(java.lang.String)
     */
    public GeoserverRole createRoleObject(String role) {
        return new GeoserverRole(role);
    }
    
    
    
    /** 
     * @see org.geoserver.security.GeoserverRoleService#getRolesForUser(java.lang.String)
     */
    public  SortedSet<GeoserverRole> getRolesForUser(String username) throws IOException {
        Connection con=null;
        PreparedStatement ps = null,ps2 = null;
        ResultSet rs = null,rs2=null;
        Map<String,GeoserverRole> map = new HashMap<String,GeoserverRole>();
        try {
            con = getConnection();
            ps = getDMLStatement("userroles.rolesForUser",con);
                        

            ps.setString(1, username);
            rs = ps.executeQuery();
            while (rs.next()) {                
                String rolename = rs.getString(1);                
                GeoserverRole roleObject = createRoleObject(rolename);                
                map.put(rolename,roleObject);         
                
            }
            rs.close();
            ps.close();
            
            ps = getDMLStatement("roleprops.selectForUser",con);
            ps.setString(1, username);
            rs = ps.executeQuery();
            while (rs.next()) {
                String rolename = rs.getString(1);                
                String propName = rs.getString(2);
                Object propValue = rs.getObject(3);
                GeoserverRole roleObject = map.get(rolename);
                if (roleObject!=null) {
                    roleObject.getProperties().put(propName, propValue==null ? "" : propValue);
                }
            }                                

        } catch (SQLException ex) {
            throw new IOException(ex);
        } finally {
            closeFinally(null, ps2, rs2);
            closeFinally(con, ps, rs);
        }
                                
        TreeSet<GeoserverRole>roles= new TreeSet<GeoserverRole>();
        roles.addAll(map.values());
        return Collections.unmodifiableSortedSet(roles);
    }
    
    /** 
     * @see org.geoserver.security.GeoserverRoleService#getRolesForGroup(java.lang.String)
     */
    public  SortedSet<GeoserverRole> getRolesForGroup(String groupname) throws IOException {
        Connection con=null;
        PreparedStatement ps = null,ps2 = null;
        ResultSet rs = null,rs2=null;
        Map<String,GeoserverRole> map = new HashMap<String,GeoserverRole>();
        try {
            con = getConnection();
            ps = getDMLStatement("grouproles.rolesForGroup",con);
                        

            ps.setString(1, groupname);
            rs = ps.executeQuery();
            while (rs.next()) {                
                String rolename = rs.getString(1);
                GeoserverRole roleObject = createRoleObject(rolename);                
                map.put(rolename,roleObject);         
                
            }
            rs.close();
            ps.close();
            
            ps = getDMLStatement("roleprops.selectForGroup",con);
            ps.setString(1, groupname);
            rs = ps.executeQuery();
            while (rs.next()) {
                String rolename = rs.getString(1);                
                String propName = rs.getString(2);
                Object propValue = rs.getObject(3);
                GeoserverRole roleObject = map.get(rolename);
                if (roleObject!=null) {
                    roleObject.getProperties().put(propName, propValue==null ? "" : propValue);
                }
            }                                

        } catch (SQLException ex) {
            throw new IOException(ex);
        } finally {
            closeFinally(null, ps2, rs2);
            closeFinally(con, ps, rs);
        }
                                
        TreeSet<GeoserverRole>roles= new TreeSet<GeoserverRole>();
        roles.addAll(map.values());
        return Collections.unmodifiableSortedSet(roles);
    }


    /**
     * @see org.geoserver.security.GeoserverRoleService#load()
     */
    public void load() throws IOException {
        // do nothing
    }



    /**
     * @see org.geoserver.security.GeoserverRoleService#getParentRole(org.geoserver.security.impl.GeoserverRole)
     */
    public GeoserverRole getParentRole(GeoserverRole role)
            throws IOException {

        Connection con=null;
        PreparedStatement ps = null,ps2=null;
        ResultSet rs = null,rs2=null;
        GeoserverRole  roleObject  = null;
        try {
            con = getConnection();
            ps = getDMLStatement("roles.keyed",con);            
            ps.setString(1, role.getAuthority());
            rs = ps.executeQuery();
            if (rs.next()) {                
                String parent = rs.getString(1);                
                if (parent!=null) { // do we have a parent ?
                    roleObject  = createRoleObject(parent);
                    ps2 = getDMLStatement("roleprops.selectForRole",con);            
                    ps2.setString(1, parent);
                    rs2 = ps2.executeQuery();
                    while (rs2.next()) {
                        String propName = rs2.getString(1);
                        Object propValue = rs2.getObject(2);
                        roleObject.getProperties().put(propName, propValue==null ? "" : propValue);
                    }
                }    
            }                
        } catch (SQLException ex) {
            throw new IOException(ex);
        } finally {
            closeFinally(null, ps2, rs2);
            closeFinally(con, ps, rs);
        }
        return roleObject;
    }


    /** 
     * @see org.geoserver.security.GeoserverRoleService#registerRoleLoadedListener(RoleLoadedListener)
     */
    public void registerRoleLoadedListener(RoleLoadedListener listener) {
        listeners.add(listener);
    }


    /** 
     * @see org.geoserver.security.GeoserverRoleService#unregisterRoleLoadedListener(RoleLoadedListener)
     */
    public void unregisterRoleLoadedListener(RoleLoadedListener listener) {
        listeners.remove(listener);
    }
    
    /**
     * Fire {@link RoleLoadedEvent} for all listeners
     */
    protected void fireRoleChangedEvent() {
        RoleLoadedEvent event = new RoleLoadedEvent(this);
        for (RoleLoadedListener listener : listeners) {
            listener.rolesChanged(event);
        }
    }


    /**
     * @see org.geoserver.security.GeoserverRoleService#getGroupNamesForRole(org.geoserver.security.impl.GeoserverRole)
     */
    public SortedSet<String> getGroupNamesForRole(GeoserverRole role) throws IOException {
        Connection con=null;
        PreparedStatement ps = null;
        ResultSet rs = null;
        SortedSet<String> result = new TreeSet<String>();
        try {
            con = getConnection();
            ps = getDMLStatement("grouproles.groupsForRole",con);
                        
            ps.setString(1, role.getAuthority());
            rs = ps.executeQuery();
            while (rs.next()) {                
                String groupname = rs.getString(1);                
                result.add(groupname);
            }
        } catch (SQLException ex) {
            throw new IOException(ex);
        } finally {
            closeFinally(con, ps, rs);
        }
        return Collections.unmodifiableSortedSet(result);
    }


    /**
     * @see org.geoserver.security.GeoserverRoleService#getUserNamesForRole(org.geoserver.security.impl.GeoserverRole)
     */
    public SortedSet<String> getUserNamesForRole(GeoserverRole role) throws IOException{
        Connection con=null;
        PreparedStatement ps = null;
        ResultSet rs = null;
        SortedSet<String> result = new TreeSet<String>();
        try {
            con = getConnection();
            ps = getDMLStatement("userroles.usersForRole",con);
                        

            ps.setString(1, role.getAuthority());
            rs = ps.executeQuery();
            while (rs.next()) {                
                String username = rs.getString(1);                
                result.add(username);
            }
        } catch (SQLException ex) {
            throw new IOException(ex);
        } finally {
            closeFinally(con, ps, rs);
        }
        return Collections.unmodifiableSortedSet(result);
    }    

    /**
     * @see org.geoserver.security.GeoserverRoleService#personalizeRoleParams(java.lang.String, java.util.Properties, java.lang.String, java.util.Properties)
     * 
     * Default implementation: if a user property name equals a role propertyname, 
     * take the value from to user property and use it for the role property. 
     * 
     */
    public  Properties personalizeRoleParams (String roleName,Properties roleParams, 
            String userName,Properties userProps) throws IOException {
        Properties props = null;
        
        // this is true if the set is modified --> common 
        // property names exist
        
        props = new Properties();
        boolean personalized=false;
        
        for (Object key: roleParams.keySet()) {
            if (userProps.containsKey(key)) {
                props.put(key, userProps.get(key));
                personalized=true;
            }
            else
                props.put(key,roleParams.get(key));
        }
        return personalized ?  props : null;
    }

    /**
     * The root configuration for the role service.
     */
    public File getConfigRoot() throws IOException {
        return new File(getSecurityManager().getRoleRoot(), getName());
    }
}
