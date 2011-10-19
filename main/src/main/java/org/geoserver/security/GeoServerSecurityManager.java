/* Copyright (c) 2001 - 2011 TOPP - www.openplans.org. All rights reserved.
 * This code is licensed under the GPL 2.0 license, availible at the root
 * application directory.
 */
package org.geoserver.security;

import java.io.File;
import java.io.FileFilter;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Properties;
import java.util.SortedSet;
import java.util.StringTokenizer;
import java.util.TreeSet;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;

import java.util.logging.Level;
import java.util.logging.Logger;

import org.apache.commons.io.FileUtils;
import org.geoserver.config.GeoServerDataDirectory;
import org.geoserver.config.util.XStreamPersister;
import org.geoserver.config.util.XStreamPersisterFactory;
import org.geoserver.platform.GeoServerExtensions;
import org.geoserver.security.concurrent.LockingGrantedAuthorityService;
import org.geoserver.security.concurrent.LockingUserGroupService;
import org.geoserver.security.config.FileBasedSecurityServiceConfig;
import org.geoserver.security.config.SecurityConfig;
import org.geoserver.security.config.SecurityNamedServiceConfig;
import org.geoserver.security.config.UserDetailsServiceConfig;
import org.geoserver.security.config.impl.UserDetailsServiceConfigImpl;
import org.geoserver.security.config.impl.XMLFileBasedSecurityServiceConfigImpl;

import org.geoserver.security.file.GrantedAuthorityFileWatcher;
import org.geoserver.security.file.UserGroupFileWatcher;
import org.geoserver.security.impl.GeoserverGrantedAuthority;
import org.geoserver.security.impl.GeoserverUser;
import org.geoserver.security.impl.Util;
import org.geoserver.security.xml.XMLConstants;
import org.geoserver.security.xml.XMLGrantedAuthorityService;
import org.geoserver.security.xml.XMLSecurityProvider;
import org.geoserver.security.xml.XMLUserGroupService;

import org.geotools.util.logging.Logging;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.BeanCreationException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.memory.UserAttribute;
import org.springframework.security.core.userdetails.memory.UserAttributeEditor;

/**
 * Top level singleton/facade/dao for the security authentication/authorization subsystem.  
 * 
 * @author Justin Deoliveira, OpenGeo
 *
 */
public class GeoServerSecurityManager implements ApplicationContextAware {

    static Logger LOGGER = Logging.getLogger("org.geoserver.security");

    /** data directory file system access */
    GeoServerDataDirectory dataDir;

    /** app context for loading plugins */
    ApplicationContext appContext;

    /** user details service */
    GeoserverUserDetailsService userDetails;

    /** cached user groups */
    ConcurrentHashMap<String, GeoserverUserGroupService> userGroups = 
        new ConcurrentHashMap<String, GeoserverUserGroupService>();

    /** cached role services */
    ConcurrentHashMap<String, GeoserverGrantedAuthorityService> grantedAuthorities = 
        new ConcurrentHashMap<String, GeoserverGrantedAuthorityService>();

    /** some helper instances for storing/loading service config */ 
    RoleServiceHelper roleServiceHelper = new RoleServiceHelper();
    UserGroupServiceHelper userGroupServiceHelper = new UserGroupServiceHelper();

    public GeoServerSecurityManager(GeoserverUserDetailsService userDetails, 
        GeoServerDataDirectory dataDir) throws IOException {
       
        this.userDetails = userDetails;
        this.dataDir = dataDir;

        //migrate from old security config
        migrateIfNecessary();
    }

    @Override
    public void setApplicationContext(ApplicationContext appContext) throws BeansException {
        this.appContext = appContext;

        //read config and initialize... we do this now since we can be ensured that the spring
        // context has been property initialized, and we can successfully look up security plugins
        try {
            init();
        } catch (Exception e) {
            throw new BeanCreationException("Error occured reading security configuration", e);
        }
    }

