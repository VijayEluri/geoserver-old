/* Copyright (c) 2001 - 2007 TOPP - www.openplans.org. All rights reserved.
 * This code is licensed under the GPL 2.0 license, available at the root
 * application directory.
 */
package org.geoserver.web.security.user;

import java.io.IOException;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.SortedSet;
import java.util.TreeSet;

import org.apache.wicket.behavior.IBehavior;
import org.apache.wicket.extensions.markup.html.form.palette.Palette;
import org.apache.wicket.extensions.markup.html.form.palette.component.Recorder;
import org.apache.wicket.markup.html.form.ChoiceRenderer;
import org.apache.wicket.markup.html.form.Form;
import org.apache.wicket.markup.html.form.FormComponentPanel;
import org.apache.wicket.markup.html.form.SubmitLink;
import org.apache.wicket.model.LoadableDetachableModel;
import org.apache.wicket.model.PropertyModel;
import org.geoserver.security.GeoServerSecurityManager;
import org.geoserver.security.impl.GeoserverUser;
import org.geoserver.security.impl.GeoserverUserGroup;
import org.geoserver.web.GeoServerApplication;
import org.geoserver.web.security.group.NewGroupPage;

/**
 * A form component that can be used to edit user to group assignments
 */
public class UserGroupFormComponent extends FormComponentPanel<Serializable> {
    private static final long serialVersionUID = 1L;


    Palette<GeoserverUserGroup> groupPalette;
    GeoserverUser user;
    Form<?> form;
    List<GeoserverUserGroup> selectedGroups;

    public List<GeoserverUserGroup> getSelectedGroups() {
        return selectedGroups;
    }

    public UserGroupFormComponent(GeoserverUser user, final Form<?> form ) {
        this(user,form,null);
    }
    public UserGroupFormComponent(GeoserverUser user, final Form<?> form, final IBehavior behavior ) {        
        super("groups");
        this.user=user;
        this.form=form;
                                                        
        try {
            selectedGroups=new ArrayList<GeoserverUserGroup>();
            selectedGroups.addAll(getSecurityManager().getActiveUserGroupService().getGroupsForUser(user));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        PropertyModel<List<GeoserverUserGroup>> model = new 
                PropertyModel<List<GeoserverUserGroup>> (this,"selectedGroups");

        
        
        LoadableDetachableModel<SortedSet<GeoserverUserGroup>> choicesModel = new 
                LoadableDetachableModel<SortedSet<GeoserverUserGroup>> () {
                    private static final long serialVersionUID = 1L;

                    @Override
                    protected SortedSet<GeoserverUserGroup> load() {                        
                        try {
                            SortedSet<GeoserverUserGroup> result=new TreeSet<GeoserverUserGroup>();
                            result.addAll(
                                getSecurityManager().getActiveUserGroupService().getUserGroups());
                            return result;
                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        }                                
                    }            
        };

        if (behavior==null) {
            groupPalette = new Palette<GeoserverUserGroup>(
                "groups", model,choicesModel,
                new ChoiceRenderer<GeoserverUserGroup>("groupname","groupname"), 10, false);
        } else {
            groupPalette = new Palette<GeoserverUserGroup>(
                    "groups", model,choicesModel,
                    new ChoiceRenderer<GeoserverUserGroup>("groupname","groupname"), 10, false) {
                        private static final long serialVersionUID = 1L;

                        @Override
                        protected Recorder<GeoserverUserGroup> newRecorderComponent() {                            
                            Recorder<GeoserverUserGroup> r= super.newRecorderComponent();
                            r.add(behavior);
                            return r;
                        }                                        
            };            
        }
         
                        
            
        groupPalette.setOutputMarkupId(true);
        add(groupPalette);
        
        SubmitLink addGroup = 
          new SubmitLink("addGroup",form) {
            private static final long serialVersionUID = 1L;
            @Override
            public void onSubmit() {
                setResponsePage(new NewGroupPage(this.getPage()));
            }            
          };
        add(addGroup);
                
    }

    GeoServerSecurityManager getSecurityManager() {
        return GeoServerApplication.get().getSecurityManager();
    }

    protected void calculateAddedRemovedCollections(Collection<GeoserverUserGroup> added, Collection<GeoserverUserGroup> removed) {
        SortedSet<GeoserverUserGroup> oldgroups;
        try {
            oldgroups = getSecurityManager().getActiveUserGroupService().getGroupsForUser(user);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        Iterator<GeoserverUserGroup> it = groupPalette.getSelectedChoices();
        
        removed.addAll(oldgroups);
        while (it.hasNext()) {
            GeoserverUserGroup group = it.next();
            if (oldgroups.contains(group)==false)
                added.add(group);
            else
                removed.remove(group);
        }
    }

    @Override
    public void updateModel() {
        groupPalette.getRecorderComponent().updateModel();
    }
    
    public Palette<GeoserverUserGroup> getGroupPalette() {
        return groupPalette;
    }
}