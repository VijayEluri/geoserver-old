/* Copyright (c) 2001 - 2011 TOPP - www.openplans.org.  All rights reserved.
 * This code is licensed under the GPL 2.0 license, availible at the root
 * application directory.
 */
package org.geoserver.security.password;

import java.io.IOException;

import org.geoserver.security.GeoServerUserGroupService;
import org.jasypt.spring.security3.PasswordEncoder;
import org.springframework.beans.factory.BeanNameAware;

/**
 * {@link PasswordEncoder} implementations useable for 
 * {@link GeoServerUserGroupService} objects
 * 
 * @author christian
 *
 */
public interface GeoServerUserPasswordEncoder extends GeoServerPasswordEncoder, BeanNameAware {
    
    /**
     * Initialize this encoder for a {@link GeoServerUserGroupService} object.
     * 
     * @param service
     * @throws IOExcpetion
     */
    void initializeFor(GeoServerUserGroupService service) throws IOException;
}