/* Copyright (c) 2001 - 2007 TOPP - www.openplans.org.  All rights reserved.
 * This code is licensed under the GPL 2.0 license, availible at the root
 * application directory.
 */
package org.geoserver.wms.decoration;

import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.Shape;
import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.geoserver.platform.GeoServerExtensions;
import org.geoserver.platform.ServiceException;
import org.geoserver.wms.WMSMapContent;
import org.jdom.Document;
import org.jdom.Element;
import org.jdom.input.SAXBuilder;

/**
 * The MapDecorationLayout class describes a set of overlays to be used to enhance a WMS response.
 * It maintains a collection of MapDecoration objects and the configuration associated with each, and
 * delegates the actual rendering operations to the decorations.
 *
 * @author David Winslow <dwinslow@opengeo.org> 
 */
public class MapDecorationLayout {
    private static Logger LOGGER = 
        org.geotools.util.logging.Logging.getLogger("org.geoserver.wms.responses");

    /**
     * The Block class annotates a MapDecoration object with positioning and sizing information, and
     * encapsulates the logic involved in resizing a decoration to fit within a particular image.
     */
    public static class Block {
        /**
         * The Position enum encodes the 'affinity' attribute of decorations.  A decoration can be 
         * anchored at either extreme or centered in the X and Y dimensions, independently, allowing
         * for nine possible Positions.
         */
        public static enum Position {
            UL("top,left"), UC("top,center"), UR("top,right"), 
            CL("center,left"), CC("center,center"), CR("center,right"), 
            LL("bottom,left"), LC("bottom,center"), LR("bottom,right");

            private final String name;

            Position(String name) { this.name = name; }

            /**
             * Decode a Position from a (presumably user-provided) string. 
             * @param str the input String, expected to be in the format <vpos,hpos> with no 
             *     whitespace
             * @return the associated Position, or null if none can be found
             */
            public static Position fromString(String str) {
                for (Position p : values()) {
                    if (p.name.equalsIgnoreCase(str)) return p;
                }

                return null;
            }
        }
        /**
         * Given the configuration and the geometry of a particular WMS response, determine the 
         * appropriate space into which a MapDecoration should draw itself.
         * @param p the Position instance indicating the area of the image where the decoration is 
         *     anchored
         * @param container a Rectangle indicating the entire drawable area of the map image
         * @param dim the requested size based on either configuration or feedback from the 
         *     MapDecoration
         * @param o a Point whose x- and y-coordinates will be interpreted as x- and y-offsets for 
         *     the MapDecoration
         * @return A rectangle that is as close to the desired size and position without exceeding 
         *     the container bounds
         */
        public static Rectangle findBounds(
                Position p,
                Rectangle container,
                Dimension dim,
                Point o) {

            if (p == null || container == null || dim == null || o == null) {
                throw new ServiceException("Bad params for decoration sizing.");
            }

            int x = 0, y = 0;
            int height = dim.height, width = dim.width;

            // adjust Y coord
            switch (p) {
                case UC:
                case UR:
                case UL:
                    y = (int) (container.getMinY() + o.y);
                    break;

                case CL:
                case CC:
                case CR:
                    y = (int) (container.getMinY() + container.getMaxY() - dim.height) / 2;
                    // ignore vertical offset when vertically centered
                    break;

                case LL:
                case LC:
                case LR:
                    y = (int) (container.getMaxY() - o.y - dim.height);
            }

            // adjust X coord
            switch(p){
                case UL:
                case CL:
                case LL:
                    x = (int) (container.getMinX() + o.x);
                    break;

                case UC:
                case CC:
                case LC:
                    x = (int) (container.getMinX() + container.getMaxX()) / 2;
                    break;

                case UR:
                case CR:
                case LR:
                    x = (int) (container.getMaxX() - o.x - dim.width);
            }

            // in the event that this block does not fit in the container, resize each dimension 
            // independently to fit (with space for the offset parameter)
            if ((dim.width + (2 * o.x)) > container.width) {
                x = (int) container.getMinX() + o.x;
                width = container.width - (2 * o.x);
            }

            if ((dim.height + (2 * o.y)) > container.height) {
                y = (int) container.getMinY() + o.y;
                height = container.height - (2 * o.y);
            }

            return new Rectangle(x, y, width, height);
        }

        /**
         * The MapDecoration that the Block will render
         */
        final MapDecoration decoration;

        /**
         * The Position at which the Block is anchored
         */
        final Position position;
        
        /**
         * The requested size, or null if the MapDecoration should be allowed to determine sizing
         */
        final Dimension dimension;

        /**
         * A Point whose x- and y-coordinates are interpreted as the x- and y-offsets when rendering
         * the MapDecoration
         */
        final Point offset;

        /**
         * Create a Block with all needed information.
         * @param d the MapDecoration which the Block will render
         * @param p the Position to which the Block is anchored
         * @param dim the Dimension of the user-requested size, or null if the MapDecoration should 
         *     determine its own size
         * @param o a Point indicating the offset (see {offset})
         */
        public Block(MapDecoration d, Position p, Dimension dim, Point o) {
            decoration = d;
            position = p;
            dimension = dim;
            offset = o;
        }