    /*
     * loads configuration and initializes the security subsystem.
     */
    void init() throws Exception {
        UserDetailsServiceConfig config = loadSecurityConfig();

        //load the user group service and ensure it is properly configured
        String userGroupServiceName = config.getUserGroupServiceName();
        GeoserverUserGroupService userGroupService = null;
        try {
            userGroupService = loadUserGroupService(userGroupServiceName);
            
            //TODO:
            //if (!userGroupService.isConfigured()) {
            //    userGroupService = null;
            //}
        }
        catch(Exception e) {
            LOGGER.log(Level.WARNING, String.format("Error occured loading user group service %s, "
                +  "falling back to default user group service", userGroupServiceName), e);
        }

        if (userGroupService == null) {
            try {
                userGroupService = loadUserGroupService("default");
            }
            catch(Exception e) {
                throw new RuntimeException("Fatal error occurred loading default role service", e);
            }
        }

        //load the role authority and ensure it is properly configured
        String roleServiceName = config.getGrantedAuthorityServiceName();
        GeoserverGrantedAuthorityService roleService = null;
        try {
            roleService = loadRoleService(roleServiceName);
            
            //TODO:
            //if (!roleService.isConfigured()) {
            //    roleService = null;
            //}
        }
        catch(Exception e) {
            LOGGER.log(Level.WARNING, String.format("Error occured loading role service %s, "
                +  "falling back to default role service", roleServiceName), e);
        }

        if (roleService == null) {
            try {
                roleService = loadRoleService("default");
            }
            catch(Exception e) {
                throw new RuntimeException("Fatal error occurred loading default role service", e);
            }
        }

        //configure the user details instance
        getUserDetails().setUserGroupService(userGroupService);
        getUserDetails().setGrantedAuthorityService(roleService);
    }

    /**
     * The user details service.
     */
    public GeoserverUserDetailsService getUserDetails() {
        return userDetails;
    }

    /**
     * Security configuration root directory.
     */
    public File getSecurityRoot() throws IOException {
        return dataDir.findOrCreateSecurityRoot(); 
    }

    /**
     * Role/granted authority configuration root directory.
     */
    public File getRoleRoot() throws IOException {
        return getRoleRoot(true); 
    }

    File getRoleRoot(boolean create) throws IOException {
        return create ? 
            dataDir.findOrCreateSecurityDir("role") : dataDir.findSecurityDir("role"); 
    }

    /**
     * User/group configuration root directory.
     */
    public File getUserGroupRoot() throws IOException {
        return dataDir.findOrCreateSecurityDir("usergroup"); 
    }

    /**
     * Lists all available role service configurations.
     */
    public SortedSet<String> listRoleServices() throws IOException {
        return listFiles(getRoleRoot());
    }

    /**
     * Loads a role service from a named configuration.
     * 
     * @param name The name of the role service configuration.
     */
    public GeoserverGrantedAuthorityService loadRoleService(String name)
            throws IOException {
        GeoserverGrantedAuthorityService roleService = grantedAuthorities.get(name);
        if (roleService == null) {
            synchronized (this) {
                roleService = grantedAuthorities.get(name);
                if (roleService == null) {
                    roleService = roleServiceHelper.load(name);
                    if (roleService != null) {
                        grantedAuthorities.put(name, roleService);
                    }
                }
            }
        }
        return roleService;
    }

    /**
     * Saves/persists a role service configuration.
     */
    public void saveRoleService(SecurityNamedServiceConfig config) throws IOException {
        roleServiceHelper.saveConfig(config);
    }

    /**
     * Removes a role service configuration.
     * 
     * @param name The name of the role service configuration.
     */
    public void removeRoleService(String name) throws IOException {
        //remove the service
        GeoserverGrantedAuthorityService grantedAuth = userDetails.getGrantedAuthorityService();
        if (grantedAuth != null && grantedAuth.getName().equals(name)) {
            throw new IllegalArgumentException("Can't delete active authority service: " + name);
        }

        //remove the cached service
        grantedAuthorities.remove(name);
        
        //remove the config dir
        roleServiceHelper.removeConfig(name);
    }

    /**
     * Lists all available user group service configurations.
     */
    public SortedSet<String> listUserGroupServices() throws IOException {
        return listFiles(getUserGroupRoot());
    }

    /**
     * Loads a user group service from a named configuration.
     * 
     * @param name The name of the user group service configuration.
     */
    public GeoserverUserGroupService loadUserGroupService(String name) throws IOException {
        GeoserverUserGroupService ugService = userGroups.get(name);
        if (ugService == null) {
            synchronized (this) {
                ugService = userGroups.get(name);
                if (ugService == null) {
                    ugService = userGroupServiceHelper.load(name);
                    if (ugService != null) {
                        userGroups.put(name, ugService);
                    }
                }
            }
        }
        return ugService;
    }

