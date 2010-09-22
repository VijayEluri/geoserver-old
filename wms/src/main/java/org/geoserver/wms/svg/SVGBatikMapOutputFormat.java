/* Copyright (c) 2001 - 2007 TOPP - www.openplans.org. All rights reserved.
 * This code is licensed under the GPL 2.0 license, availible at the root
 * application directory.
 */
package org.geoserver.wms.svg;

import java.awt.Dimension;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.FactoryConfigurationError;
import javax.xml.parsers.ParserConfigurationException;

import org.apache.batik.svggen.SVGGeneratorContext;
import org.apache.batik.svggen.SVGGraphics2D;
import org.geoserver.ows.Response;
import org.geoserver.platform.Operation;
import org.geoserver.platform.ServiceException;
import org.geoserver.wms.DefaultWebMapService;
import org.geoserver.wms.GetMapOutputFormat;
import org.geoserver.wms.WMS;
import org.geoserver.wms.WMSMapContext;
import org.geoserver.wms.map.MaxErrorEnforcer;
import org.geoserver.wms.map.RawMap;
import org.geoserver.wms.map.RenderExceptionStrategy;
import org.geotools.map.MapContext;
import org.geotools.renderer.lite.StreamingRenderer;
import org.vfny.geoserver.wms.WmsException;
import org.w3c.dom.Document;

import com.vividsolutions.jts.geom.Envelope;

/**
 * Renders svg using the Batik SVG Toolkit. An SVG context is created for a map and then passed of
 * to {@link org.geotools.renderer.lite.StreamingRenderer}.
 * 
 * @author Justin Deoliveira, The Open Planning Project
 * 
 */
public final class SVGBatikMapOutputFormat extends Response implements GetMapOutputFormat {

    private final WMS wms;

    public static class BatikSVGMap extends org.geoserver.wms.response.Map {
        private SVGGraphics2D graphics;

        BatikSVGMap(SVGGraphics2D graphics) {
            this.graphics = graphics;
            setMimeType(SVG.MIME_TYPE);
        }

        public SVGGraphics2D getGraphics() {
            return graphics;
        }
    }

    public SVGBatikMapOutputFormat(WMS wms) {
        super(BatikSVGMap.class, SVG.OUTPUT_FORMATS);
        this.wms = wms;
    }
    
    /**
     * @return same as {@link #getMimeType()}
     * @see org.geoserver.ows.Response#getMimeType(java.lang.Object,
     *      org.geoserver.platform.Operation)
     */
    @Override
    public String getMimeType(Object value, Operation operation) throws ServiceException {
        return getMimeType();
    }
    
    /**
     * @return {@code true} if the WMS is configured for the {@link WMS#SVG_BATIK Batik} svg
     *         strategy
     * @see org.geoserver.wms.GetMapOutputFormat#enabled()
     */
    public boolean enabled() {
        boolean enabled = SVG.canHandle(wms, WMS.SVG_BATIK);
        return enabled;
    }

    /**
     * @return {@code true} if the WMS is configured for the {@link WMS#SVG_BATIK Batik} svg
     *         strategy
     * @see org.geoserver.ows.Response#canHandle(org.geoserver.platform.Operation)
     */
    @Override
    public boolean canHandle(Operation operation) {
       return enabled();
    }

    /**
     * @return {@code ["image/svg+xml", "image/svg xml", "image/svg"]}
     * @see org.geoserver.wms.GetMapOutputFormat#getOutputFormatNames()
     */
    public Set<String> getOutputFormatNames() {
        return SVG.OUTPUT_FORMATS;
    }

    /**
     * @return {@code "image/svg+xml"}
     * @see org.geoserver.wms.GetMapOutputFormat#getMimeType()
     */
    public String getMimeType() {
        return SVG.MIME_TYPE;
    }

    /**
     * 
     * @see org.geoserver.wms.GetMapOutputFormat#produceMap(org.geoserver.wms.WMSMapContext)
     */
    public BatikSVGMap produceMap(WMSMapContext mapContext) throws ServiceException, IOException {

        StreamingRenderer renderer = setUpRenderer(mapContext);
        SVGGraphics2D g = createSVGMap(renderer, mapContext);
        renderer = null;

        // This method of output does not output the DOCTYPE definiition
        // TODO: make a config option that toggles wether doctype is
        // written out.
        // OutputFormat format = new OutputFormat();
        // XMLSerializer serializer = new XMLSerializer(new OutputStreamWriter(out, "UTF-8"),
        // format);

        return new BatikSVGMap(g);
    }

