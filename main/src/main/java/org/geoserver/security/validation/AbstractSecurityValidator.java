/* Copyright (c) 2001 - 2008 TOPP - www.openplans.org. All rights reserved.
 * This code is licensed under the GPL 2.0 license, available at the root
 * application directory.
 */

package org.geoserver.security.validation;


import java.io.File;
import java.io.IOException;

import org.geoserver.platform.GeoServerExtensions;
import org.geoserver.security.GeoServerSecurityManager;
import org.geoserver.security.xml.XMLSecurityConfigValidationErrors;

public abstract class AbstractSecurityValidator {

    protected GeoServerSecurityManager manager;

        
    public AbstractSecurityValidator() {
        manager=GeoServerExtensions.bean(GeoServerSecurityManager.class);
    }
    
       
    protected boolean isNotEmpty(String aString) {
        return aString !=null && aString.length()> 0;
    }

    
    /**
     * Subclasses must override  
     * 
     * @return
     */
    protected abstract AbstractSecurityValidationErrors getSecurityErrors();


    /**
     * Gets the temp directory, null if not found or not
     * writable 
     * 
     * @return
     */
    public File getTempDir() {
        String tempPath = System.getProperty("java.io.tmpdir");
        if (tempPath==null)
            return null;
        File tempDir = new File(tempPath);
        if (tempDir.exists()==false) return null;
        if (tempDir.isDirectory()==false) return null;
        if (tempDir.canWrite()==false) return null;
        return tempDir;
    }


    /**
     * Checks if the file can be created. For relative file names,
     * {@link #getTempDir()} is used (if there is a temp dir)
     * 
     * returns true if file can be created or if the test
     * is not possible due to a missing temp dir
     * 
     * false if file creation causes an {@link IOException}
     * 
     * @param file
     * @return
     * @throws SecurityConfigException
     */
    protected boolean checkFile(File file) throws SecurityConfigException {
        File testFile = null;
        try {            
            if (file.isAbsolute()) {
                testFile=file;
            } else {
                File tempDir = getTempDir();
                if (tempDir==null) return true; // cannot check relative file name
                testFile=new File(tempDir,file.getPath());
            }
         
            if (testFile.exists()==false) {
                testFile.createNewFile();
                testFile.delete();
            }
            return true;
        } catch (IOException ex) {
            return false;
        }
    }
    
    
}
