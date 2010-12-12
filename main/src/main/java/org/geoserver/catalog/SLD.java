package org.geoserver.catalog;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.Reader;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.TransformerException;

import org.geotools.factory.CommonFactoryFinder;
import org.geotools.sld.v1_1.SLDConfiguration;
import org.geotools.styling.NamedLayer;
import org.geotools.styling.NamedStyle;
import org.geotools.styling.SLDParser;
import org.geotools.styling.SLDTransformer;
import org.geotools.styling.Style;
import org.geotools.styling.StyleFactory;
import org.geotools.styling.StyledLayer;
import org.geotools.styling.StyledLayerDescriptor;
import org.geotools.styling.UserLayer;
import org.geotools.util.Version;
import org.geotools.xml.Parser;
import org.vfny.geoserver.util.SLDValidator;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

public class SLD {

    static StyleFactory styleFactory = CommonFactoryFinder.getStyleFactory(null);
    
    public static StyledLayerDescriptor parse(Object input, Version version) throws IOException {
        return Handler.lookup(version).parse(input);
    }
    
    public static void encode(StyledLayerDescriptor sld, Version version, boolean format, 
            OutputStream output) throws IOException {
        
        Handler.lookup(version).encode(sld, format, output);
    }
    
    public static List<Exception> validate(Object input, Version version) throws IOException {
        return Handler.lookup(version).validate(input);
    }

    public static Style style(StyledLayerDescriptor sld) {
        for (int i = 0; i < sld.getStyledLayers().length; i++) {
            Style[] styles = null;
            
            if (sld.getStyledLayers()[i] instanceof NamedLayer) {
                NamedLayer layer = (NamedLayer) sld.getStyledLayers()[i];
                styles = layer.getStyles();
            }
            else if(sld.getStyledLayers()[i] instanceof UserLayer) {
                UserLayer layer = (UserLayer) sld.getStyledLayers()[i];
                styles = layer.getUserStyles();
            }
            
            if (styles != null) {
                for (int j = 0; j < styles.length; i++) {
                    if (!(styles[j] instanceof NamedStyle)) {
                        return styles[j];
                    }
                }
            }
            
        }

        return null;
    }

    public static StyledLayerDescriptor sld(Style style) {
        StyledLayerDescriptor sld = styleFactory.createStyledLayerDescriptor();
        
        NamedLayer layer = styleFactory.createNamedLayer();
        layer.setName(style.getName());
        sld.addStyledLayer(layer);
        
        layer.addStyle(style);
        
        return sld;
    }

    static Reader toReader(Object input) throws IOException {
        if (input instanceof Reader) {
            return (Reader) input;
        }
        
        if (input instanceof InputStream) {
            return new InputStreamReader((InputStream)input);
        }
        
        if (input instanceof File) {
            return new FileReader((File)input);
        }
        
        throw new IllegalArgumentException("Unable to turn " + input + " into reader");
    }
    
    public static enum Handler {
        SLD_10("1.0.0") {
            
            @Override
            public StyledLayerDescriptor parse(Object input) throws IOException {
                SLDParser p = parser(input);
                StyledLayerDescriptor sld = p.parseSLD();
                if (sld.getStyledLayers().length == 0) {
                    //most likely a style that is not a valid sld, try to actually parse out a 
                    // style and then wrap it in an sld
                    Style[] style = p.readDOM();
                    if (style.length > 0) {
                        NamedLayer l = styleFactory.createNamedLayer();
                        l.addStyle(style[0]);
                        sld.addStyledLayer(l);
                    }
                }
                
                return sld;
            }
            
            @Override
            protected List<Exception> validate(Object input) throws IOException {
                return new SLDValidator().validateSLD(new InputSource(toReader(input)), null);
            }
            
            @Override
            public void encode(StyledLayerDescriptor sld, boolean format, OutputStream output) throws IOException {
                SLDTransformer tx = new SLDTransformer();
                if (format) {
                    tx.setIndentation(2);
                }
                try {
                    tx.transform( sld, output );
                } 
                catch (TransformerException e) {
                    throw (IOException) new IOException("Error writing style").initCause(e);
                }
            }
            
            
            SLDParser parser(Object input) throws IOException {
                if (input instanceof File) {
                    return new SLDParser(styleFactory, (File) input);
                }
                else {
                    return new SLDParser(styleFactory, toReader(input));
                }
            }
        },
        
        SLD_11("1.1.0") {
            
            @Override
            public StyledLayerDescriptor parse(Object input) throws IOException {
                SLDConfiguration sld = new SLDConfiguration();
                try {
                    return (StyledLayerDescriptor) new Parser(sld).parse(toReader(input));
                } 
                catch(Exception e) {
                    if (e instanceof IOException) throw (IOException) e;
                    throw (IOException) new IOException().initCause(e);
                }
            }
            
            @Override
            protected List<Exception> validate(Object input) throws IOException {
                SLDConfiguration sld = new SLDConfiguration();
                Parser p = new Parser(sld);
                p.setValidating(true);
                
                try {
                    p.parse(toReader(input));
                    return p.getValidationErrors();
                } 
                catch(Exception e) {
                    return Collections.singletonList(e);
                }
            }

            @Override
            public void encode(StyledLayerDescriptor sld, boolean format, OutputStream output) throws IOException {
                // TODO Auto-generated method stub
            }  
        };
        
        private Version version;
        
        private Handler(String version) {
            this.version = new org.geotools.util.Version(version);
        }
        
        public Version getVersion() {
            return version;
        }

        protected abstract StyledLayerDescriptor parse(Object input) throws IOException;
        
        protected abstract void encode(StyledLayerDescriptor sld, boolean format, OutputStream output) 
            throws IOException;
        
        protected abstract List<Exception> validate(Object input) throws IOException;
        
        public static Handler lookup(Version version) {
            for (Handler h : values()) {
                if (h.getVersion().equals(version)) {
                    return h;
                }
            }
            throw new IllegalArgumentException("No support for SLD " + version);
        }
    };
}
