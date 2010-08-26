/* Copyright (c) 2001 - 2007 TOPP - www.openplans.org.  All rights reserved.
 * This code is licensed under the GPL 2.0 license, availible at the root
 * application directory.
 */
package org.vfny.geoserver.wms.responses;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.awt.image.ColorModel;
import java.awt.image.ComponentColorModel;
import java.awt.image.IndexColorModel;
import java.awt.image.RenderedImage;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Vector;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.media.jai.ImageLayout;
import javax.media.jai.Interpolation;
import javax.media.jai.InterpolationBicubic2;
import javax.media.jai.InterpolationBilinear;
import javax.media.jai.InterpolationNearest;
import javax.media.jai.JAI;
import javax.media.jai.LookupTableJAI;
import javax.media.jai.PlanarImage;
import javax.media.jai.ROI;
import javax.media.jai.ROIShape;
import javax.media.jai.operator.BandMergeDescriptor;
import javax.media.jai.operator.ConstantDescriptor;
import javax.media.jai.operator.CropDescriptor;
import javax.media.jai.operator.LookupDescriptor;
import javax.media.jai.operator.MosaicDescriptor;

import org.geoserver.platform.ServiceException;
import org.geoserver.wms.DefaultWebMapService;
import org.geoserver.wms.WMS;
import org.geoserver.wms.WMSInfo;
import org.geoserver.wms.WatermarkInfo;
import org.geoserver.wms.WMSInfo.WMSInterpolation;
import org.geoserver.wms.responses.MapDecoration;
import org.geoserver.wms.responses.MapDecorationLayout;
import org.geoserver.wms.responses.MetatiledMapDecorationLayout;
import org.geoserver.wms.responses.decoration.WatermarkDecoration;
import org.geotools.coverage.grid.GridCoverage2D;
import org.geotools.coverage.grid.GridEnvelope2D;
import org.geotools.coverage.grid.GridGeometry2D;
import org.geotools.coverage.grid.io.AbstractGridCoverage2DReader;
import org.geotools.coverage.grid.io.AbstractGridFormat;
import org.geotools.geometry.jts.ReferencedEnvelope;
import org.geotools.image.ImageWorker;
import org.geotools.image.palette.InverseColorMapOp;
import org.geotools.map.MapLayer;
import org.geotools.parameter.Parameter;
import org.geotools.referencing.crs.DefaultGeographicCRS;
import org.geotools.renderer.lite.RendererUtilities;
import org.geotools.renderer.lite.StreamingRenderer;
import org.geotools.renderer.lite.gridcoverage2d.GridCoverageRenderer;
import org.geotools.renderer.shape.ShapefileRenderer;
import org.geotools.resources.image.ColorUtilities;
import org.geotools.styling.RasterSymbolizer;
import org.geotools.styling.Style;
import org.opengis.feature.Feature;
import org.opengis.feature.type.FeatureType;
import org.opengis.geometry.BoundingBox;
import org.opengis.parameter.GeneralParameterValue;
import org.springframework.beans.factory.DisposableBean;
import org.vfny.geoserver.global.GeoserverDataDirectory;
import org.vfny.geoserver.wms.RasterMapProducer;
import org.vfny.geoserver.wms.WMSMapContext;
import org.vfny.geoserver.wms.WmsException;
import org.vfny.geoserver.wms.requests.GetMapRequest;
import org.vfny.geoserver.wms.responses.map.metatile.MetatileMapProducer;

/**
 * Abstract base class for GetMapProducers that relies in LiteRenderer for
 * creating the raster map and then outputs it in the format they specializes
 * in.
 * 
 * <p>
 * This class does the job of producing a BufferedImage using geotools
 * LiteRenderer, so it should be enough for a subclass to implement
 * {@linkPlain #formatImageOutputStream(String, BufferedImage, OutputStream)}
 * </p>
 * 
 * <p>
 * Generates a map using the geotools jai rendering classes. Uses the Lite
 * renderer, loading the data on the fly, which is quite nice. Thanks Andrea and
 * Gabriel. The word is that we should eventually switch over to
 * StyledMapRenderer and do some fancy stuff with caching layers, but I think we
 * are a ways off with its maturity to try that yet. So Lite treats us quite
 * well, as it is stateless and therefore loads up nice and fast.
 * </p>
 * 
 * <p>
 * </p>
 * 
 * @author Chris Holmes, TOPP
 * @author Simone Giannecchini, GeoSolutions
 * @version $Id$
 */