    /**
     * Saves/persists a user group service configuration.
     */
    public void saveUserGroupService(SecurityNamedServiceConfig config) throws IOException {
        userGroupServiceHelper.saveConfig(config);
    }

    /**
     * Removes a user group service configuration.
     * 
     * @param name The name of the user group service configuration.
     */
    public void removeUserGroupService(String name) throws IOException {
        //remove the service
        GeoserverUserGroupService userGroup = getUserDetails().getUserGroupService();
        if (userGroup != null && userGroup.getName().equals(name)) {
            throw new IllegalArgumentException("Can't delete active user group service: " + name);
        }

        //remove the cached service
        userGroups.remove(name);
        
        //remove the config dir
        userGroupServiceHelper.removeConfig(name);
    }

    /*
     * converts an old security configuration to the new
     */
    void migrateIfNecessary() throws IOException{
        
        if (getRoleRoot(false) != null) {
            File oldUserFile = new File(getSecurityRoot(), "users.properties.old");
            if (oldUserFile.exists()) {
                LOGGER.warning(oldUserFile.getCanonicalPath()+" could be removed manually");
            }
            return; // already migrated
        }
        
        LOGGER.info("Start security migration");
        
        //create required directories
        getRoleRoot();
        getUserGroupRoot();
        
        // check for service.properties, create if necessary
        File serviceFile = new File(getSecurityRoot(), "service.properties");
        if (serviceFile.exists()==false) {
            FileUtils.copyURLToFile(Util.class.getResource("serviceTemplate.properties"),
                    serviceFile);
        }

        long checkInterval = 10000; // 10 secs

        //check for the default user group service, create if necessary
        GeoserverUserGroupService userGroupService = 
            loadUserGroupService(XMLUserGroupService.DEFAULT_NAME);

        if (userGroupService == null) {
            XMLFileBasedSecurityServiceConfigImpl ugConfig = new XMLFileBasedSecurityServiceConfigImpl();
            ugConfig.setName(XMLUserGroupService.DEFAULT_NAME);
            ugConfig.setClassName(XMLUserGroupService.class.getName());
            ugConfig.setCheckInterval(checkInterval); 
            ugConfig.setFileName(XMLConstants.FILE_UR);
            ugConfig.setStateless(false);
            ugConfig.setValidating(true);
            saveUserGroupService(ugConfig);
            userGroupService = loadUserGroupService(XMLUserGroupService.DEFAULT_NAME);
        }

        //check for the default role service, create if necessary
        GeoserverGrantedAuthorityService roleService = 
            loadRoleService(XMLGrantedAuthorityService.DEFAULT_NAME);

        if (roleService == null) {
            XMLFileBasedSecurityServiceConfigImpl gaConfig = new XMLFileBasedSecurityServiceConfigImpl();                 
            gaConfig.setName(XMLGrantedAuthorityService.DEFAULT_NAME);
            gaConfig.setClassName(XMLGrantedAuthorityService.class.getName());
            gaConfig.setCheckInterval(checkInterval); 
            gaConfig.setFileName(XMLConstants.FILE_RR);
            gaConfig.setStateless(false);
            gaConfig.setValidating(true);
            saveRoleService(gaConfig);

            roleService = loadRoleService(XMLGrantedAuthorityService.DEFAULT_NAME);
        }

        //save the top level config
        UserDetailsServiceConfig config = new UserDetailsServiceConfigImpl();
        config.setGrantedAuthorityServiceName(XMLGrantedAuthorityService.DEFAULT_NAME);
        config.setUserGroupServiceName(XMLUserGroupService.DEFAULT_NAME);
        saveSecurityConfig(config);

        //TODO: just call initializeFrom
        userGroupService.setSecurityManager(this);
        roleService.setSecurityManager(this);

        //populate the user group and role service
        GeoserverUserGroupStore userGroupStore = userGroupService.createStore();
        GeoserverGrantedAuthorityStore roleStore = roleService.createStore();

        //migradate from users.properties
        File usersFile = new File(getSecurityRoot(), "users.properties");
        if (usersFile.exists()) {
            //load user.properties populate the services 
            Properties props = Util.loadPropertyFile(usersFile);

            UserAttributeEditor configAttribEd = new UserAttributeEditor();

            for (Iterator<Object> iter = props.keySet().iterator(); iter.hasNext();) {
                // the attribute editors parses the list of strings into password, username and enabled
                // flag
                String username = (String) iter.next();
                configAttribEd.setAsText(props.getProperty(username));

                // if the parsing succeeded turn that into a user object
                UserAttribute attr = (UserAttribute) configAttribEd.getValue();
                if (attr != null) {
                    GeoserverUser user = 
                        userGroupStore.createUserObject(username, attr.getPassword(), attr.isEnabled());
                    userGroupStore.addUser(user);

                    for (GrantedAuthority auth : attr.getAuthorities()) {
                        GeoserverGrantedAuthority role = 
                            roleStore.getGrantedAuthorityByName(auth.getAuthority());
                        if (role==null) {
                            role = roleStore.createGrantedAuthorityObject(auth.getAuthority());
                            roleStore.addGrantedAuthority(role);
                        }
                        roleStore.associateRoleToUser(role, username);
                    }
                }
            }
        } else  {
            // no user.properties, populate with default user and roles
            if (userGroupService.getUserByUsername(GeoserverUser.DEFAULT_ADMIN.getUsername()) == null) {
                userGroupStore.addUser(GeoserverUser.DEFAULT_ADMIN);
                roleStore.addGrantedAuthority(GeoserverGrantedAuthority.ADMIN_ROLE);
                roleStore.associateRoleToUser(GeoserverGrantedAuthority.ADMIN_ROLE,
                        GeoserverUser.DEFAULT_ADMIN.getUsername());
            }
        }

        // check for roles in service.properties but not in user.properties 
        serviceFile = new File(getSecurityRoot(), "service.properties");
        if (serviceFile.exists()) {
            Properties props = Util.loadPropertyFile(serviceFile);
            for (Entry<Object,Object> entry: props.entrySet()) {
                StringTokenizer tokenizer = new StringTokenizer((String)entry.getValue(),",");
                while (tokenizer.hasMoreTokens()) {
                    String roleName = tokenizer.nextToken().trim();
                    if (roleName.length()>0) {
                        if (roleStore.getGrantedAuthorityByName(roleName)==null)
                            roleStore.addGrantedAuthority(roleStore.createGrantedAuthorityObject(roleName));
                    }
                }
            }
        }

        // check for  roles in data.properties but not in user.properties
        File dataFile = new File(getSecurityRoot(), "layer.properties");
        if (dataFile.exists()) {
            Properties props = Util.loadPropertyFile(dataFile);
            for (Entry<Object,Object> entry: props.entrySet()) {
                if ("mode".equals(entry.getKey().toString()))
                    continue; // skip mode directive
                StringTokenizer tokenizer = new StringTokenizer((String)entry.getValue(),",");
                while (tokenizer.hasMoreTokens()) {
                    String roleName = tokenizer.nextToken().trim();
                    if (roleName.length()>0 && roleName.equals("*")==false) {
                        if (roleStore.getGrantedAuthorityByName(roleName)==null)
                            roleStore.addGrantedAuthority(roleStore.createGrantedAuthorityObject(roleName));
                    }
                }
            }
        }

        //persist the changes
        roleStore.store();
        userGroupStore.store();
        
        // first part of migration finished, rename old file
        if (usersFile.exists()) {
            File oldUserFile = new File(usersFile.getCanonicalPath()+".old");
            usersFile.renameTo(oldUserFile);
            LOGGER.info("Renamed "+usersFile.getCanonicalPath() + " to " +
                    oldUserFile.getCanonicalPath());
        }
                
        LOGGER.info("End security migration");
    }

