/* Copyright (c) 2001 - 2008 TOPP - www.openplans.org. All rights reserved.
 * This code is licensed under the GPL 2.0 license, available at the root
 * application directory.
 */

package org.geoserver.security.config;

import java.util.List;

import org.geoserver.security.GeoServerSecurityFilterChain;
import org.geoserver.security.GeoserverRoleService;


/**
 * Interface for a {@link GeoserverUserDetailsService} configuration
 * 
 * @author christian
 *
 */
public interface SecurityManagerConfig  extends SecurityConfig {

    /**
     * @return the name for a 
     * {@link GeoserverRoleService} object
     * 
     */
    public String getRoleServiceName();
    /**
     * @param roleServiceName, the name of a
     * {@link GeoserverRoleService} object 
     */
    public void setRoleServiceName(String roleServiceName);
    
    /**
     * @return list of names for {@link GeoServerAuthenticationProvider} objects
     */
    public List<String> getAuthProviderNames();

    /**
     * Flag determining if anonymous authentication is active.
     */
    public Boolean isAnonymousAuth();

    /**
     * Sets flag determining if anonymous authentication is active.
     */
    public void setAnonymousAuth(Boolean anonymousAuth);

    /**
     * @return The security filter chain.
     */
    public GeoServerSecurityFilterChain getFilterChain();

    /**
     * Admin Console encrypts URL Parameters ?
     * @return
     */
    public boolean isEncryptingUrlParams();
    public void setEncryptingUrlParams(boolean encryptingUrlParams);

    /**
     * if passwords in configuration files are encrypted, the
     * Spring name of the encrypter 
     * 
     * @return
     */
    public String getConfigPasswordEncrypterName();
    public void setConfigPasswordEncrypterName(String configPasswordEncrypterName);


}
