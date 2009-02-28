/* Copyright (c) 2001 - 2007 TOPP - www.openplans.org. All rights reserved.
 * This code is licensed under the GPL 2.0 license, availible at the root
 * application directory.
 */
package org.geoserver.template;

import freemarker.cache.ClassTemplateLoader;
import freemarker.cache.FileTemplateLoader;
import freemarker.cache.TemplateLoader;
import freemarker.template.Configuration;

import org.opengis.feature.simple.SimpleFeatureType;
import org.geoserver.catalog.Catalog;
import org.geoserver.catalog.NamespaceInfo;
import org.geoserver.platform.GeoServerExtensions;
import org.vfny.geoserver.global.GeoserverDataDirectory;
import java.io.File;
import java.io.IOException;
import java.io.Reader;
import java.util.NoSuchElementException;
import java.util.logging.Logger;


/**
 * A freemarker template loader which can load templates from locations under
 * a GeoServer data directory.
 * <p>
 * To use this template loader, use the {@link Configuration#setTemplateLoader(TemplateLoader)}
 * method:
 * <pre>
 *         <code>
 *  Configuration cfg = new Configuration();
 *  cfg.setTemplateLoader( new GeoServerTemplateLoader() );
 *  ...
 *  Template template = cfg.getTemplate( "foo.ftl" );
 *  ...
 *         </code>
 * </pre>
 * </p>
 * <p>
 * In {@link #findTemplateSource(String)}, the following lookup heuristic is
 * applied to locate a file based on the given path.
 * <ol>
 *  <li>The path relative to '<data_dir>/featureTypes/[featureType]'
 *          given that a feature ( {@link #setFeatureType(String)} ) has been set
 *  <li>The path relative to '<data_dir>/featureTypes'
 *  <li>The path relative to '<data_dir>/templates'
 *  <li>The path relative to the calling class with {@link Class#getResource(String)}.
 * </ol>
 * <b>Note:</b> If method 5 succeeds, the resulting template will be copied to
 * the 'templates' directory of the data directory.
 * </p>
 *
 * @author Justin Deoliveira, The Open Planning Project, jdeolive@openplans.org
 *
 */
public class GeoServerTemplateLoader implements TemplateLoader {
    /** logger */
    static Logger LOGGER = org.geotools.util.logging.Logging.getLogger("org.geoserver.template");

    /**
     * Delegate file based template loader
     */
    FileTemplateLoader fileTemplateLoader;

    /**
     * Delegate class based template loader, may be null depending on how
     */
    ClassTemplateLoader classTemplateLoader;

    /**
     * Feature type directory to load template against. Its presence is mutually
     * exclusive with coverageName
     */
    SimpleFeatureType featureType;

    /**
     * Coverage info directory to load template against. Its presence is mutually
     * exclusive with featureTypeInfo
     */
    private String coverageName;

    /**
     * Reference to the GeoServer catalog so we can look up the prefix for a namespace.
     */
    private Catalog catalog;

    /**
     * Constructs the template loader.
     *
     * @param caller The "calling" class, used to look up templates based with
     * {@link Class#getResource(String)}, may be <code>null</code>
     *
     * @throws IOException
     */
    public GeoServerTemplateLoader(Class caller) throws IOException {
        //create a file template loader to delegate to
        fileTemplateLoader = new FileTemplateLoader(GeoserverDataDirectory.getGeoserverDataDirectory());

        //grab the catalog and store a reference
        catalog = (Catalog)GeoServerExtensions.bean("catalog");

        //create a class template loader to delegate to
        if (caller != null) {
            classTemplateLoader = new ClassTemplateLoader(caller, "");
        }
    }

    /**
     * Sets the feature type in which templates are loaded against.
     * <p>
     *
     * </p>
     * @param featureType
     */
    public void setFeatureType(SimpleFeatureType featureType) {
        this.featureType = featureType;
    }

    /**
     * Sets the coverage info
     * 
     * @param coverageName
     */
    public void setCoverageName(String coverageName){
        this.coverageName = coverageName;
    }
    
