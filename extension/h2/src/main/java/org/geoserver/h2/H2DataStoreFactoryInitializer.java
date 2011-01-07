package org.geoserver.h2;

import java.io.File;
import java.io.IOException;

import org.geoserver.data.DataStoreFactoryInitializer;
import org.geoserver.platform.GeoServerResourceLoader;
import org.geotools.data.h2.H2DataStoreFactory;

/**
 * Initializes an H2 data store factory setting its location to the geoserver
 *  data directory.
 *
 * @author Justin Deoliveira, The Open Planning Project
 *
 */
public class H2DataStoreFactoryInitializer extends 
    DataStoreFactoryInitializer<H2DataStoreFactory> {

    GeoServerResourceLoader resourceLoader;
    
    public H2DataStoreFactoryInitializer() {
        super( H2DataStoreFactory.class );
    }
    
    public void setResourceLoader(GeoServerResourceLoader resourceLoader) {
        this.resourceLoader = resourceLoader;
    }
    
    public void initialize(H2DataStoreFactory factory) {
        //create an h2 directory
        File h2;
        try {
            h2 = resourceLoader.findOrCreateDirectory("h2");
        } 
        catch (IOException e) {
            throw new RuntimeException("Unable to create h2 directory", e);
        }
        
        factory.setBaseDirectory( h2 );
    }
}