public abstract class DefaultRasterMapProducer extends
    AbstractRasterMapProducer implements RasterMapProducer {
    private final static Interpolation NN_INTERPOLATION = new InterpolationNearest();

    private final static Interpolation BIL_INTERPOLATION = new InterpolationBilinear();

    private final static Interpolation BIC_INTERPOLATION = new InterpolationBicubic2(
            0);

    // antialiasing settings, no antialias, only text, full antialias
    private final static String AA_NONE = "NONE";

    private final static String AA_TEXT = "TEXT";

    private final static String AA_FULL = "FULL";

    private final static List<String> AA_SETTINGS = Arrays.asList(new String[] {
            AA_NONE, AA_TEXT, AA_FULL });

    /**
     * The size of a megabyte
     */
    private static final int KB = 1024;

    /**
     * The lookup table used for data type transformation (it's really the identity one)
     */
    private static LookupTableJAI IDENTITY_TABLE = new LookupTableJAI(getTable());
    private static byte[] getTable() {
        byte[] arr = new byte[256];
        for (int i = 0; i < arr.length; i++) {
            arr[i] = (byte) i;
        }
        return arr;
    }

    /** WMS Service configuration * */
    protected WMS wms;

    /** A logger for this class. */
    private static final Logger LOGGER = org.geotools.util.logging.Logging.getLogger("org.vfny.geoserver.responses.wms.map");

    /** Which format to encode the image in if one is not supplied */
    private static final String DEFAULT_MAP_FORMAT = "image/png";

    /** The MapDecorationLayout instance **/
    private MapDecorationLayout layout;

    /** true iff this image is metatiled */
    private boolean tiled = false;
    
    private List<GridCoverage2D> renderedCoverages = new ArrayList<GridCoverage2D>();


    /**
     * 
     */
    public DefaultRasterMapProducer() {
        this(DEFAULT_MAP_FORMAT, null);
    }

    /**
     * 
     */
    public DefaultRasterMapProducer(WMS wms) {
        this(DEFAULT_MAP_FORMAT, wms);
    }

    /**
     * @param the mime type to be written down as an HTTP header when a map of this format is generated
     */
    public DefaultRasterMapProducer(String mime, WMS wms) {
        super(mime);
        this.wms = wms;
    }

    public DefaultRasterMapProducer(String mime, String[] outputFormats, WMS wms) {
        super(mime, outputFormats);
        this.wms = wms;
    }

    /**
     * Writes the image to the client.
     * 
     * @param out
     *            The output stream to write to.
     * 
     * @throws org.vfny.geoserver.ServiceException
     *             DOCUMENT ME!
     * @throws java.io.IOException
     *             DOCUMENT ME!
     */
    public void writeTo(OutputStream out)
    throws ServiceException, java.io.IOException {
        try {
            formatImageOutputStream(this.image, out);
            out.flush();
        } finally {
            // let go of the coverages created for rendering
            for (GridCoverage2D coverage : renderedCoverages) {
                coverage.dispose(true);
            }
            
            // let go of the image chain as quick as possible to free memory
            if(image instanceof PlanarImage) {
                disposePlanarImageChain((PlanarImage) image, new HashSet<PlanarImage>());
            } else if(image instanceof BufferedImage) {
                ((BufferedImage) image).flush();
            }
        }
    }
    
    @SuppressWarnings("unchecked")
	static void disposePlanarImageChain(PlanarImage pi, HashSet<PlanarImage> visited) {
        Vector sinks = pi.getSinks();
        if(sinks != null) {
            for (Object sink: sinks) {
                if(sink instanceof PlanarImage && !visited.contains(sink))
                    disposePlanarImageChain((PlanarImage) sink, visited);
                else if(sink instanceof BufferedImage) {
                    ((BufferedImage) sink).flush();
                }
            }
        }
        pi.dispose();
        visited.add(pi);
        Vector sources = pi.getSources();
        if(sources != null) {
            for (Object child : sources) {
                if(child instanceof PlanarImage && !visited.contains(child))
                    disposePlanarImageChain((PlanarImage) child, visited);
                else if(child instanceof BufferedImage) {
                    ((BufferedImage) child).flush();
                }
            }
        }
    }

    /**
     * Performs the execute request using geotools rendering.
     * 
     * @param map
     *            The information on the types requested.
     * 
     * @throws WmsException
     *             For any problems.
     */
    public void produceMap() throws WmsException {
        try {
            findDecorationLayout(mapContext);
        } catch (Exception e) { 
            throw new WmsException(e); 
        }

        Rectangle paintArea = new Rectangle(
                0, 0, 
                mapContext.getMapWidth(), mapContext.getMapHeight()
        );

        if (LOGGER.isLoggable(Level.FINE)) {
            LOGGER.fine("setting up " + paintArea.width + "x" + paintArea.height + " image");
        }

        // extra antialias setting
        final GetMapRequest request = mapContext.getRequest();
        String antialias = (String) request.getFormatOptions().get("antialias");
        if (antialias != null)
            antialias = antialias.toUpperCase();

        // figure out a palette for buffered image creation
        IndexColorModel palette = null;
        final InverseColorMapOp paletteInverter = mapContext.getPaletteInverter();
        final boolean transparent = mapContext.isTransparent() && isTransparencySupported();
        final Color bgColor = mapContext.getBgColor();
        if (paletteInverter != null && AA_NONE.equals(antialias)) {
            palette = paletteInverter.getIcm();
        } else if (AA_NONE.equals(antialias)) {
            PaletteExtractor pe = new PaletteExtractor(transparent ? null : bgColor);
            MapLayer[] layers = mapContext.getLayers();
            for (int i = 0; i < layers.length; i++) {
                pe.visit(layers[i].getStyle());
                if (!pe.canComputePalette())
                    break;
            }
            if (pe.canComputePalette())
                palette = pe.getPalette();
        }

        // before even preparing the rendering surface, check it's not too big,
        // if so, throw a service exception
        long maxMemory = wms.getMaxRequestMemory() * KB;
        // ... base image memory
        long memory = getDrawingSurfaceMemoryUse(paintArea.width, paintArea.height, palette, transparent);
        // .. use a fake streaming renderer to evaluate the extra back buffers used when rendering
        // multiple featureTypeStyles against the same layer
        StreamingRenderer testRenderer = new StreamingRenderer();
        testRenderer.setContext(mapContext);
        memory += testRenderer.getMaxBackBufferMemory(paintArea.width, paintArea.height);
        if(maxMemory > 0 && memory > maxMemory) {
            long kbUsed = memory / KB;
            long kbMax = maxMemory / KB;
            throw new WmsException("Rendering request would use " + kbUsed + "KB, whilst the " +
                    "maximum memory allowed is " + kbMax + "KB");
        }
        
        // TODO: allow rendering to continue with vector layers
        // TODO: allow rendering to continue with layout
        // TODO: handle rotated rasters
        // TODO: handle color conversions
        // TODO: handle meta-tiling
        // TODO: how to handle timeout here? I guess we need to move it into the dispatcher?
        
        // fast path for pure coverage rendering
        if (mapContext.getLayerCount() == 1 && mapContext.getAngle() == 0.0) {
            try {
                RenderedImage image = directRasterRender(mapContext, 0);

                if(image != null) {
                    this.image = image;
                    return;
                }
            } catch (Exception e) {
                throw new WmsException("Error rendering coverage on the fast path", null, e);
            }
        }

        
        
        // we use the alpha channel if the image is transparent or if the meta tiler
        // is enabled, since apparently the Crop operation inside the meta-tiler
        // generates striped images in that case (see GEOS-
        boolean useAlpha = transparent || MetatileMapProducer.isRequestTiled(request, this);
        final RenderedImage preparedImage = prepareImage(paintArea.width, paintArea.height, 
                palette, useAlpha);
        final Map<RenderingHints.Key, Object> hintsMap = new HashMap<RenderingHints.Key, Object>();

        final Graphics2D graphic = ImageUtils.prepareTransparency(transparent, bgColor,
                preparedImage, hintsMap);

        // set up the antialias hints
        if (AA_NONE.equals(antialias)) {
            hintsMap.put(RenderingHints.KEY_ANTIALIASING,
                    RenderingHints.VALUE_ANTIALIAS_OFF);
            if (preparedImage.getColorModel() instanceof IndexColorModel) {
                // otherwise we end up with dithered colors where the match is
                // not 100%
                hintsMap.put(RenderingHints.KEY_DITHERING,
                        RenderingHints.VALUE_DITHER_DISABLE);
            }
        } else if (AA_TEXT.equals(antialias)) {
            hintsMap.put(RenderingHints.KEY_ANTIALIASING,
                    RenderingHints.VALUE_ANTIALIAS_OFF);
            hintsMap.put(RenderingHints.KEY_TEXT_ANTIALIASING,
                    RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        } else {
            if (antialias != null && !AA_FULL.equals(antialias)) {
                LOGGER.warning("Unrecognized antialias setting '" + antialias
                        + "', valid values are " + AA_SETTINGS);
            }
            hintsMap.put(RenderingHints.KEY_ANTIALIASING,
                    RenderingHints.VALUE_ANTIALIAS_ON);
        }

        // these two hints improve text layout in diagonal labels and reduce artifacts
        // in line rendering (without hampering performance)
        hintsMap.put(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
        hintsMap.put(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);

        // turn off/on interpolation rendering hint
        if(wms != null) {
            if (WMSInterpolation.Nearest.equals(wms.getInterpolation())) {
                hintsMap.put(JAI.KEY_INTERPOLATION, NN_INTERPOLATION);
                hintsMap.put(RenderingHints.KEY_INTERPOLATION,RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
            } else if (WMSInterpolation.Bilinear.equals(wms.getInterpolation())) {
                hintsMap.put(JAI.KEY_INTERPOLATION, BIL_INTERPOLATION);
                hintsMap.put(RenderingHints.KEY_INTERPOLATION,RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            } else if (WMSInterpolation.Bicubic.equals(wms.getInterpolation())) {
                hintsMap.put(JAI.KEY_INTERPOLATION, BIC_INTERPOLATION);
                hintsMap.put(RenderingHints.KEY_INTERPOLATION,RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            }
        }

        // make sure the hints are set before we start rendering the map
        graphic.setRenderingHints(hintsMap);

        RenderingHints hints = new RenderingHints(hintsMap);
        if(DefaultWebMapService.useShapefileRenderer()) {
            renderer = new ShapefileRenderer();
        } else {
            StreamingRenderer sr = new StreamingRenderer();
//            sr.setThreadPool(DefaultWebMapService.getRenderingPool());
            renderer = sr;
        }
        renderer.setContext(mapContext);
        renderer.setJava2DHints(hints);

        // setup the renderer hints
        Map<Object, Object> rendererParams = new HashMap<Object, Object>();
        rendererParams.put("optimizedDataLoadingEnabled", new Boolean(true));
        rendererParams.put("renderingBuffer", new Integer(mapContext
                .getBuffer()));
        rendererParams.put("maxFiltersToSendToDatastore",  DefaultWebMapService.getMaxFilterRules());
        rendererParams.put(ShapefileRenderer.SCALE_COMPUTATION_METHOD_KEY,
                ShapefileRenderer.SCALE_OGC);
        if(AA_NONE.equals(antialias)) {
            rendererParams.put(ShapefileRenderer.TEXT_RENDERING_KEY, 
                    StreamingRenderer.TEXT_RENDERING_STRING);
        } else {
            rendererParams.put(ShapefileRenderer.TEXT_RENDERING_KEY, 
                    StreamingRenderer.TEXT_RENDERING_ADAPTIVE);
        }
        if(DefaultWebMapService.isLineWidthOptimizationEnabled()) {
            rendererParams.put(StreamingRenderer.LINE_WIDTH_OPTIMIZATION_KEY, true);
        }
        // turn on advanced projection handling
        rendererParams.put(StreamingRenderer.ADVANCED_PROJECTION_HANDLING_KEY, true);
        // see if the user specified a dpi
        if (mapContext.getRequest().getFormatOptions().get("dpi") != null) {
            rendererParams.put(StreamingRenderer.DPI_KEY, ((Integer) mapContext.getRequest().getFormatOptions().get("dpi")));
        }

        boolean kmplacemark = false;
        if (mapContext.getRequest().getFormatOptions().get("kmplacemark") != null)
            kmplacemark = ((Boolean) mapContext.getRequest().getFormatOptions()
                    .get("kmplacemark")).booleanValue();
        if (kmplacemark) {
            // create a StyleVisitor that copies a style, but removes the
            // PointSymbolizers and TextSymbolizers
            KMLStyleFilteringVisitor dupVisitor = new KMLStyleFilteringVisitor();
            
            // Remove PointSymbolizers and TextSymbolizers from the
            // layers' Styles to prevent their rendering on the
            // raster image. Both are better served with the
            // placemarks.
            MapLayer[] layers = mapContext.getLayers();
            for (int i = 0; i < layers.length; i++) {
                Style style = layers[i].getStyle();
                style.accept(dupVisitor);
                Style copy = (Style) dupVisitor.getCopy();
                layers[i].setStyle(copy);
            }
        }
        renderer.setRendererHints(rendererParams);

        // if abort already requested bail out
        if (this.abortRequested) {
            graphic.dispose();
            return;
        }

        // enforce no more than x rendering errors
        int maxErrors = wms.getMaxRenderingErrors();
        MaxErrorEnforcer errorChecker = new MaxErrorEnforcer(renderer, maxErrors);

        // Add a render listener that ignores well known rendering exceptions and reports back non
        // ignorable ones
        final RenderExceptionStrategy nonIgnorableExceptionListener;
        nonIgnorableExceptionListener = new RenderExceptionStrategy(renderer);
        renderer.addRenderListener(nonIgnorableExceptionListener);

        // setup the timeout enforcer (the enforcer is neutral when the timeout is 0)
        int maxRenderingTime = wms.getMaxRenderingTime() * 1000;
        RenderingTimeoutEnforcer timeout = new RenderingTimeoutEnforcer(maxRenderingTime, renderer,
                graphic);
        timeout.start();
        try {
            // finally render the image;
            renderer.paint(graphic, paintArea, mapContext.getRenderingArea(), mapContext.getRenderingTransform());

            // apply watermarking
            try {
                if (layout != null)
                    this.layout.paint(graphic, paintArea, mapContext);
            } catch (Exception e) {
                throw new WmsException("Problem occurred while trying to watermark data", "", e);
            }
        } finally {
            timeout.stop();
            graphic.dispose();
        }

        // check if the request did timeout
        if (timeout.isTimedOut()) {
            throw new WmsException(
                    "This requested used more time than allowed and has been forcefully stopped. "
                    + "Max rendering time is " + (maxRenderingTime / 1000.0) + "s");
        }

        //check if a non ignorable error occurred
        if(nonIgnorableExceptionListener.exceptionOccurred()){
            Exception renderError = nonIgnorableExceptionListener.getException();
            throw new WmsException("Rendering process failed", "internalError", renderError);
        }

        // check if too many errors occurred
        if(errorChecker.exceedsMaxErrors()) {
            throw new WmsException("More than " + maxErrors + " rendering errors occurred, bailing out.", 
                    "internalError", errorChecker.getLastException());
        }

        if (!this.abortRequested) {
            if(palette != null && palette.getMapSize() < 256)
                this.image = optimizeSampleModel(preparedImage);
            else 
                this.image = preparedImage;
        }
    }


    /**
     * Set the Watermark Painter.
     * 
     * @param wmPainter
     *            the wmPainter to set
     */
    public void setDecorationLayout(MapDecorationLayout layout) {
        this.layout = layout;
    }

    public void findDecorationLayout(WMSMapContext mapContext) throws Exception {
        String layoutName = null;
        if (mapContext.getRequest().getFormatOptions() != null) {
            layoutName = (String)mapContext.getRequest().getFormatOptions().get("layout");
        }

        if (layoutName != null){
            try {
                File layoutDir = GeoserverDataDirectory.findConfigDir(
                        GeoserverDataDirectory.getGeoserverDataDirectory(),
                        "layouts"
                );

                if (layoutDir != null) {
                    File layoutConfig = new File(layoutDir, layoutName + ".xml");

                    if (layoutConfig.exists() && layoutConfig.canRead()) {
                        this.layout = MapDecorationLayout.fromFile(layoutConfig, tiled);
                    } else {
                        LOGGER.log(Level.WARNING, "Unknown layout requested: " + layoutName);
                    }
                } else {
                    LOGGER.log(Level.WARNING, "No layout directory defined");
                }
            } catch (Exception e) {
                LOGGER.log(Level.WARNING, "Failed to load layout: " + layoutName, e);
            } 
        }

        if (layout == null){
            layout = tiled 
            ? new MetatiledMapDecorationLayout()
            : new MapDecorationLayout();
        }

        WMS wms = mapContext.getRequest().getWMS();

        if (wms != null){ 
            MapDecorationLayout.Block watermark = 
                getWatermark(mapContext.getRequest().getWMS().getServiceInfo());
            if (watermark != null) layout.addBlock(watermark);
        }
    }

    public static MapDecorationLayout.Block getWatermark(WMSInfo wms) {
        WatermarkInfo watermark = (wms == null ? null : wms.getWatermark());
        if (watermark != null && watermark.isEnabled()) {
            Map<String, String> options = new HashMap<String,String>();
            options.put("url", watermark.getURL());
            options.put("opacity", Float.toString((255f - watermark.getTransparency())/ 2.55f));

            MapDecoration d = new WatermarkDecoration();
            try {
                d.loadOptions(options);
            } catch (Exception e) {
                LOGGER.log(Level.SEVERE, "Couldn't construct watermark from configuration", e);
                throw new WmsException(e);
            }

            MapDecorationLayout.Block.Position p = null;

            switch (watermark.getPosition()) {
            case TOP_LEFT:
                p = MapDecorationLayout.Block.Position.UL;
                break;
            case TOP_CENTER:
                p = MapDecorationLayout.Block.Position.UC;
                break;
            case TOP_RIGHT:
                p = MapDecorationLayout.Block.Position.UR;
                break;
            case MID_LEFT:
                p = MapDecorationLayout.Block.Position.CL;
                break;
            case MID_CENTER:
                p = MapDecorationLayout.Block.Position.CC;
                break;
            case MID_RIGHT:
                p = MapDecorationLayout.Block.Position.CR;
                break;
            case BOT_LEFT:
                p = MapDecorationLayout.Block.Position.LL;
                break;
            case BOT_CENTER:
                p = MapDecorationLayout.Block.Position.LC;
                break;
            case BOT_RIGHT:
                p = MapDecorationLayout.Block.Position.LR;
                break;
            default:
                throw new WmsException(
                        "Unknown WatermarkInfo.Position value.  Something is seriously wrong."
                );
            }

            return new MapDecorationLayout.Block(d, p, null, new Point(0,0));
        }

        return null;
    }

    /**
     * Indicate whether metatiling is activated for this map producer.
     */
    public void setMetatiled(boolean tiled) {
        this.tiled = tiled;
    }

    /**
     * Sets up a {@link BufferedImage#TYPE_4BYTE_ABGR} if the paletteInverter is
     * not provided, or a indexed image otherwise. Subclasses may override this
     * method should they need a special kind of image
     * 
     * @param width
     * @param height
     * @param paletteInverter
     * @return
     */
    final protected RenderedImage prepareImage(int width, int height,
            IndexColorModel palette, boolean transparent) {
        return ImageUtils.createImage(width, height, isPaletteSupported() ? palette : null,
                transparent && isTransparencySupported());
    }
    
    /**
     * Returns true if the format supports image transparency, false otherwise
     * @return true if the format supports image transparency, false otherwise
     */
    protected boolean isTransparencySupported() {
        return true;
    }
    
    /**
     * Returns true if the format supports palette encoding, false otherwise
     * @return true if the format supports palette encoding, false otherwise
     */
    protected boolean isPaletteSupported() {
        return true;
    }
    

    /**
     * When you override {@link #prepareImage(int, int, IndexColorModel, boolean)} remember
     * to override this one as well
     * @param width
     * @param height
     * @param palette
     * @param transparent
     * @return
     */
    protected long getDrawingSurfaceMemoryUse(int width, int height,
            IndexColorModel palette, boolean transparent) {
        return ImageUtils.getDrawingSurfaceMemoryUse(width, height, isPaletteSupported() ? palette : null,transparent && isTransparencySupported());
    }

    /**
     * @param originalImage
     * @return
     */
    protected RenderedImage forceIndexed8Bitmask(RenderedImage originalImage) {
        return ImageUtils.forceIndexed8Bitmask(originalImage, mapContext.getPaletteInverter());
    }

    /**
     * This takes an image with an indexed color model that uses
     * less than 256 colors and has a 8bit sample model, and transforms it to
     * one that has the optimal sample model (for example, 1bit if the palette only has 2 colors)
     * @param source
     * @return
     */
    private static RenderedImage optimizeSampleModel(RenderedImage source) {
        int w = source.getWidth();
        int h = source.getHeight();
        ImageLayout layout = new ImageLayout();
        layout.setColorModel(source.getColorModel());
        layout.setSampleModel(source.getColorModel().createCompatibleSampleModel(w, h));
        // if I don't force tiling off with this setting an exception is thrown
        // when writing the image out...
        layout.setTileWidth(w);
        layout.setTileHeight(h);
        RenderingHints hints = new RenderingHints(JAI.KEY_IMAGE_LAYOUT, layout);
        // TODO SIMONE why not format?
        return LookupDescriptor.create(source, IDENTITY_TABLE, hints);

    }
    
    /**
     * Renders a single coverage as the final RenderedImage to be encoded, skipping all of the
     * Java2D machinery and using a pure JAI chain of transformations instead. This considerably
     * improves both scalability and performance
     * @param mapContext The map definition (used for map size and transparency/color management)
     * @param the layer that is supposed to contain a coverage
     * @return the result of rendering the coverage, or null if there was no coverage, or the coverage 
     *         could not be renderer for some reason
     */
    private RenderedImage directRasterRender(WMSMapContext mapContext, int layerIndex) throws IOException {
        // extract the raster symbolizers and see if we can use the fast path
        List<RasterSymbolizer> symbolizers = getRasterSymbolizers(mapContext, 0);
        if(symbolizers.size() != 1)
            return null;
        RasterSymbolizer symbolizer = symbolizers.get(0);
        
        // extract the reader
        Feature feature = mapContext.getLayer(0).getFeatureSource().getFeatures().features().next();
        AbstractGridCoverage2DReader reader = (AbstractGridCoverage2DReader) feature.getProperty("grid").getValue();
        Object params = feature.getProperty("params").getValue();
        
        // if there is a output tile size hint, use it, otherwise use the output size itself 
        final int tileSizeX; 
        final int tileSizeY;
        if(mapContext.getTileSize() != -1) {
           tileSizeX = tileSizeY = mapContext.getTileSize();
        } else {
           tileSizeX = mapContext.getMapWidth();
           tileSizeY = mapContext.getMapHeight();
        }
        
        // prepare a final image layout should we need to perform a mosaic or a crop
        final ImageLayout layout= new ImageLayout();
        layout.setMinX(0);
        layout.setMinY(0);
        layout.setWidth(mapContext.getMapWidth());
        layout.setHeight(mapContext.getMapHeight());
        layout.setTileGridXOffset(0);
        layout.setTileGridYOffset(0);
        layout.setTileWidth(tileSizeX);
        layout.setTileHeight(tileSizeY);
        
        // Check transparency and bg color
        final boolean transparent = mapContext.isTransparent() && isTransparencySupported();
        Color bgColor = mapContext.getBgColor();
        
        // set transparency
        if(transparent)
            bgColor= new Color(bgColor.getRed(),bgColor.getGreen(),bgColor.getBlue(),0);
        
        // grab the interpolation
        Interpolation interpolation = Interpolation.getInstance(Interpolation.INTERP_NEAREST);
        if (wms != null) {
            if (WMSInterpolation.Nearest.equals(wms.getInterpolation())) {
                interpolation = Interpolation.getInstance(Interpolation.INTERP_NEAREST);
            } else if (WMSInterpolation.Bilinear.equals(wms.getInterpolation())) {
                interpolation = Interpolation.getInstance(Interpolation.INTERP_BILINEAR);
            } else if (WMSInterpolation.Bicubic.equals(wms.getInterpolation())) {
                interpolation = Interpolation.getInstance(Interpolation.INTERP_BICUBIC);
            }
        }

        // read best available coverage and render it
        final Rectangle rasterArea = new Rectangle(0, 0, mapContext.getMapWidth(), mapContext.getMapHeight());
        final AffineTransform worldToScreen = RendererUtilities.worldToScreenTransform(mapContext.getAreaOfInterest(), rasterArea);
        try {
            final GridCoverage2D coverage = readBestCoverage(mapContext, reader, params, rasterArea, interpolation);
            Number[] bands;
            if(transparent) {
                bands = new Byte[] {(byte) bgColor.getRed(), (byte) bgColor.getGreen(), (byte) bgColor.getBlue(), (byte) bgColor.getAlpha()};
            } else {
                bands = new Byte[] {(byte) bgColor.getRed(), (byte) bgColor.getGreen(), (byte) bgColor.getBlue()};
            }
            // render the coverage
            try {
                if (coverage == null) {
                    // we're outside of the coverage definition area, return an empty space
                    image = ConstantDescriptor.create((float) mapContext.getMapWidth(), (float) mapContext.getMapHeight(), 
                            bands, new RenderingHints(JAI.KEY_IMAGE_LAYOUT, layout));
                } else {
                    // create a solid color empty image
                    final GridCoverageRenderer gcr = new GridCoverageRenderer(
                    		mapContext.getCoordinateReferenceSystem(), 
                            mapContext.getAreaOfInterest(), 
                            rasterArea,
                            worldToScreen, 
                            null);
                    image = gcr.renderImage(coverage, symbolizer, interpolation, mapContext.getBgColor(), tileSizeX, tileSizeY);
                }
            } finally {
                // once the final image is rendered we need to clean up the planar image chain
                // that the coverage references to
                if (coverage != null)
                    renderedCoverages.add(coverage);
            }
        } catch (Throwable e) {
            throw new WmsException(e);
        }
        
        // check if we managed to process the coverage into an image
        if(image == null)
            return null;
        // We need to find the background color expressed in terms of image color components
        // (which depends on the color model nature, the input and output transparency)
        // TODO: there must be a more general way to turn a color into the
        // required components for a certain color model... right???
        ColorModel cm = image.getColorModel();
        double[] bgValues = null;
        
        PlanarImage[] alphaChannels = null;
        
        // in case of index color model we try to preserve it, so that output
        // formats that can work with it can enjoy its extra compactness
        if(cm instanceof IndexColorModel) {
            IndexColorModel icm = (IndexColorModel) cm;
            
            // try to find the index that matches the requested background color
            int bgColorIndex = -1;
            if (cm.hasAlpha()) {
                if (transparent) {
                    bgColorIndex = icm.getTransparentPixel(); 
                } else {
                    bgColorIndex = ColorUtilities.findColorIndex(bgColor, icm);
                }
                
                if(bgColorIndex != -1){
                	final ImageWorker worker=new ImageWorker(image);
                	worker.forceComponentColorModel();
                	final RenderedImage alpha=worker.retainLastBand().getRenderedImage();
                    alphaChannels = new PlanarImage[] {PlanarImage.wrapRenderedImage(alpha)};
                }
            } else {
                bgColorIndex = ColorUtilities.findColorIndex(bgColor, icm);
            }
            
            if(bgColorIndex == -1) {
                // we need to expand the image to RGB 
                image = new ImageWorker(image).forceComponentColorModel().getRenderedImage();
                bgValues = new double[] { bgColor.getRed(), bgColor.getGreen(),bgColor.getBlue(), transparent ? 0 : 255 };
                cm = image.getColorModel();
            } else {
                bgValues = new double[] {bgColorIndex};
            }
        }
        
        // in case of component color model 
        if (cm instanceof ComponentColorModel ){
        	
        	// convert to RGB if GRAY
        	final ComponentColorModel ccm= (ComponentColorModel) cm;
        	final boolean hasAlpha=cm.hasAlpha();
        	
        	
        	// if we have a grayscale image expand to rgb
        	if(ccm.getNumColorComponents() == 1) {
	            // aaime TODO: the original color model might be able to produce the requested bg color
	            // we should find a way to avoid the band expansion in that case
	            // aaime TODO: what do we do if the number of color components is not 1, 3 or 4?
	        	
	        	// simone: keep into account alpha band
        		final ImageWorker iw=new ImageWorker(image);
	        	if(hasAlpha){
	        		// get alpha
	        		final RenderedImage alpha = iw.retainLastBand().getRenderedImage();
	        		alphaChannels = new PlanarImage[] {PlanarImage.wrapRenderedImage(alpha)};
	        		// get first band
	        		final RenderedImage gray = new ImageWorker(image).retainFirstBand().getRenderedImage();
	        		image=new ImageWorker(gray).bandMerge(3).addBand(alpha, false).forceComponentColorModel().forceColorSpaceRGB().getRenderedImage();
	        	}
	        	else
	        		image =iw.bandMerge(3).forceComponentColorModel().forceColorSpaceRGB().getRenderedImage();
	        	
	        	// get back the ColorModel
	            cm = image.getColorModel();
        	}
        	
            if (hasAlpha) {
                
                // TODO: are there cases other than RGBA here? I guess
                // if the cm does not have 4 bands we should expand to RGB first?
                if (transparent) {
                    bgValues = new double[] { bgColor.getRed(), bgColor.getGreen(),
                            bgColor.getBlue(), 0 };
                } else {
                    bgValues = new double[] { bgColor.getRed(), bgColor.getGreen(),
                            bgColor.getBlue(), 255 };
                }
            } else {
                if (transparent) {
                    // we need to expand the image with an alpha channel
                    RenderedImage alpha = ConstantDescriptor.create(new Float(image.getMinX() + image.getWidth()), 
                            new Float(image.getMinY() + image.getHeight()), new Byte[] {new Byte((byte) 255)}, null);
                    image = BandMergeDescriptor.create(image, alpha, null);
                    // this will work fine for all situation where the color components are <= 3
                    // e.g., one band rasters with no colormap will have only one usually
                    bgValues = new double[] {0, 0, 0, 0 };
                } else {
                    // TODO: handle the case where the component color model is not RGB
                    // We cannot use ImageWorker as is because it basically seems to assume
                    // component -> 3 band in forceComponentColorModel()
                    // but I guess we'll need to turn the image into a 3 band RGB one. 
                    bgValues = new double[] { bgColor.getRed(), bgColor.getGreen(),
                            bgColor.getBlue() };
                }
            }
        } 
        // TODO: handle other color models, as the direct one (can we ever get it?)

        Rectangle imageBounds = PlanarImage.wrapRenderedImage(image).getBounds();
        
        // If we need to add a collar and apply a bg color, use mosaic for fast bg color application
        // Else check if we need to crop a larger than required image
        if(imageBounds.getWidth() < rasterArea.getWidth() || imageBounds.getHeight() < rasterArea.getHeight() 
                || imageBounds.getMinX() > 0 || imageBounds.getMinY() > 0 || alphaChannels != null) {
            // setup the ROI, otherwise we won't get solid color background collars around the image
            Rectangle roi = new Rectangle(image.getMinX(), image.getMinY(), 
                                          image.getWidth(), image.getHeight());
            ROI[] rois = new ROI[] {new ROIShape(roi)};

            // build the transparency thresholds
            double[][] thresholds = new double[][] { { ColorUtilities.getThreshold(image.getSampleModel()
                    .getDataType()) } };

            // apply the mosaic
            image = MosaicDescriptor.create(
            		new RenderedImage[] { image },
            		MosaicDescriptor.MOSAIC_TYPE_OVERLAY,//alphaChannels != null ? MosaicDescriptor.MOSAIC_TYPE_BLEND: MosaicDescriptor.MOSAIC_TYPE_OVERLAY, 
	                alphaChannels, 
	                rois, 
	                thresholds, 
	                bgValues,
	                new RenderingHints(JAI.KEY_IMAGE_LAYOUT,layout));
            
            
        } else if(imageBounds.getWidth() > rasterArea.getWidth() || 
                  imageBounds.getHeight() > rasterArea.getHeight()) {
            // reduce the image to the actually required size
            image = CropDescriptor.create(image, 0f, 0f, 
                    (float) mapContext.getMapWidth(), (float) mapContext.getMapHeight(), 
                    new RenderingHints(JAI.KEY_IMAGE_LAYOUT,layout));
        }
        
        return image;
    }

    /**
     * Reads the best matching grid out of a grid coverage applying sub-sampling and using overviews as necessary
     * @param mapContext
     * @param reader
     * @param params
     * @param rasterArea
     * @param interpolation
     * @return
     * @throws IOException
     */
	private GridCoverage2D readBestCoverage(WMSMapContext mapContext,
			AbstractGridCoverage2DReader reader, Object params,
			final Rectangle rasterArea, Interpolation interpolation) throws IOException {
	    
	    try {
	        ReferencedEnvelope dataEnvelope = new ReferencedEnvelope(reader.getOriginalEnvelope()).transform(DefaultGeographicCRS.WGS84, true);
	        ReferencedEnvelope requestEnvelope = mapContext.getAreaOfInterest().transform(DefaultGeographicCRS.WGS84, true);
	        if(!dataEnvelope.intersects((BoundingBox) requestEnvelope))
	            return null;
	    } catch(Exception e) {
	        LOGGER.log(Level.WARNING, "Failed to compare data and request envelopes, proceeding with rendering anyways", e);
	    }
	    
	    
	    
		// //
		// It is an AbstractGridCoverage2DReader, let's use parameters
		// if we have any supplied by a user.
		// //
		// first I created the correct ReadGeometry
		final Parameter<GridGeometry2D> readGG = new Parameter<GridGeometry2D>(AbstractGridFormat.READ_GRIDGEOMETRY2D);
		readGG.setValue(new GridGeometry2D(new GridEnvelope2D(rasterArea), mapContext.getAreaOfInterest()));
		// then I try to get read parameters associated with this
		// coverage if there are any.
		GridCoverage2D coverage = null;
		if (params != null) {
		    // //
		    //
		    // Getting parameters to control how to read this coverage.
		    // Remember to check to actually have them before forwarding
		    // them to the reader.
		    //
		    // //
		    GeneralParameterValue[] readParams = (GeneralParameterValue[]) params;
		    final int length = readParams.length;
		    if (length > 0) {
		        // we have a valid number of parameters, let's check if
		        // also have a READ_GRIDGEOMETRY2D. In such case we just
		        // override it with the one we just build for this
		        // request.
		        final String name = AbstractGridFormat.READ_GRIDGEOMETRY2D.getName().toString();
		        int i = 0;
		        for (; i < length; i++)
		            if (readParams[i].getDescriptor().getName().toString().equalsIgnoreCase(
		                    name))
		                break;
		        // did we find anything?
		        if (i < length) {
		            // we found another READ_GRIDGEOMETRY2D, let's override it.
		            ((Parameter) readParams[i]).setValue(readGG);
		            coverage = (GridCoverage2D) reader.read(readParams);
		        } else {
		            // add the correct read geometry to the supplied
		            // params since we did not find anything
		            GeneralParameterValue[] readParams2 = new GeneralParameterValue[length + 1];
		            System.arraycopy(readParams, 0, readParams2, 0, length);
		            readParams2[length] = readGG;
		            coverage = (GridCoverage2D) reader.read(readParams2);
		        }
		    }
		}
		// if for any reason the previous block did not produce a coverage (no params, empty params)
		if(coverage == null) {
		    coverage = (GridCoverage2D) reader.read(new GeneralParameterValue[] { readGG });
		}
		
		return coverage;
	}
	
    /**
     * Returns the list of raster symbolizers contained in a specific layer of the map context (the
     * full map context is provided in order to compute the current scale and thus determine the
     * active rules)
     * 
     * @param mc
     * @param layerIndex
     * @return
     */
    List<RasterSymbolizer> getRasterSymbolizers(WMSMapContext mc, int layerIndex) {
        double scaleDenominator = RendererUtilities.calculateOGCScale(mc.getAreaOfInterest(), mc.getMapWidth(), null);
        MapLayer layer = mc.getLayer(layerIndex);
        FeatureType featureType = layer.getFeatureSource().getSchema();
        Style style = layer.getStyle();
        
        RasterSymbolizerVisitor visitor = new RasterSymbolizerVisitor(scaleDenominator, featureType);
        style.accept(visitor);
        
        return visitor.getRasterSymbolizers(); 
    }

    
    

}