    /*
     * looks up security plugins
     */
    List<GeoServerSecurityProvider> lookupSecurityProviders() {
        List<GeoServerSecurityProvider> list = new ArrayList<GeoServerSecurityProvider>( 
            GeoServerExtensions.extensions(GeoServerSecurityProvider.class, appContext));
        list.add(new XMLSecurityProvider());
        return list;
    }

    /*
     * list files in a directory.
     */
    SortedSet<String> listFiles(File dir) {
        SortedSet<String> result = new TreeSet<String>();
        File[] dirs = dir.listFiles(new FileFilter() {
            @Override
            public boolean accept(File pathname) {
                return pathname.isDirectory();
            }
        });
        for (File d : dirs) {
            result.add(d.getName());
        }
        return result;
    }

    /*
     * creates the persister for security plugin configuration.
     */
    XStreamPersister persister() throws IOException{
        List<GeoServerSecurityProvider> all = lookupSecurityProviders();
        
        //create and configure an xstream persister to load the configuration files
        XStreamPersister xp = new XStreamPersisterFactory().createXMLPersister();
        xp.getXStream().alias("security", UserDetailsServiceConfigImpl.class);
        for (GeoServerSecurityProvider roleService : all) {
            roleService.configure(xp);
        }
        return xp;
    }

