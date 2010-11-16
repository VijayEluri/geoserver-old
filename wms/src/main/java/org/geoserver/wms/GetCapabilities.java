/* Copyright (c) 2010 TOPP - www.openplans.org.  All rights reserved.
 * This code is licensed under the GPL 2.0 license, availible at the root
 * application directory.
 */
package org.geoserver.wms;

import java.util.Set;

import org.geoserver.platform.ServiceException;
import org.geoserver.wms.capabilities.Capabilities_1_3_0_Transformer;
import org.geoserver.wms.capabilities.GetCapabilitiesTransformer;
import org.geotools.util.Version;
import org.geotools.xml.transform.TransformerBase;

/**
 * WMS GetCapabilities operation default implementation.
 * 
 * @author Gabriel Roldan
 */
public class GetCapabilities {

    private final WMS wms;

    public GetCapabilities(final WMS wms) {
        this.wms = wms;
    }

    /**
     * 
     * @param request
     *            get capabilities request, as generated by
     * @return
     * @see GetCapabilitiesTransformer
     * @see Capabilities_1_3_0_Transformer
     */
    public TransformerBase run(final GetCapabilitiesRequest request) throws ServiceException {

        final Version version = WMS.version(request.getVersion());
        if (version == null) {
            throw new IllegalArgumentException("version not supplied.");
        }
        
        // UpdateSequence handling for WMS: see WMS 1.1.1 page 23
        long reqUS = -1;
        if (request.getUpdateSequence() != null && !"".equals(request.getUpdateSequence().trim())) {
            try {
                reqUS = Long.parseLong(request.getUpdateSequence());
            } catch (NumberFormatException nfe) {
                throw new ServiceException(
                        "GeoServer only accepts numbers in the updateSequence parameter");
            }
        }
        long geoUS = wms.getUpdateSequence();
        if (reqUS > geoUS) {
            throw new ServiceException(
                    "Client supplied an updateSequence that is greater than the current sever updateSequence",
                    "InvalidUpdateSequence");
        }
        if (reqUS == geoUS) {
            throw new ServiceException("WMS capabilities document is current (updateSequence = "
                    + geoUS + ")", "CurrentUpdateSequence");
        }
        // otherwise it's a normal response...

        Set<String> legendFormats = wms.getAvailableLegendGraphicsFormats();
        Set<String> mapFormats = wms.getAvailableMapFormats();
        TransformerBase transformer;
        String baseUrl = request.getBaseUrl();
        if (WMS.VERSION_1_1_1.equals(version)) {
            transformer = new GetCapabilitiesTransformer(wms, baseUrl, mapFormats, legendFormats);
        } else if (WMS.VERSION_1_3_0.equals(version)) {
            transformer = new Capabilities_1_3_0_Transformer(wms, baseUrl, mapFormats);
        } else {
            throw new IllegalArgumentException("Unknown version: " + version);
        }

        return transformer;
    }

}
