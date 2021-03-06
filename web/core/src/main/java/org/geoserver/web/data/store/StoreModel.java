/* Copyright (c) 2001 - 2011 TOPP - www.openplans.org. All rights reserved.
 * This code is licensed under the GPL 2.0 license, available at the root
 * application directory.
 */
package org.geoserver.web.data.store;

import org.apache.wicket.model.IModel;
import org.apache.wicket.model.LoadableDetachableModel;
import org.geoserver.catalog.StoreInfo;
import org.geoserver.catalog.WorkspaceInfo;
import org.geoserver.web.GeoServerApplication;
import org.geoserver.web.data.workspace.WorkspaceDetachableModel;

/**
 * Detachable model for a specific store.
 * 
 */
@SuppressWarnings("serial")
public class StoreModel<T extends StoreInfo> extends LoadableDetachableModel<T> {

    IModel workspace;
    String name;
    
    public StoreModel(T store) {
        super(store);
        setObject(store);
    }

    public void setObject(T object) {
        super.setObject(object);
        if (object != null) {
            workspace = new WorkspaceDetachableModel(object.getWorkspace());
            name = object.getName();
        }
    };
    
    @Override
    protected T load() {
        if (workspace == null) {
            return null;
        }
        return (T) GeoServerApplication.get().getCatalog().getStoreByName(
            (WorkspaceInfo) workspace.getObject(), name, StoreInfo.class); 
    }
}
