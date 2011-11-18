/* Copyright (c) 2001 - 2012 TOPP - www.openplans.org. All rights reserved.
 * This code is licensed under the GPL 2.0 license, available at the root
 * application directory.
 */
package org.geoserver.security.ldap;

import java.security.AuthProvider;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLSession;

import org.geoserver.config.util.XStreamPersister;
import org.geoserver.security.GeoServerSecurityProvider;
import org.geoserver.security.config.SecurityNamedServiceConfig;
import org.springframework.ldap.core.support.DefaultTlsDirContextAuthenticationStrategy;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.ldap.DefaultSpringSecurityContextSource;
import org.springframework.security.ldap.authentication.BindAuthenticator;
import org.springframework.security.ldap.authentication.LdapAuthenticationProvider;
import org.springframework.security.ldap.authentication.SpringSecurityAuthenticationSource;
import org.springframework.security.ldap.userdetails.DefaultLdapAuthoritiesPopulator;
import org.springframework.security.ldap.userdetails.LdapAuthoritiesPopulator;

public class LDAPSecurityProvider extends GeoServerSecurityProvider {

    @Override
    public void configure(XStreamPersister xp) {
        xp.getXStream().alias("ldap", LDAPSecurityServiceConfig.class);
    }

    @Override
    public Class<? extends AuthenticationProvider> getAuthenticationProviderClass() {
        return LdapAuthenticationProvider.class;
    }
    
    @Override
    public AuthenticationProvider createAuthenticationProvider(SecurityNamedServiceConfig config) {
        LDAPSecurityServiceConfig ldapConfig = (LDAPSecurityServiceConfig) config;
        
        DefaultSpringSecurityContextSource ldapContext = 
                new DefaultSpringSecurityContextSource(ldapConfig.getServerURL());
        ldapContext.setCacheEnvironmentProperties(false);
        ldapContext.setAuthenticationSource(new SpringSecurityAuthenticationSource());
        
        if (ldapConfig.isUseTLS()) {
            //TLS does not play nicely with pooled connections 
            ldapContext.setPooled(false);

            DefaultTlsDirContextAuthenticationStrategy tls = 
                new DefaultTlsDirContextAuthenticationStrategy();
            tls.setHostnameVerifier(new HostnameVerifier() {
                @Override
                public boolean verify(String hostname, SSLSession session) {
                    return true;
                }
            });

            ldapContext.setAuthenticationStrategy(tls);
        }

        BindAuthenticator authenticator = new BindAuthenticator(ldapContext);
        authenticator.setUserDnPatterns(new String[]{ldapConfig.getUserDnPattern()});
        
        DefaultLdapAuthoritiesPopulator authPopulator = 
            new DefaultLdapAuthoritiesPopulator(ldapContext, ldapConfig.getGroupSearchBase());
        if (ldapConfig.getGroupSearchFilter() != null) {
            authPopulator.setGroupSearchFilter(ldapConfig.getGroupSearchFilter());
        }

        return new LdapAuthenticationProvider(authenticator, authPopulator);
    }
}
