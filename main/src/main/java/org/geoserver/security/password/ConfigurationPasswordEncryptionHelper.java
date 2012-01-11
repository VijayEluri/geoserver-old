/* Copyright (c) 2001 - 2011 TOPP - www.openplans.org.  All rights reserved.
 * This code is licensed under the GPL 2.0 license, availible at the root
 * application directory.
 */
package org.geoserver.security.password;

import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.geoserver.catalog.DataStoreInfo;
import org.geoserver.catalog.StoreInfo;
import org.geoserver.security.GeoServerSecurityManager;
import org.geotools.data.DataAccessFactory;
import org.geotools.data.DataAccessFactory.Param;
import org.geotools.util.logging.Logging;

/**
 * Helper class for encryption of passwords in connection parameters for {@link StoreInfo} objects.
 * <p>
 * This class will encrypt any password parameter from {@link StoreInfo#getConnectionParameters()}. 
 * </p> 
 * 
 * @author christian
 */
public class ConfigurationPasswordEncryptionHelper {

    static protected Logger LOGGER = Logging.getLogger("org.geoserver.security");

    /**
     * cache of datastore factory class to fields to encrypt
     */
    static protected Map<Class<? extends DataAccessFactory>, Set<String>> CACHE = 
            new HashMap<Class<? extends DataAccessFactory>, Set<String>>();

    GeoServerSecurityManager securityManager;

    public ConfigurationPasswordEncryptionHelper(GeoServerSecurityManager securityManager) {
        this.securityManager = securityManager;
    }

    /**
     * Determines the fields in {@link StoreInfo#getConnectionParameters()} that require encryption
     * for this type of store object.
     */
    public Set<String> getEncryptedFields(StoreInfo info) {
        if (!(info instanceof DataStoreInfo)) {
            //only datastores supposed at this time, TODO: fix this
            return Collections.emptySet();
        }

        //find this store object data access factory
        DataAccessFactory factory;
        try {
            factory = securityManager.getCatalog().getResourcePool().getDataStoreFactory((DataStoreInfo) info);
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "Error looking up factory for store : " + info + ". Unable to " +
                "encrypt connection parameters.", e);
            return Collections.emptySet();
        }

        if (factory == null) {
            LOGGER.warning("Could not find factory for store : " + info + ". Unable to encrypt " +
                "connection parameters.");
            return Collections.emptySet();
        }

        Set<String> toEncrypt = CACHE.get(factory.getClass());
        if (toEncrypt!=null) {
            return toEncrypt;
        }

        synchronized (CACHE) {
            toEncrypt = CACHE.get(info.getClass());
            if (toEncrypt!=null) {
                return toEncrypt;
            }
            
            toEncrypt = Collections.emptySet();
            if (info != null && info.getConnectionParameters() != null) {
                toEncrypt = new HashSet<String>(3);
                for (Param p : factory.getParametersInfo()) {
                    if (p.isPassword()) {
                        toEncrypt.add(p.getName());
                    }
                }
            }
            CACHE.put(factory.getClass(), toEncrypt);
        }
        return toEncrypt;
    }

    /**
     * Encrypts a parameter value.
     * <p>
     * If no encoder is configured then the value is returned as is.
     * </p>
     */
    public String encode(String value) {
        String encoderName = securityManager.getConfigPasswordEncrypterName();
        if (encoderName != null) {
            GeoServerPasswordEncoder pwEncoder = securityManager.loadPasswordEncoder(encoderName);
            if (pwEncoder != null) {
                String prefix = pwEncoder.getPrefix(); 
                if (value.startsWith(prefix+GeoServerPasswordEncoder.PREFIX_DELIMTER)) {
                    throw new RuntimeException("Cannot encode a password with prefix: "+
                        prefix+GeoServerPasswordEncoder.PREFIX_DELIMTER);
                }
                value = pwEncoder.encodePassword(value, null);
            }
        }
        return value;
    }

    /**
     * Decrypts previously encrypted store connection parameters.
     */
    public void decode(StoreInfo info) {
        List<GeoServerPBEPasswordEncoder> encoders =
            securityManager.loadPasswordEncoders(GeoServerPBEPasswordEncoder.class);

        Set<String> encryptedFields = getEncryptedFields(info);
        if (info.getConnectionParameters() !=null) {
            for (String key : info.getConnectionParameters().keySet()) {
                if (encryptedFields.contains(key)) {
                    String value = (String)info.getConnectionParameters().get(key);
                    if (value!=null) {
                        info.getConnectionParameters().put(key, decode(value, encoders));
                    }
                }
            }
        }
    }

    /**
     * Decrypts a previously encrypted value.
     */
    public String decode(String value) {
        return decode(value, 
            securityManager.loadPasswordEncoders(GeoServerPBEPasswordEncoder.class));
    }

    String decode(String value, List<GeoServerPBEPasswordEncoder> encoders) {
        for (GeoServerPBEPasswordEncoder encoder : encoders) {
            if (encoder.isResponsibleForEncoding(value)) {
                return encoder.decode(value);
            }
        }
        return value;
    }
}