/* Copyright (c) 2001 - 2011 TOPP - www.openplans.org. All rights reserved.
 * This code is licensed under the GPL 2.0 license, availible at the root
 * application directory.
 */
package org.geoserver.security.impl;

import java.util.logging.Logger;

import org.geoserver.security.GeoServerSecurityManager;
import org.geoserver.security.GeoServerSecurityService;
import org.geoserver.security.GeoserverUserDetailsService;

/**
 * Common base class for user group and role services.
 * 
 * @author Justin Deoliveira, OpenGeo
 */
public abstract class AbstractGeoServerSecurityService implements GeoServerSecurityService {

    public static String DEFAULT_NAME = "default";

    /** logger */
    protected static Logger LOGGER = 
        org.geotools.util.logging.Logging.getLogger("org.geoserver.security");

    protected String name;
    protected GeoServerSecurityManager securityManager;

    protected AbstractGeoServerSecurityService() {
    }

    protected AbstractGeoServerSecurityService(String name) {
        setName(name);
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public void setName(String name) {
        this.name = name;
    }

    @Override
    public GeoServerSecurityManager getSecurityManager() {
        return securityManager;
    }

    @Override
    public void setSecurityManager(GeoServerSecurityManager securityManager) {
        this.securityManager = securityManager;
    }
    
    public GeoserverUserDetailsService getUserDetails() {
        GeoServerSecurityManager securityManager = getSecurityManager();
        return securityManager != null ? securityManager.getUserDetails() : null;
    }

    @Override
    public boolean canCreateStore() {
        return false;
    }

}
