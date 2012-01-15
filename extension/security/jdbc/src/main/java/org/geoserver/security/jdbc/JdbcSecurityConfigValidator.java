/* Copyright (c) 2001 - 2007 TOPP - www.openplans.org. All rights reserved.
 * This code is licensed under the GPL 2.0 license, availible at the root
 * application directory.
 */

package org.geoserver.security.jdbc;

import org.geoserver.security.config.SecurityAuthProviderConfig;
import org.geoserver.security.config.SecurityRoleServiceConfig;
import org.geoserver.security.config.SecurityUserGroupServiceConfig;
import org.geoserver.security.jdbc.config.JDBCConnectAuthProviderConfig;
import org.geoserver.security.jdbc.config.JDBCSecurityServiceConfig;
import org.geoserver.security.validation.SecurityConfigException;
import org.geoserver.security.validation.SecurityConfigValidationErrors;
import org.geoserver.security.validation.SecurityConfigValidator;

public class JdbcSecurityConfigValidator extends SecurityConfigValidator {

    @Override
    public void validate(SecurityRoleServiceConfig config) throws SecurityConfigException {
        super.validate(config);
        JDBCSecurityServiceConfig jdbcConfig = (JDBCSecurityServiceConfig) config;
        
        validateFileNames(jdbcConfig);
        
        if (jdbcConfig.isJndi())
            validateJNDI(jdbcConfig);
        else 
            validateJDBC(jdbcConfig);
    }
    
    @Override
    public void validate(SecurityUserGroupServiceConfig config)
            throws SecurityConfigException {
        super.validate(config);
        JDBCSecurityServiceConfig jdbcConfig = (JDBCSecurityServiceConfig) config;
        if (jdbcConfig.isJndi())
            validateJNDI(jdbcConfig);
        else
            validateJDBC(jdbcConfig);
    }
    
    protected void validateFileNames(JDBCSecurityServiceConfig config) throws SecurityConfigException
    {
        
    }
    
    protected void validateJNDI(JDBCSecurityServiceConfig config) throws SecurityConfigException {
        if (isNotEmpty(config.getJndiName())==false)
            throw createSecurityException(JdbcSecurityConfigValidationErrors.SEC_ERR_210);
    }
    
    protected void validateJDBC(JDBCSecurityServiceConfig config) throws SecurityConfigException {
        if (isNotEmpty(config.getDriverClassName())==false)
            throw createSecurityException(JdbcSecurityConfigValidationErrors.SEC_ERR_200);
        if (isNotEmpty(config.getUserName())==false)
            throw createSecurityException(JdbcSecurityConfigValidationErrors.SEC_ERR_201);
        if (isNotEmpty(config.getConnectURL())==false)
            throw createSecurityException(JdbcSecurityConfigValidationErrors.SEC_ERR_202);

        try {
            Class.forName(config.getDriverClassName());
        } catch (ClassNotFoundException e) {
            throw createSecurityException(JdbcSecurityConfigValidationErrors.SEC_ERR_203,
                    config.getDriverClassName());
        }

    }

    @Override
    public void validate(SecurityAuthProviderConfig config) throws SecurityConfigException {
        super.validate(config);
        JDBCConnectAuthProviderConfig jdbcConfig = (JDBCConnectAuthProviderConfig) config;
        if (isNotEmpty(jdbcConfig.getDriverClassName())==false)
            throw createSecurityException(JdbcSecurityConfigValidationErrors.SEC_ERR_200);
        if (isNotEmpty(jdbcConfig.getConnectURL())==false)
            throw createSecurityException(JdbcSecurityConfigValidationErrors.SEC_ERR_202);

        try {
            Class.forName(jdbcConfig.getDriverClassName());
        } catch (ClassNotFoundException e) {
            throw createSecurityException(JdbcSecurityConfigValidationErrors.SEC_ERR_203,
                    jdbcConfig.getDriverClassName());
        }

        
    }

    
    @Override
    protected SecurityConfigValidationErrors getSecurityErrors() {
        return new JdbcSecurityConfigValidationErrors();
    }


}