    /*
     * loads the global security config
     */
    UserDetailsServiceConfig loadSecurityConfig() throws IOException {
        return (UserDetailsServiceConfig) loadConfigFile(getSecurityRoot(), persister());
    }

    /*
     * saves the global security config
     */
    void saveSecurityConfig(UserDetailsServiceConfig config) throws IOException {
        FileOutputStream fout = new FileOutputStream(new File(getSecurityRoot(), "config.xml"));
        try {
            XStreamPersister xp = persister();
            xp.getXStream().alias("security", UserDetailsServiceConfigImpl.class);
            xp.save(config, fout); 
        }
        finally {
            fout.close();
        }
    }

    /*
     * reads a file named 'config.xml' from the specified directly using the specified xstream 
     * persister
     */
    SecurityConfig loadConfigFile(File directory, XStreamPersister xp) throws IOException {
        FileInputStream fin = new FileInputStream(new File(directory, "config.xml"));
        try {
            return xp.load(fin, SecurityConfig.class);
        }
        finally {
            fin.close();
        }
    }

    /*
     * saves a file named 'config.xml' from the specified directly using the specified xstream 
     * persister
     */
    void saveConfigFile(SecurityConfig config, File directory, XStreamPersister xp) 
            throws IOException {
        //TODO: do a safe save, where we write first to a temp file to avoid corrupting the 
        // existing file in case of an error during serialization
        FileOutputStream fout = new FileOutputStream(new File(directory, "config.xml"));
        try {
            xp.save(config, fout);
        }
        finally {
            fout.close();
        }
    }

    class UserGroupServiceHelper {
        public GeoserverUserGroupService load(String name) throws IOException {
            
            SecurityNamedServiceConfig config = loadConfig(name);
            if (config == null) {
                //no such config
                return null;
            }

            //look up the service for this config
            GeoserverUserGroupService service = null;

            for (GeoServerSecurityProvider p : lookupSecurityProviders()) {
                if (p.getUserGroupServiceClass() == null) {
                    continue;
                }
                if (p.getUserGroupServiceClass().getName().equals(config.getClassName())) {
                    service = p.createUserGroupService(config);
                    break;
                }
            }

            if (service == null) {
                throw new IOException("No user group service matching config: " + config);
            }

            service.setSecurityManager(GeoServerSecurityManager.this);
            if (!config.isStateless()) {
                service = new LockingUserGroupService(service);
            }
            service.setName(name);
            service.initializeFromConfig(config);
            
            if (config instanceof FileBasedSecurityServiceConfig) {
                FileBasedSecurityServiceConfig fileConfig = 
                    (FileBasedSecurityServiceConfig) config;
                if (fileConfig.getCheckInterval()>0) {
                    File file = new File(fileConfig.getFileName());
                    if (file.isAbsolute()==false) 
                        file = new File(new File(getUserGroupRoot(), name), file.getPath());
                    if (file.canRead()==false) {
                        throw new IOException("Cannot read file: "+file.getCanonicalPath());
                    }
                    UserGroupFileWatcher watcher = new 
                        UserGroupFileWatcher(file.getCanonicalPath(),service,file.lastModified());
                    watcher.setDelay(fileConfig.getCheckInterval());
                    service.registerUserGroupLoadedListener(watcher);
                    watcher.start();
                }
            }
            
            return service;
        }

