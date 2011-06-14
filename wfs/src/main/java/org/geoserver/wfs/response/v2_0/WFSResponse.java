/* Copyright (c) 2001 - 2011 TOPP - www.openplans.org. All rights reserved.
 * This code is licensed under the GPL 2.0 license, available at the root
 * application directory.
 */
package org.geoserver.wfs.response.v2_0;

import static org.geoserver.ows.util.ResponseUtils.buildSchemaURL;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.util.Set;

import net.opengis.wfs20.TransactionType;

import org.eclipse.emf.ecore.EObject;
import org.geoserver.config.GeoServer;
import org.geoserver.platform.Operation;
import org.geoserver.platform.ServiceException;
import org.geotools.wfs.v2_0.WFS;
import org.geotools.wfs.v2_0.WFSConfiguration;
import org.geotools.xml.EMFUtils;
import org.geotools.xml.Encoder;

public abstract class WFSResponse extends org.geoserver.wfs.response.WFSResponse {

    public WFSResponse(GeoServer gs, Class binding, Set<String> outputFormats) {
        super(gs, binding, outputFormats);
    }

    public WFSResponse(GeoServer gs, Class binding, String outputFormat) {
        super(gs, binding, outputFormat);
    }

    public WFSResponse(GeoServer gs, Class binding) {
        super(gs, binding);
    }

    @Override
    public String getMimeType(Object value, Operation operation) throws ServiceException {
        return "text/xml";
    }

    @Override
    public void write(Object value, OutputStream output, Operation operation) throws IOException,
            ServiceException {

        Encoder encoder = new Encoder(new WFSConfiguration());
        encoder.setEncoding(Charset.forName( getInfo().getGeoServer().getGlobal().getCharset()) );

        String baseURL = (String) EMFUtils.get((EObject)operation.getParameters()[0], "baseUrl");
        
        encoder.setSchemaLocation(WFS.NAMESPACE, buildSchemaURL(baseURL, "wfs/2.0.0/wfs.xsd"));
        encode(encoder, value, output);
    }

    protected abstract void encode(Encoder encoder, Object value, OutputStream output) 
        throws IOException, ServiceException;
}