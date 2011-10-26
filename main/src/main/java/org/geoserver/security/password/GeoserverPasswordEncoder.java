/* Copyright (c) 2001 - 2011 TOPP - www.openplans.org.  All rights reserved.
 * This code is licensed under the GPL 2.0 license, availible at the root
 * application directory.
 */


package org.geoserver.security.password;

import org.springframework.beans.factory.BeanNameAware;
import org.springframework.security.authentication.encoding.PasswordEncoder;

/**
 * General  Geoserver password encoding interface
 * 
 * @author christian
 *
 */
public interface GeoserverPasswordEncoder extends PasswordEncoder, BeanNameAware {

    public final static String PREFIX_DELIMTER=":";
    
    /**
     * @return the {@link PasswordEncoding} 
     */
    public PasswordEncoding getEncodingType();
    
    
    /**
     * @param encPass
     * @return true if this encoder has encoded encPass
     */
    public boolean isResponsibleForEncoding(String encPass);

    /**
     * decodes an encoded password. Only supported for
     * {@link PasswordEncoding#ENCRYPT} and {@link PasswordEncoding#PLAIN}
     * encoders 
     * 
     * @param encPass
     * @return
     * @throws UnsupportedOperationException
     */
    public String decode(String encPass) throws UnsupportedOperationException;
    
    public String getBeanName();
    
    /**
     * @return a prefix which is stored with the password.
     * This prefix must be unique within all {@link GeoserverPasswordEncoder}
     * implementations.
     * 
     * Reserved:
     * 
     * plain
     * digest1
     * crypt1
     * 
     * A plain text password is stored as
     * 
     * plain:password
     */
    public String getPrefix();

}