    /**
     * 
     * @see org.geoserver.ows.Response#write(java.lang.Object, java.io.OutputStream,
     *      org.geoserver.platform.Operation)
     */
    @Override
    public void write(Object value, OutputStream output, Operation operation) throws IOException,
            ServiceException {

        BatikSVGMap map = (BatikSVGMap) value;
        SVGGraphics2D graphics = map.getGraphics();
        graphics.stream(new OutputStreamWriter(output, "UTF-8"));
    }

    private StreamingRenderer setUpRenderer(WMSMapContext mapContext) {
        StreamingRenderer renderer;
        renderer = new StreamingRenderer();

        // optimized data loading was not here, but yet it seems sensible to
        // have it...
        Map<String, Object> rendererParams = new HashMap<String, Object>();
        rendererParams.put("optimizedDataLoadingEnabled", Boolean.TRUE);
        // we need the renderer to draw everything on the batik provided graphics object
        rendererParams.put(StreamingRenderer.OPTIMIZE_FTS_RENDERING_KEY, Boolean.FALSE);
        // render everything in vector form if possible
        rendererParams.put(StreamingRenderer.VECTOR_RENDERING_KEY, Boolean.TRUE);
        rendererParams.put("renderingBuffer", new Integer(mapContext.getBuffer()));
        if (DefaultWebMapService.isLineWidthOptimizationEnabled()) {
            rendererParams.put(StreamingRenderer.LINE_WIDTH_OPTIMIZATION_KEY, true);
        }
        renderer.setRendererHints(rendererParams);
        renderer.setContext(mapContext);
        return renderer;
    }

    public SVGGraphics2D createSVGMap(final StreamingRenderer renderer,
            final WMSMapContext mapContext) throws ServiceException, IOException {
        try {
            MapContext map = renderer.getContext();
            double width = -1;
            double height = -1;

            if (map instanceof WMSMapContext) {
                WMSMapContext wmsMap = (WMSMapContext) map;
                width = wmsMap.getMapWidth();
                height = wmsMap.getMapHeight();
            } else {
                // guess a width and height based on the envelope
                Envelope area = map.getAreaOfInterest();

                if ((area.getHeight() > 0) && (area.getWidth() > 0)) {
                    if (area.getHeight() >= area.getWidth()) {
                        height = 600;
                        width = height * (area.getWidth() / area.getHeight());
                    } else {
                        width = 800;
                        height = width * (area.getHeight() / area.getWidth());
                    }
                }
            }

            if ((height == -1) || (width == -1)) {
                throw new IOException("Could not determine map dimensions");
            }

            SVGGeneratorContext context = setupContext();
            SVGGraphics2D g = new SVGGraphics2D(context, true);

            g.setSVGCanvasSize(new Dimension((int) width, (int) height));

            // turn off/on anti aliasing
            if (wms.isSvgAntiAlias()) {
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                        RenderingHints.VALUE_ANTIALIAS_ON);
            } else {
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                        RenderingHints.VALUE_ANTIALIAS_OFF);
            }

            // enforce no more than x rendering errors
            int maxErrors = wms.getMaxRenderingErrors();
            MaxErrorEnforcer errorChecker = new MaxErrorEnforcer(renderer, maxErrors);

            // Add a render listener that ignores well known rendering exceptions and reports back
            // non
            // ignorable ones
            final RenderExceptionStrategy nonIgnorableExceptionListener;
            nonIgnorableExceptionListener = new RenderExceptionStrategy(renderer);
            renderer.addRenderListener(nonIgnorableExceptionListener);

            renderer.paint(g, new Rectangle(g.getSVGCanvasSize()), mapContext.getRenderingArea(),
                    mapContext.getRenderingTransform());

            // check if too many errors occurred
            if (errorChecker.exceedsMaxErrors()) {
                throw new WmsException("More than " + maxErrors
                        + " rendering errors occurred, bailing out.", "internalError",
                        errorChecker.getLastException());
            }

            // check if a non ignorable error occurred
            if (nonIgnorableExceptionListener.exceptionOccurred()) {
                Exception renderError = nonIgnorableExceptionListener.getException();
                throw new WmsException("Rendering process failed", "internalError", renderError);
            }

            return g;
        } catch (ParserConfigurationException e) {
            throw new WmsException("Unexpected exception", "internalError", e);
        }
    }

    private SVGGeneratorContext setupContext() throws FactoryConfigurationError,
            ParserConfigurationException {
        Document document = null;

        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();

        DocumentBuilder db = dbf.newDocumentBuilder();

        // Create an instance of org.w3c.dom.Document
        String svgNamespaceURI = "http://www.w3.org/2000/svg";
        document = db.getDOMImplementation().createDocument(svgNamespaceURI, "svg", null);

        // Set up the context
        return SVGGeneratorContext.createDefault(document);
    }

}