        /**
         * Determine the desired size for the decoration, either the user-specified size, or an
         * automatically detemrined size from the MapDecoration
         *
         * @param g2d the Graphics2D context into which the MapDecoration will be rendered
         * @param mapContent the WMSMapContext for the request being handled
         */
        public Dimension findOptimalSize(Graphics2D g2d, WMSMapContent mapContent) {
            return (dimension != null) 
                ? dimension 
                : decoration.findOptimalSize(g2d, mapContent);
        }

        /**
         * Draw this Block.  Sizing and positioning will be handled by the findBounds method.
         * @param g2d the Graphics2D context where the Block should be drawn
         * @param rect the current drawable area
         * @param mapContent the map context for the current map request
         */
        public void paint(Graphics2D g2d, Rectangle rect, WMSMapContent mapContent) 
        throws Exception {
            Dimension desiredSize = findOptimalSize(g2d, mapContent);

            Rectangle box = findBounds(position, rect, desiredSize, offset);
            Shape oldClip = g2d.getClip();
            g2d.setClip(box);
            decoration.paint(g2d, box, mapContent);
            g2d.setClip(oldClip);
        }
    }

    /**
     * A container for the blocks in this layout.  Blocks contain the positioning information, so 
     * this contains all the state for the layout.
     *
     * @see {Block}
     */
    private List<Block> blocks;

    /**
     * Create a new MapDecorationLayout with no decorations in it yet.
     */
    public MapDecorationLayout() {
        this.blocks = new ArrayList<Block>();
    }

    /**
     * Read an XML layout file and populate a new MapDecorationLayout with the MapDecorations specified 
     * therein.
     *
     * @param f the File from which the layout should be read
     * @param tiled is this map metatiled?
     * @return a new MapDecorationLayout containing the MapDecorations specified
     * @throws Exception if the configuration is invalid or other errors occur while parsing
     */
    public static MapDecorationLayout fromFile(File f, boolean tiled) throws Exception {
        MapDecorationLayout dl = tiled 
            ? new MetatiledMapDecorationLayout()
            : new MapDecorationLayout();
        
        Document confFile = new SAXBuilder().build(f);

        for (Element e : (List<Element>)confFile.getRootElement().getChildren("decoration")){
            Map<String, String> m = new HashMap<String,String>();
            for (Element option : (List<Element>)e.getChildren("option")){
                m.put(option.getAttributeValue("name"), option.getAttributeValue("value"));
            }

            MapDecoration decoration = getDecoration(e.getAttributeValue("type"));
            if (decoration == null) {
                LOGGER.log(
                    Level.WARNING,
                    "Unknown decoration type: " + e.getAttributeValue("type") + " requested."
                );
                continue;
            }
            decoration.loadOptions(m);

            Block.Position pos = Block.Position.fromString(e.getAttributeValue("affinity"));

            if (pos == null) {
                LOGGER.log(
                    Level.WARNING,
                    "Unknown affinity: " + e.getAttributeValue("affinity") + " requested."
                );
                continue;
            }

            Dimension size = null;

            try {
                if (e.getAttributeValue("size") != null 
                        && !e.getAttributeValue("size").equalsIgnoreCase("auto")) {
                    String[] sizeArr = e.getAttributeValue("size").split(",");

                    size = new Dimension(Integer.valueOf(sizeArr[0]), Integer.valueOf(sizeArr[1]));
                }
            } catch (Exception exc){
                LOGGER.log(
                    Level.WARNING,
                    "Couldn't interpret size parameter: "  + e.getAttributeValue("size"),
                    e
                );
            }

            Point offset = null;
            try {
                String[] offsetArr = e.getAttributeValue("offset").split(",");
                offset = new Point(Integer.valueOf(offsetArr[0]), Integer.valueOf(offsetArr[1]));
            } catch (Exception exc) {
                LOGGER.log(
                    Level.WARNING,
                    "Couldn't interpret size parameter: " + e.getAttributeValue("offset")
                );
                offset = new Point(0, 0);
            }

            dl.addBlock(new Block(
                decoration,
                pos,
                size,
                offset
            ));
        }

        return dl;
    }

    /**
     * Add a Block to the layout. 
     *
     * @see {Block}
     */
    public void addBlock(Block b) {
        blocks.add(b);
    }

    /**
     * Paint all the Blocks in this layout.
     *
     * @param g2d the Graphics2D context in which the Blocks will be rendered
     * @param paintArea the drawable area
     * @param mapContent the WMSMapContext for the current map request
     *
     * @see {Block#paint}
     */
    public void paint(Graphics2D g2d, Rectangle paintArea, WMSMapContent mapContent) { 
        for (Block b : blocks) {
            try {
                b.paint(g2d, paintArea, mapContent);
            } catch (Exception e) {
                LOGGER.log(Level.WARNING, "couldn't paint due to: ", e);
            }
        }
    }

    /**
     * Find a MapDecoration plugin by name
     * @param name the name of the MapDecoration plugin to look up, case-sensitive
     * @return the corresponding MapDecoration, or null if none is available with the given name
     */
    private static MapDecoration getDecoration(String name) {
        Object o = GeoServerExtensions.bean(name);

        if (o instanceof MapDecoration) {
            return (MapDecoration) o;
        }

        return null;
    }
    
    /**
     * Returns true if the layout is not going to paint anything
     */
    public boolean isEmpty() {
        return blocks.isEmpty();
    }
}
