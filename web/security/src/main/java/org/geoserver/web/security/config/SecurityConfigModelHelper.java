/* Copyright (c) 2001 - 2008 TOPP - www.openplans.org. All rights reserved.
 * This code is licensed under the GPL 2.0 license, available at the root
 * application directory.
 */


package org.geoserver.web.security.config;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;

import org.geoserver.security.config.SecurityConfig;

/**
 * 
 * 
 * 
 * @author christian
 *
 */
public  class SecurityConfigModelHelper implements Serializable{

    private static final long serialVersionUID = 1L;
    
    protected boolean isNew;
    protected byte[] serializedInitialConfig;    
    protected SecurityConfig config;
    
    public SecurityConfigModelHelper(SecurityConfig config, boolean isNew) {
        this.isNew = isNew;
        try {
            serializedInitialConfig=serializeConfg(config);
            if (isNew)
                this.config=config;
            else // use a working copy
                restoreInitialConfig();
            
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }        
    }
    
    public boolean isNew() {
        return isNew;
    }
    public SecurityConfig getConfig() {
        return config;
    }
    public void restoreInitialConfig() {
        try {
            ObjectInputStream oin = new ObjectInputStream(
                new ByteArrayInputStream(serializedInitialConfig));
            config = (SecurityConfig) oin.readObject();        
            oin.close();
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }        
    }

    protected byte[] serializeConfg(SecurityConfig config) {
        ByteArrayOutputStream bout = new ByteArrayOutputStream();
        try {
            ObjectOutputStream oout = new ObjectOutputStream(bout);
            oout.writeObject(config);
            oout.close();
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }        
        return bout.toByteArray();        
    }
    
    public boolean hasChanges() {
        byte[] bytes = serializeConfg(config);
        return ! (bytes.equals(serializedInitialConfig));
    }
}