/* Copyright (c) 2001 - 2012 TOPP - www.openplans.org. All rights reserved.
 * This code is licensed under the GPL 2.0 license, available at the root
 * application directory.
 */
package org.geoserver.security.ldap;

import org.geoserver.security.config.BaseSecurityNamedServiceConfig;

public class LDAPSecurityServiceConfig extends BaseSecurityNamedServiceConfig {

    String serverURL;
    String userDnPattern;
    String groupSearchBase;
    String groupSearchFilter;
    String userGroupService;
    Boolean useTLS;

    public String getServerURL() {
        return serverURL;
    }
    public void setServerURL(String serverURL) {
        this.serverURL = serverURL;
    }

    public String getUserDnPattern() {
        return userDnPattern;
    }
    public void setUserDnPattern(String userDnPattern) {
        this.userDnPattern = userDnPattern;
    }

    public String getGroupSearchBase() {
        return groupSearchBase;
    }
    public void setGroupSearchBase(String groupSearchBase) {
        this.groupSearchBase = groupSearchBase;
    }

    public String getGroupSearchFilter() {
        return groupSearchFilter;
    }
    public void setGroupSearchFilter(String groupSearchFilter) {
        this.groupSearchFilter = groupSearchFilter;
    }
    public void setUseTLS(Boolean useTLS) {
        this.useTLS = useTLS;
    }
    public Boolean isUseTLS() {
        return useTLS;
    }

    public String getUserGroupService() {
        return userGroupService;
    }
    public void setUserGroupService(String userGroupService) {
        this.userGroupService = userGroupService;
    }
}