    public Object findTemplateSource(String path) throws IOException {
        File template = null;

        
        //first check relative to set feature type
        String baseDirName;
        try {
            final String dirName;
            if (featureType != null) {
                baseDirName = "featureTypes";
                dirName = GeoserverDataDirectory.findFeatureTypeDirName(featureType);
            } else if (coverageName != null) {
                baseDirName = "coverages";
                dirName = GeoserverDataDirectory.findCoverageDirName(coverageName);
            } else {
                baseDirName = "featureTypes";
                dirName = "";
            }
            
            template = (File) fileTemplateLoader.findTemplateSource(baseDirName + File.separator
                    + dirName + File.separator + path);

            if (template != null) {
                return template;
            }

            if (featureType != null) {
                NamespaceInfo nsInfo = null;
                if ( featureType.getName().getNamespaceURI() != null ) {
                    nsInfo = catalog.getNamespaceByURI(featureType.getName().getNamespaceURI());
                }
                // the feature type might not be registered, it may come from WMS feature portrayal, be a 
                // remote one
                if(nsInfo != null) {
                    //try looking up the template in the default location for the particular namespaces
                    // under templates/<namespace>
                    template = (File) fileTemplateLoader.findTemplateSource(
                            "templates" + File.separator + nsInfo.getPrefix() + File.separator + path
                            );
                }
            }

            if (template != null) return template;

            // next, try relative to featureTypes or coverages directory, as appropriate
            template = (File) fileTemplateLoader.findTemplateSource(baseDirName + File.separator
                    + path);

            if (template != null) {
                return template;
            }

        } catch(NoSuchElementException e) {
            // this one is thrown if the feature type is not found, and happens whenever
            // the feature type is a remote one
            // No problem, we just go on, there won't be any specific template for it
        }
    

        //next, check the templates directory
        template = (File) fileTemplateLoader.findTemplateSource("templates" + File.separator + path);

        if (template != null) {
            return template;
        }

        //final effort to use a class resource
        if (classTemplateLoader != null) {
            Object source = classTemplateLoader.findTemplateSource(path);

            //wrap the source in a source that maintains the orignial path
            if (source != null) {
                return new ClassTemplateSource(path, source);
            }
        }

        return null;
    }

    public long getLastModified(Object source) {
        if (source instanceof File) {
            //loaded from file
            return fileTemplateLoader.getLastModified(source);
        } else {
            //loaded from class
            ClassTemplateSource wrapper = (ClassTemplateSource) source;

            return classTemplateLoader.getLastModified(wrapper.source);
        }
    }

    public Reader getReader(Object source, String encoding)
        throws IOException {
        if (source instanceof File) {
            // loaded from file
            return fileTemplateLoader.getReader(source, encoding);
        } else {
            // get teh resource for the raw source as use it right away
            ClassTemplateSource wrapper = (ClassTemplateSource) source;

            return classTemplateLoader.getReader(wrapper.source, encoding);
        }
    }

    public void closeTemplateSource(Object source) throws IOException {
        if (source instanceof File) {
            fileTemplateLoader.closeTemplateSource(source);
        } else {
            ClassTemplateSource wrapper = (ClassTemplateSource) source;

            //close the raw source
            classTemplateLoader.closeTemplateSource(wrapper.source);

            //cleanup
            wrapper.path = null;
            wrapper.source = null;
        }
    }

    /**
     * Template source for use when a template is loaded from a class.
     * <p>
     * Used to store the intial path so the template can be copied to the data
     * directory.
     * </p>
     * @author Justin Deoliveira, The Open Planning Project, jdeolive@openplans.org
     *
     */
    static class ClassTemplateSource {
        /**
         * The path used to load the template.
         */
        String path;

        /**
         * The raw source from the class template loader
         */
        Object source;

        public ClassTemplateSource(String path, Object source) {
            this.path = path;
            this.source = source;
        }
    }
}
