/* Copyright (c) 2001 - 2007 TOPP - www.openplans.org.  All rights reserved.
 * This code is licensed under the GPL 2.0 license, availible at the root
 * application directory.
 */
package org.geoserver.kml;

import java.io.IOException;
import java.nio.charset.Charset;
import java.util.Map;

import org.geoserver.platform.ServiceException;
import org.geoserver.wms.GetMapRequest;
import org.geoserver.wms.WMS;
import org.geoserver.wms.WMSMapContext;
import org.geoserver.wms.map.AbstractMapOutputFormat;
import org.geoserver.wms.map.XMLTransformerMap;

/**
 * 
 */
public class NetworkLinkMapOutputFormat extends AbstractMapOutputFormat {
    /**
     * Official KMZ mime type, tweaked to output NetworkLink
     */
    static final String KML_MIME_TYPE = KMLMapOutputFormat.MIME_TYPE + ";mode=networklink";

    static final String KMZ_MIME_TYPE = KMZMapOutputFormat.MIME_TYPE + ";mode=networklink";

    public static final String[] OUTPUT_FORMATS = { KML_MIME_TYPE, KMZ_MIME_TYPE };

    private WMS wms;

    public NetworkLinkMapOutputFormat(WMS wms) {
        super(KML_MIME_TYPE, OUTPUT_FORMATS);
        this.wms = wms;
    }

    /**
     * Initializes the KML encoder. None of the map production is done here, it is done in
     * writeTo(). This way the output can be streamed directly to the output response and not
     * written to disk first, then loaded in and then sent to the response.
     * 
     * @param mapContext
     *            WMSMapContext describing what layers, styles, area of interest etc are to be used
     *            when producing the map.
     * @see org.geoserver.wms.GetMapOutputFormat#produceMap(org.geoserver.wms.WMSMapContext)
     */
    @SuppressWarnings("rawtypes")
    public XMLTransformerMap produceMap(WMSMapContext mapContext) throws ServiceException,
            IOException {
        KMLNetworkLinkTransformer transformer = new KMLNetworkLinkTransformer(wms);
        transformer.setIndentation(3);
        Charset encoding = wms.getCharSet();
        transformer.setEncoding(encoding);
        Map fo = mapContext.getRequest().getFormatOptions();
        Boolean superoverlay = (Boolean) fo.get("superoverlay");
        if (superoverlay == null) {
            superoverlay = Boolean.FALSE;
        }
        transformer.setEncodeAsRegion(superoverlay);
        GetMapRequest request = mapContext.getRequest();
        boolean cachedMode = "cached".equals(KMLUtils.getSuperoverlayMode(request, wms));
        transformer.setCachedMode(cachedMode);

        String mimeType = request.getFormat();
        XMLTransformerMap wmsResponse = new XMLTransformerMap(mapContext, transformer, mapContext,
                mimeType);
        return wmsResponse;
    }
}
