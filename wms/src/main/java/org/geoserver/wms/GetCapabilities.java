package org.geoserver.wms;

import static org.geoserver.ows.util.ResponseUtils.baseURL;

import java.nio.charset.Charset;
import java.util.Set;

import org.geoserver.platform.ServiceException;
import org.geoserver.wms.request.GetCapabilitiesRequest;
import org.geoserver.wms.response.GetCapabilitiesTransformer;

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
     */
    public GetCapabilitiesTransformer run(final GetCapabilitiesRequest request)
            throws ServiceException {

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
        GetCapabilitiesTransformer transformer;
        String baseUrl = baseURL(request.getHttpRequest());
        transformer = new GetCapabilitiesTransformer(wms, baseUrl, mapFormats, legendFormats);

        // if (request.getWFS().getGeoServer().isVerbose()) {
        transformer.setIndentation(2);
        final Charset encoding = wms.getCharSet();
        transformer.setEncoding(encoding);

        return transformer;
    }

}
