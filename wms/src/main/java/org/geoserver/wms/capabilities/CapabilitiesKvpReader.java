/* Copyright (c) 2001 - 2007 TOPP - www.openplans.org.  All rights reserved.
 * This code is licensed under the GPL 2.0 license, availible at the root
 * application directory.
 */
package org.geoserver.wms.capabilities;

import java.util.Map;

import org.geoserver.ows.KvpRequestReader;
import org.geoserver.wms.GetCapabilitiesRequest;
import org.geoserver.wms.WMS;
import org.geotools.util.Version;

/**
 * This utility reads in a GetCapabilities KVP request and turns it into an appropriate internal
 * CapabilitiesRequest object, upon request.
 * 
 * @author Rob Hranac, TOPP
 * @author Gabriel Roldan
 * @version $Id$
 */
public class CapabilitiesKvpReader extends KvpRequestReader {

    private WMS wms;

    public CapabilitiesKvpReader(WMS wms) {
        super(GetCapabilitiesRequest.class);
        this.wms = wms;
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    @Override
    public GetCapabilitiesRequest read(Object req, Map kvp, Map rawKvp) throws Exception {
        GetCapabilitiesRequest request = (GetCapabilitiesRequest) super.read(req, kvp, rawKvp);
        request.setRawKvp(rawKvp);

        String version = request.getVersion();
        if (null == version || version.length() == 0) {
            version = (String) rawKvp.get("WMTVER");
        }
        
        // version negotation
        Version requestedVersion = WMS.version(version);
        Version negotiatedVersion = wms.negotiateVersion(requestedVersion);
        request.setVersion(negotiatedVersion.toString());
        
        return request;
    }
}
