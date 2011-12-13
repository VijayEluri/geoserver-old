package org.geoserver.security.xml;

import java.io.IOException;

import org.geoserver.config.util.XStreamPersister;
import org.geoserver.security.GeoServerAuthenticationProvider;
import org.geoserver.security.GeoServerSecurityProvider;
import org.geoserver.security.GeoserverRoleService;
import org.geoserver.security.GeoserverUserGroupService;
import org.geoserver.security.UsernamePasswordAuthenticationProvider;
import org.geoserver.security.config.PasswordPolicyConfig;
import org.geoserver.security.config.SecurityNamedServiceConfig;
import org.geoserver.security.config.impl.PasswordPolicyConfigImpl;
import org.geoserver.security.config.impl.UsernamePasswordAuthenticationProviderConfig;
import org.geoserver.security.config.impl.XMLFileBasedRoleServiceConfigImpl;
import org.geoserver.security.config.impl.XMLFileBasedUserGroupServiceConfigImpl;
import org.geoserver.security.config.validation.SecurityConfigValidator;
import org.geoserver.security.password.PasswordValidator;
import org.geoserver.security.password.PasswordValidatorImpl;

/**
 * Security provider for default XML-based implementation.
 * 
 * @author Justin Deoliveira, OpenGeo
 */
public class XMLSecurityProvider extends GeoServerSecurityProvider {

    @Override
    public void configure(XStreamPersister xp) {
        super.configure(xp);
        xp.getXStream().alias("usergroupservice", XMLFileBasedUserGroupServiceConfigImpl.class);
        xp.getXStream().alias("roleservice", XMLFileBasedRoleServiceConfigImpl.class);
        xp.getXStream().alias("passwordpolicy", PasswordPolicyConfigImpl.class);
        xp.getXStream().alias("usernamepassword", UsernamePasswordAuthenticationProviderConfig.class);

    }

    @Override
    public Class<? extends GeoserverUserGroupService> getUserGroupServiceClass() {
        return XMLUserGroupService.class;
    }

    @Override
    public GeoserverUserGroupService createUserGroupService(SecurityNamedServiceConfig config) 
        throws IOException {
        return new XMLUserGroupService();
    }

    @Override
    public Class<? extends GeoserverRoleService> getRoleServiceClass() {
        return XMLRoleService.class;
    }

    @Override
    public GeoserverRoleService createRoleService(SecurityNamedServiceConfig config)
            throws IOException {
        return new XMLRoleService();
    }

    /**
     * Create the standard password validator
     * 
     * @param config
     * @return
     */
    public PasswordValidator createPasswordValidator(PasswordPolicyConfig config) {
        return new PasswordValidatorImpl();
    }

    /**
     * Returns the specific class of the password validator created by 
     * {@link #createPasswordValidator(PasswordPolicyConfig))}.
     * <p>
     * If the extension does not provide a user group service this method should simply return
     * <code>null</code>. 
     * </p> 
     */
    public  Class<? extends PasswordValidator> getPasswordValidatorClass() {
        return PasswordValidatorImpl.class;
    }

    /**
     * Creates an authentication provider.
     * <p>
     * If the extension does not provide an authentication provider this method should simply return
     * <code>null</code>.
     * </p> 
     */
    public GeoServerAuthenticationProvider createAuthenticationProvider(SecurityNamedServiceConfig config) {
        return new UsernamePasswordAuthenticationProvider();
    }

    /**
     * Returns the concrete class of authentication provider created by 
     *  {@link #createAuthenticationProvider(SecurityNamedServiceConfig)}.
     * <p>
     * If the extension does not provide an authentication provider this method should simply return
     * <code>null</code>.
     * </p> 
     */
    public Class<? extends GeoServerAuthenticationProvider> getAuthenticationProviderClass() {
        return UsernamePasswordAuthenticationProvider.class;
    }

    @Override
    public boolean roleServiceNeedsLockProtection() {
        return true;
    }
    
    @Override
    public boolean userGroupServiceNeedsLockProtection() {
        return true;
    }

    @Override
    public SecurityConfigValidator getConfigurationValidator() {
        return new XMLSecurityConfigValidator(); 
     }

}
