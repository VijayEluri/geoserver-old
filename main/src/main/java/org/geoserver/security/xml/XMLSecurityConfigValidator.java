/* Copyright (c) 2001 - 2007 TOPP - www.openplans.org. All rights reserved.
 * This code is licensed under the GPL 2.0 license, availible at the root
 * application directory.
 */

package org.geoserver.security.xml;

import java.io.File;
import java.io.IOException;

import org.geoserver.security.GeoserverUserGroupService;
import org.geoserver.security.config.SecurityRoleServiceConfig;
import org.geoserver.security.config.SecurityUserGroupServiceConfig;
import org.geoserver.security.validation.SecurityConfigException;
import org.geoserver.security.validation.SecurityConfigValidationErrors;
import org.geoserver.security.validation.SecurityConfigValidator;


/**
 * Validator for the XML implementation
 * 
 * @author christian
 *
 */
public class XMLSecurityConfigValidator extends SecurityConfigValidator {

    @Override
    public void validate(SecurityRoleServiceConfig config) throws SecurityConfigException {
        super.validate(config);
        XMLRoleServiceConfig xmlConfig = (XMLRoleServiceConfig) config;
        validateCheckIntervall(xmlConfig.getCheckInterval());
        validateFileName(xmlConfig.getFileName());
        
    }
    
    @Override
    public void validate(SecurityUserGroupServiceConfig config)
            throws SecurityConfigException {
        super.validate(config);
        XMLUserGroupServiceConfig xmlConfig = (XMLUserGroupServiceConfig) config;
        validateCheckIntervall(xmlConfig.getCheckInterval());
        validateFileName(xmlConfig.getFileName());
        
    }
    
    protected void validateFileName(String fileName) throws SecurityConfigException {
        if (isNotEmpty(fileName)==false)
            throw createSecurityException(XMLSecurityConfigValidationErrors.SEC_ERR_104);
    }
    
    protected void validateCheckIntervall(long msecs) throws SecurityConfigException {
        if (msecs !=0 && msecs < 1000)
            throw createSecurityException(XMLSecurityConfigValidationErrors.SEC_ERR_100);
    }
    

    @Override
    protected SecurityConfigValidationErrors getSecurityErrors() {
        return new XMLSecurityConfigValidationErrors();
    }
    
    /**
     * Additional Validation. Removing this configuration may also remove the file
     * where the roles are contained. (the file may be stored within the configuration
     * sub directory). The design insists on an empty role file.  
     * 
     */
    @Override
    public void validateRemoveRoleService(SecurityRoleServiceConfig config)
            throws SecurityConfigException {
        super.validateRemoveRoleService(config);
        
        XMLRoleServiceConfig xmlConfig = (XMLRoleServiceConfig) config;
        File file = new File(xmlConfig.getFileName());                
        // check if if file name is absolute and not in standard role directory
        try {
            
            if (file.isAbsolute() && 
                file.getCanonicalPath().startsWith(
                        new File(manager.getRoleRoot(),config.getName()).getCanonicalPath()+File.separator)==false)
                return;
            // file in security sub dir, check if roles exists
            if (manager.loadRoleService(config.getName()).getRoles().size()>0) {
                throw createSecurityException(XMLSecurityConfigValidationErrors.SEC_ERR_102, config.getName());
            }
            
        } catch (IOException e) {
            throw new RuntimeException();
        }
        
    }

    /**
     * Additional Validation. Removing this configuration may also remove the file
     * where the users and groups are contained. (the file may be stored within the configuration
     * sub directory). The design insists on an empty user/group file.  
     * 
     */

    @Override
    public void validateRemoveUserGroupService(SecurityUserGroupServiceConfig config)
            throws SecurityConfigException {
        XMLUserGroupServiceConfig xmlConfig = (XMLUserGroupServiceConfig) config;
        File file = new File(xmlConfig.getFileName());                
        // check if if file name is absolute and not in standard role directory
        try {
            
            if (file.isAbsolute() && 
                file.getCanonicalPath().startsWith(
                        new File(manager.getUserGroupRoot(),config.getName()).getCanonicalPath()+File.separator)==false)
                return;
            // file in security sub dir, check if roles exists
            GeoserverUserGroupService service = manager.loadUserGroupService(config.getName()); 
            if (service.getUserGroups().size()>0 || service.getUsers().size()>0) {
                throw createSecurityException(XMLSecurityConfigValidationErrors.SEC_ERR_103, config.getName());
            }
            
        } catch (IOException e) {
            throw new RuntimeException();
        }        
        super.validateRemoveUserGroupService(config);
    }

    /** 
     * Additional validation, check if the file exists or can be created 
     */
    @Override
    public void validateAddRoleService(SecurityRoleServiceConfig config)
            throws SecurityConfigException {
        super.validateAddRoleService(config);
        XMLRoleServiceConfig xmlConfig = (XMLRoleServiceConfig) config;
        File file  = new File(xmlConfig.getFileName());
        checkFile(file);        
    }
 
    /** 
     * Additional validation, check if the file exists or can be created 
     */

    @Override
    public void validateAddUserGroupService(SecurityUserGroupServiceConfig config)
            throws SecurityConfigException {
        super.validateAddUserGroupService(config);
        XMLUserGroupServiceConfig xmlConfig = 
                (XMLUserGroupServiceConfig) config;
        File file  = new File(xmlConfig.getFileName());
        checkFile(file);        
    }

    protected File getTempDir() {
        String tempPath = System.getProperty("java.io.tmpdir");
        if (tempPath==null)
            return null;
        File tempDir = new File(tempPath);
        if (tempDir.exists()==false) return null;
        if (tempDir.isDirectory()==false) return null;
        if (tempDir.canWrite()==false) return null;
        return tempDir;
    }
    
    protected void checkFile(File file) throws SecurityConfigException {
        File testFile = null;
        try {            
            if (file.isAbsolute()) {
                testFile=file;
            } else {
                File tempDir = getTempDir();
                if (tempDir==null) return; // cannot check relative file name
                testFile=new File(tempDir,file.getPath());
            }
         
            if (testFile.exists()==false) {
                testFile.createNewFile();
                testFile.delete();
            }
        } catch (IOException ex) {
            throw createSecurityException(XMLSecurityConfigValidationErrors.SEC_ERR_101,
                    file.getPath());
        }
    }
    
    @Override
    public void validateModifiedRoleService(SecurityRoleServiceConfig config,
            SecurityRoleServiceConfig oldConfig) throws SecurityConfigException {
        super.validateModifiedRoleService(config, oldConfig);
        XMLRoleServiceConfig old = (XMLRoleServiceConfig) oldConfig;
        XMLRoleServiceConfig modified = (XMLRoleServiceConfig) config;
        
        if (old.getFileName().equals(
                modified.getFileName()) == false) 
                throw createSecurityException(XMLSecurityConfigValidationErrors.SEC_ERR_105,
                        old.getFileName(),modified.getFileName());
    }
    @Override
    public void validateModifiedUserGroupService(SecurityUserGroupServiceConfig config,
            SecurityUserGroupServiceConfig oldConfig) throws SecurityConfigException {
        super.validateModifiedUserGroupService(config, oldConfig);
        XMLUserGroupServiceConfig old = (XMLUserGroupServiceConfig) oldConfig;
        XMLUserGroupServiceConfig modified = (XMLUserGroupServiceConfig) config;
        
        if (old.getFileName().equals(
                modified.getFileName()) == false) 
                throw createSecurityException(XMLSecurityConfigValidationErrors.SEC_ERR_105,
                        old.getFileName(),modified.getFileName());

    }
}
