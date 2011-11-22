/* Copyright (c) 2001 - 2007 TOPP - www.openplans.org. All rights reserved.
 * This code is licensed under the GPL 2.0 license, available at the root
 * application directory.
 */

package org.geoserver.web.security.config;

    



import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.apache.wicket.extensions.ajax.markup.html.tabs.AjaxTabbedPanel;
import org.apache.wicket.extensions.markup.html.tabs.AbstractTab;
import org.apache.wicket.extensions.markup.html.tabs.ITab;
import org.apache.wicket.extensions.markup.html.tabs.TabbedPanel;
import org.apache.wicket.markup.html.form.SubmitLink;
import org.apache.wicket.markup.html.panel.Panel;
import org.apache.wicket.model.ResourceModel;
import org.geoserver.security.GeoserverUserGroupService;
import org.geoserver.security.config.SecurityUserGoupServiceConfig;
import org.geoserver.security.config.impl.SecurityNamedServiceConfigImpl;
import org.geoserver.web.GeoServerApplication;
import org.geoserver.web.security.AbstractSecurityPage;
import org.geoserver.web.security.group.GroupPanel;
import org.geoserver.web.security.user.UserPanel;


public class UserGroupTabbedPage extends AbstractSecurityPage {
    
    protected TabbedPanel tabbedPanel;
    protected SubmitLink saveLink;
    protected String serviceName;
    

    public UserGroupTabbedPage(AbstractSecurityPage responsePage) {
        this(null,responsePage);
    }
    

    public UserGroupTabbedPage(final String serviceName,AbstractSecurityPage responsePage) {
        this.serviceName=serviceName;
        add(getCancelLink(responsePage));
        add(saveLink(responsePage));        
        initializeComponents();        
    }
    
    protected void initializeComponents() {        
        List<ITab> tabs = new ArrayList<ITab>();
        
        // add the config panel first
        tabs.add(new AbstractTab(new ResourceModel("config")) {            
            private static final long serialVersionUID = 1L;

            @Override
            public Panel getPanel(String panelId) {
                try {
                    SecurityUserGoupServiceConfig config = 
                            GeoServerApplication.get().getSecurityManager().loadUserGroupServiceConfig(serviceName);
                    SecurityConfigModelHelper helper = null;
                    if (config==null)
                        helper = new SecurityConfigModelHelper(new SecurityNamedServiceConfigImpl(),true);
                    else
                        helper = new SecurityConfigModelHelper(config,false);
                    // TODO, switch implementation
                    return  new XMLNamedConfigPanel(panelId,helper,GeoserverUserGroupService.class);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }
        }); 

        
        tabs.add(new AbstractTab(new ResourceModel("users")) {            
            private static final long serialVersionUID = 1L;

            @Override
            public Panel getPanel(String panelId) {
                try {
                    return  new UserPanel(panelId,serviceName);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }
        }); 

        tabs.add(new AbstractTab(new ResourceModel("groups")) {            
            private static final long serialVersionUID = 1L;

            @Override
            public Panel getPanel(String panelId) {
                try {
                    return new GroupPanel(panelId,serviceName);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }
        }); 

        Integer selectedTab = null;
        if (tabbedPanel!=null)
            selectedTab = tabbedPanel.getSelectedTab();        
        addOrReplace(tabbedPanel=new AjaxTabbedPanel(this.getClass().getSimpleName(), tabs));
        if (selectedTab!=null) {
            tabbedPanel.setSelectedTab(selectedTab);
        }     
        
    }
    
    
    SubmitLink saveLink(final AbstractSecurityPage responsePage) {
        return new SubmitLink("save") {
            @Override
            public void onSubmit() {
                // TODO store
                responsePage.setDirty(true);
                setResponsePage(responsePage);

            }
        };
    }

        
    @Override
    protected void onBeforeRender() {
        if (isDirty()) {
            initializeComponents();
            setDirty(false);
        }
        super.onBeforeRender();

    }
}