        /**
         * loads the named user group service config from persistence
         */
        public SecurityNamedServiceConfig loadConfig(String name) throws IOException {
            File dir = new File(getUserGroupRoot(), name);
            if (!dir.exists()) {
                return null;
            }

            XStreamPersister xp = persister();
            return (SecurityNamedServiceConfig) loadConfigFile(dir, xp);
        }

        /**
         * saves the user group service config to persistence
         */
        public void saveConfig(SecurityNamedServiceConfig config) throws IOException {
            File dir = new File(getUserGroupRoot(), config.getName());
            dir.mkdir();

            saveConfigFile(config, dir, persister());
        }

        /**
         * removes the user group service config from persistence
         */
        public void removeConfig(String name) throws IOException {
            FileUtils.deleteDirectory(new File(getUserGroupRoot(), name));
        }
    }

    class RoleServiceHelper {
        
            /**
         * Loads the role service for the named config from persistence.
         */
        public GeoserverGrantedAuthorityService load(String name) throws IOException {
            
            SecurityNamedServiceConfig config = loadConfig(name);
            if (config == null) {
                //no such config
                return null;
            }

            //look up the service for this config
            GeoserverGrantedAuthorityService service = null;

            for (GeoServerSecurityProvider p  : lookupSecurityProviders()) {
                if (p.getRoleServiceClass() == null) {
                    continue;
                }
                if (p.getRoleServiceClass().getName().equals(config.getClassName())) {
                    service = p.createRoleService(config);
                    break;
                }
            }

            if (service == null) {
                throw new IOException("No authority service matching config: " + config);
            }
            service.setSecurityManager(GeoServerSecurityManager.this);

            //TODO: we should probably create a new instance of the service config... or mandate
            // that authority service beans be prototype beans and look them up every time
            if (!config.isStateless()) {
                service = new LockingGrantedAuthorityService(service);
            }
            service.setName(name);

            //TODO: do we need this anymore?
            service.initializeFromConfig(config);

            if (config instanceof FileBasedSecurityServiceConfig) {
                FileBasedSecurityServiceConfig fileConfig = 
                    (FileBasedSecurityServiceConfig) config;
                if (fileConfig.getCheckInterval()>0) {
                    File file = new File(fileConfig.getFileName());
                    if (file.isAbsolute()==false) 
                        file = new File(new File(getRoleRoot(), name), file.getPath());
                    if (file.canRead()==false) {
                        throw new IOException("Cannot read file: "+file.getCanonicalPath());
                    }
                    GrantedAuthorityFileWatcher watcher = new 
                        GrantedAuthorityFileWatcher(file.getCanonicalPath(),service,file.lastModified());
                    watcher.setDelay(fileConfig.getCheckInterval());
                    service.registerGrantedAuthorityLoadedListener(watcher);
                    watcher.start();
                }
            }

            return service;
        }

        /**
         * loads the named authority service config from persistence
         */
        public SecurityNamedServiceConfig loadConfig(String name) throws IOException {
            File dir = new File(getRoleRoot(), name);
            if (!dir.exists()) {
                return null;
            }

            XStreamPersister xp = persister();
            return (SecurityNamedServiceConfig) loadConfigFile(dir, xp);
        }

        /**
         * saves the authority service config to persistence
         */
        public void saveConfig(SecurityNamedServiceConfig config) throws IOException {
            File dir = new File(getRoleRoot(), config.getName());
            dir.mkdir();
            saveConfigFile(config, dir, persister());
        }

        /**
         * removes the authority service config from persistence
         */
        public void removeConfig(String name) throws IOException {
            FileUtils.deleteDirectory(new File(getRoleRoot(), name));
        }

//        /**
//         * looks up all available authority services.
//         */
//        public List<GeoserverGrantedAuthorityService> lookupAuthorityServices() throws IOException {
//            for (GeoServerSecurityProvider p : lookupSecurityProviders()) {
//                GeoserverGrantedAuthorityService roleService = p.createRoleService(config)
//            }
//            List<GeoserverGrantedAuthorityService> list = new ArrayList(
//                GeoServerExtensions.extensions(GeoserverGrantedAuthorityService.class, appContext));
//            list.add(new XMLGrantedAuthorityService());
//            return list;
//        }
    }
}