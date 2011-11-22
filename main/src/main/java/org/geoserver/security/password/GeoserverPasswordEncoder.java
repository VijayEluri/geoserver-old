/* Copyright (c) 2001 - 2011 TOPP - www.openplans.org.  All rights reserved.
 * This code is licensed under the GPL 2.0 license, availible at the root
 * application directory.
 */


package org.geoserver.security.password;

import org.springframework.security.authentication.encoding.PasswordEncoder;

/**
 * General  Geoserver password encoding interface
 * 
 * @author christian
 *
 */
public interface GeoserverPasswordEncoder extends PasswordEncoder {

    public final static String PREFIX_DELIMTER=":";
    
    /**
     * @return the {@link PasswordEncodingType} 
     */
    public PasswordEncodingType getEncodingType();
    
    
    /**
     * @param encPass
     * @return true if this encoder has encoded encPass
     */
    public boolean isResponsibleForEncoding(String encPass);

    /**
     * decodes an encoded password. Only supported for
     * {@link PasswordEncodingType#ENCRYPT} and {@link PasswordEncodingType#PLAIN}
     * encoders 
     * 
     * @param encPass
     * @return
     * @throws UnsupportedOperationException
     */
    public String decode(String encPass) throws UnsupportedOperationException;
    
    
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