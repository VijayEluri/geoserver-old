/* Copyright (c) 2001 - 2007 TOPP - www.openplans.org. All rights reserved.
 * This code is licensed under the GPL 2.0 license, available at the root
 * application directory.
 */
package org.geoserver.web.security.role;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.SortedSet;

import org.apache.wicket.model.IModel;
import org.apache.wicket.model.Model;
import org.geoserver.security.impl.GeoserverRole;
import org.geoserver.security.impl.GeoserverUserGroup;
import org.geoserver.web.GeoServerApplication;
import org.geoserver.web.wicket.GeoServerDataProvider;

/**
 * Page listing for {@link GeoserverUserGroup} objects
 * 
 * @author christian
 *
 */
@SuppressWarnings("serial")
public class RoleListProvider extends GeoServerDataProvider<GeoserverRole> {

    
    public static final Property<GeoserverRole> ROLENAME = new BeanProperty<GeoserverRole>("rolename", "authority");    
    public static final Property<GeoserverRole> PARENTROLENAME = new Property<GeoserverRole>() {
        
        @Override
        public String getName() {
            return "parentrolename";
        }

        @Override
        public Object getPropertyValue(GeoserverRole item) {
            GeoserverRole parent=null;
            try {
                parent = GeoServerApplication.get().getSecurityManager()
                    .getActiveRoleService().getParentRole(item);
            } catch (IOException e) {
                //TODO is this correct
                throw new RuntimeException(e);
            }
            
            if (parent==null)
                return "";
            else
                return parent.getAuthority();
        }

        @Override
        public IModel getModel(IModel itemModel) {
            return new Model((String) getPropertyValue((GeoserverRole) itemModel.getObject()));
        }

        @Override
        public Comparator<GeoserverRole> getComparator() {
            return new PropertyComparator<GeoserverRole>(this);
        }

        @Override
        public boolean isVisible() {
            return true;
        }

        @Override
        public boolean isSearchable() {
            return true;
        }
        
    };
            
    public static final Property<GeoserverRole> HASROLEPARAMS = new Property<GeoserverRole>() {

        @Override
        public String getName() {
            return "hasroleparams";
        }

        @Override
        public Object getPropertyValue(GeoserverRole item) {
            if (item.getProperties().size()==0)
                return Boolean.FALSE;
            else
                return Boolean.TRUE;                    
        }

        @Override
        public IModel getModel(IModel itemModel) {
            return new Model((Boolean) getPropertyValue((GeoserverRole) itemModel.getObject()));
        }

        @Override
        public Comparator<GeoserverRole> getComparator() {
            return new PropertyComparator<GeoserverRole>(this);
        }

        @Override
        public boolean isVisible() {
            return true;
        }

        @Override
        public boolean isSearchable() {
            return true;
        }        
    };
    
    @Override
    protected List<GeoserverRole> getItems() {
        SortedSet<GeoserverRole> roles=null;
        try {
            roles = GeoServerApplication.get().getSecurityManager().getActiveRoleService().getRoles();
        } catch (IOException e) {
            // TODO, is this correct ?
            throw new RuntimeException(e); 
        }
        List<GeoserverRole> roleList = new ArrayList<GeoserverRole>();
        roleList.addAll(roles);
        return roleList;
    }

    @Override
    protected List<Property<GeoserverRole>> getProperties() {
        List<Property<GeoserverRole>> result = new ArrayList<GeoServerDataProvider.Property<GeoserverRole>>();
        result.add(ROLENAME);
        result.add(PARENTROLENAME);
        result.add(HASROLEPARAMS);
        return result;
    }

}