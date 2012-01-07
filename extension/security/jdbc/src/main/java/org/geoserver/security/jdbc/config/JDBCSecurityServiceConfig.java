/* Copyright (c) 2001 - 2008 TOPP - www.openplans.org. All rights reserved.
 * This code is licensed under the GPL 2.0 license, available at the root
 * application directory.
 */
package org.geoserver.security.jdbc.config;

import org.geoserver.security.config.BaseSecurityNamedServiceConfig;
import org.geoserver.security.config.SecurityNamedServiceConfig;

/**
 * Extension of {@link SecurityNamedServiceConfig} in which the underlying config is stored in 
 * a database accessible via JDBC.
 *
 * @author christian
 */
public class JDBCSecurityServiceConfig extends BaseSecurityNamedServiceConfig {

    private static final long serialVersionUID = 1L;

    private String propertyFileNameDDL;
    private String propertyFileNameDML;
    private String jndiName;
    private boolean jndi;
    private String driverClassName;
    private String connectURL;
    private String userName;
    private String password;

    /**
     * Flag controlling whether to connect through JNDI or through creation of a direct connection. 
     * <p>
     * If set {@link #getJndiName()} is used to obtain the connection.
     * </p>
     */
    public boolean isJndi() {
        return jndi;
    }

    /**
     * Set flag controlling whether to connect through JNDI or through creation of a direct 
     * connection.
     */
    public void setJndi(boolean jndi) {
        this.jndi = jndi;
    }

    /**
     * Name of JNDI resource for database connection.
     * <p>
     * Used if {@link #isJndi()} is set.
     * </p>
     */
    public String getJndiName() {
        return jndiName;
    }

    /**
     * Sets name of JNDI resource for database connection.
     */
    public void setJndiName(String jndiName) {
        this.jndiName = jndiName;
    }

    /**
     * File name of property file containing DDL statements.
     */
    public String getPropertyFileNameDDL() {
        return propertyFileNameDDL;
    }

    /**
     * Sets file name of property file containing DDL statements.
     */
    public void setPropertyFileNameDDL (String propertyFileNameDDL) {
        this.propertyFileNameDDL = propertyFileNameDDL;
    }

    /**
     * File name of property file containing DML statements.
     */
    public String getPropertyFileNameDML() {
        return propertyFileNameDML;
    }

    /**
     * Sets file name of property file containing DML statements.
     */
    public void setPropertyFileNameDML (String propertyFileNameDML) {
        this.propertyFileNameDML = propertyFileNameDML;
    }

    /**
     * The JDBC driver class name.
     * <p>
     * Used only if {@link #isJndi()} is false.
     * </p>
     */
    public String getDriverClassName() {
        return driverClassName;
    }

    /**
     * Sets the JDBC driver class name.
     * 
     */
    public void setDriverClassName(String driverClassName) {
        this.driverClassName = driverClassName;
    }

    /**
     * The JDBC url with which to obtain a database connection with.  
     * <p>
     * Used only if {@link #isJndi()} is false.
     * </p>
     */
    public String getConnectURL() {
        return connectURL;
    }

    /**
     * The JDBC url with which to obtain a database connection with.
     */
    public void setConnectURL(String connectURL) {
        this.connectURL = connectURL;
    }

    /**
     * The database user name.
     * <p>
     * Used only if {@link #isJndi()} is false.
     * </p>
     */
    public String getUserName() {
        return userName;
    }

    /**
     * Sets the database user name.
     */
    public void setUserName(String userName) {
        this.userName = userName;
    }

    /**
     * /**
     * The database password.
     * <p>
     * Used only if {@link #isJndi()} is false.
     * </p>
     */
    public String getPassword() {
        return password;
    }

    /**
     * Sets the database password.
     */
    public void setPassword(String password) {
        this.password = password;
    }

}
