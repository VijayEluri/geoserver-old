/* Copyright (c) 2001 - 2011 TOPP - www.openplans.org. All rights reserved.
 * This code is licensed under the GPL 2.0 license, available at the root
 * application directory.
 */
package org.geoserver.wfs.v2_0;

import net.opengis.wfs20.UpdateType;

import org.geoserver.config.GeoServer;
import org.geoserver.wfs.UpdateElementHandler;

public class UpdateHandler extends UpdateElementHandler {

    public UpdateHandler(GeoServer gs) {
        super(gs, UpdateType.class);
    }

}