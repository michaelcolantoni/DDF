/**
 * Copyright (c) Codice Foundation
 * <p/>
 * This is free software: you can redistribute it and/or modify it under the terms of the GNU Lesser
 * General Public License as published by the Free Software Foundation, either version 3 of the
 * License, or any later version.
 * <p/>
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without
 * even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details. A copy of the GNU Lesser General Public License
 * is distributed along with this program and can be found at
 * <http://www.gnu.org/licenses/lgpl.html>.
 */

package ddf.ldap.ldaplogin;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.HashSet;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.net.ssl.SSLContext;
import javax.security.auth.Subject;
import javax.security.auth.callback.Callback;
import javax.security.auth.callback.CallbackHandler;
import javax.security.auth.callback.NameCallback;
import javax.security.auth.callback.PasswordCallback;
import javax.security.auth.callback.UnsupportedCallbackException;
import javax.security.auth.login.LoginException;

import org.apache.commons.lang.StringUtils;
import org.apache.karaf.jaas.boot.principal.RolePrincipal;
import org.apache.karaf.jaas.boot.principal.UserPrincipal;
import org.apache.karaf.jaas.modules.AbstractKarafLoginModule;
import org.forgerock.opendj.ldap.Attribute;
import org.forgerock.opendj.ldap.ByteString;
import org.forgerock.opendj.ldap.Connection;
import org.forgerock.opendj.ldap.LDAPConnectionFactory;
import org.forgerock.opendj.ldap.LDAPOptions;
import org.forgerock.opendj.ldap.LdapException;
import org.forgerock.opendj.ldap.SearchResultReferenceIOException;
import org.forgerock.opendj.ldap.SearchScope;
import org.forgerock.opendj.ldap.requests.BindRequest;
import org.forgerock.opendj.ldap.requests.DigestMD5SASLBindRequest;
import org.forgerock.opendj.ldap.requests.GSSAPISASLBindRequest;
import org.forgerock.opendj.ldap.requests.Requests;
import org.forgerock.opendj.ldap.responses.BindResult;
import org.forgerock.opendj.ldap.responses.SearchResultEntry;
import org.forgerock.opendj.ldif.ConnectionEntryReader;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.FrameworkUtil;
import org.osgi.framework.ServiceReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ddf.security.common.audit.SecurityLogger;
import ddf.security.encryption.EncryptionService;

public class SslLdapLoginModule extends AbstractKarafLoginModule {

    public static final String CONNECTION_URL = "connection.url";

    public static final String CONNECTION_USERNAME = "connection.username";

    public static final String CONNECTION_PASSWORD = "connection.password";

    public static final String USER_BASE_DN = "user.base.dn";

    public static final String USER_FILTER = "user.filter";

    public static final String USER_SEARCH_SUBTREE = "user.search.subtree";

    public static final String ROLE_BASE_DN = "role.base.dn";

    public static final String ROLE_FILTER = "role.filter";

    public static final String ROLE_NAME_ATTRIBUTE = "role.name.attribute";

    public static final String ROLE_SEARCH_SUBTREE = "role.search.subtree";

    public static final String SSL_STARTTLS = "ssl.starttls";

    public static final String BIND_METHOD = "bindMethod";

    public static final String REALM = "realm";

    public static final String KDC_ADDRESS = "kdcAddress";

    public static final String PROTOCOL = "TLS";

    private static final Logger LOGGER = LoggerFactory.getLogger(SslLdapLoginModule.class);

    private static final String DEFAULT_AUTHENTICATION = "simple";

    private String realm;

    private String kdcAddress;

    private String bindMethod;

    private String connectionURL;

    private String connectionUsername;

    private String connectionPassword;

    private String userBaseDN;

    private String userFilter;

    private boolean userSearchSubtree = true;

    private String roleBaseDN;

    private String roleFilter;

    private String roleNameAttribute;

    private boolean roleSearchSubtree = true;

    private String authentication = DEFAULT_AUTHENTICATION;

    private boolean startTls = false;

    private LDAPConnectionFactory ldapConnectionFactory;

    protected boolean doLogin() throws LoginException {
        Callback[] callbacks = new Callback[2];
        callbacks[0] = new NameCallback("Username: ");
        callbacks[1] = new PasswordCallback("Password: ", false);

        try {
            callbackHandler.handle(callbacks);
        } catch (IOException ioException) {
            throw new LoginException(ioException.getMessage());
        } catch (UnsupportedCallbackException unsupportedCallbackException) {
            throw new LoginException(unsupportedCallbackException.getMessage()
                    + " not available to obtain information from user.");
        }

        user = ((NameCallback) callbacks[0]).getName();
        if (user == null) {
            return false;
        }

        char[] tmpPassword = ((PasswordCallback) callbacks[1]).getPassword();

        // If either a username or password is specified don't allow authentication = "none".
        // This is to prevent someone from logging into Karaf as any user without providing a
        // valid password (because if authentication = none, the password could be any
        // value - it is ignored).
        if ("none".equals(authentication) && (tmpPassword != null)) {
            LOGGER.debug(
                    "Changing from authentication = none to simple since user or password was specified.");
            // default to simple so that the provided user/password will get checked
            authentication = "simple";
        }

        if (tmpPassword == null) {
            tmpPassword = new char[0];
        }
        principals = new HashSet<>();

        Connection connection = null;
        String userDn = null;
        try {
            connection = ldapConnectionFactory.getConnection();
        } catch (LdapException e) {
            LOGGER.info("Unable to get LDAP Connection from factory.", e);
            return false;
        }
        if (connection != null) {
            try {
                try {
                    BindRequest request;
                    switch (bindMethod) {
                    case "Simple":
                        request = Requests.newSimpleBindRequest(connectionUsername,
                                connectionPassword.toCharArray());
                        break;
                    case "SASL":
                        request = Requests.newPlainSASLBindRequest(connectionUsername,
                                connectionPassword.toCharArray());
                        break;
                    case "GSSAPI SASL":
                        request = Requests.newGSSAPISASLBindRequest(connectionUsername,
                                connectionPassword.toCharArray());
                        ((GSSAPISASLBindRequest) request).setRealm(realm);
                        ((GSSAPISASLBindRequest) request).setKDCAddress(kdcAddress);
                        break;
                    case "Digest MD5 SASL":
                        request = Requests.newDigestMD5SASLBindRequest(connectionUsername,
                                connectionPassword.toCharArray());
                        ((DigestMD5SASLBindRequest) request).setCipher(DigestMD5SASLBindRequest.CIPHER_HIGH);
                        ((DigestMD5SASLBindRequest) request).getQOPs().clear();
                        ((DigestMD5SASLBindRequest) request).getQOPs().add(DigestMD5SASLBindRequest.QOP_AUTH_CONF);
                        ((DigestMD5SASLBindRequest) request).getQOPs().add(DigestMD5SASLBindRequest.QOP_AUTH_INT);
                        ((DigestMD5SASLBindRequest) request).getQOPs().add(DigestMD5SASLBindRequest.QOP_AUTH);
                        if (StringUtils.isNotEmpty(realm)) {
                            ((DigestMD5SASLBindRequest) request).setRealm(realm);
                        }
                        break;
                    default:
                        request = Requests.newSimpleBindRequest(connectionUsername,
                                connectionPassword.toCharArray());
                        break;
                    }
                    BindResult bindResult = connection.bind(request);

                    if (!bindResult.isSuccess()) {
                        LOGGER.debug("Bind failed");
                        return false;
                    }
                } catch (LdapException e) {
                    LOGGER.debug("Unable to bind to LDAP server.", e);
                    return false;
                }
                SearchScope scope;
                if (userSearchSubtree) {
                    scope = SearchScope.WHOLE_SUBTREE;
                } else {
                    scope = SearchScope.SINGLE_LEVEL;
                }
                userFilter = userFilter.replaceAll(Pattern.quote("%u"),
                        Matcher.quoteReplacement(user));
                userFilter = userFilter.replace("\\", "\\\\");
                ConnectionEntryReader entryReader = connection.search(userBaseDN, scope,
                        userFilter);
                try {
                    if (!entryReader.hasNext()) {
                        LOGGER.info("User {} not found in LDAP.", user);
                        return false;
                    }
                    SearchResultEntry searchResultEntry = entryReader.readEntry();

                    userDn = searchResultEntry.getName()
                            .toString();

                } catch (LdapException | SearchResultReferenceIOException e) {
                    LOGGER.info("Unable to read contents of LDAP user search.", e);
                    return false;
                }
            } finally {
                connection.close();
            }
        } else {
            return false;
        }

        try {
            connection = ldapConnectionFactory.getConnection();
        } catch (LdapException e) {
            LOGGER.info("Unable to get LDAP Connection from factory.", e);
            return false;
        }

        if (connection != null) {
            try {
                BindResult bindResult = connection.bind(userDn, tmpPassword);

                if (!bindResult.isSuccess()) {
                    LOGGER.info("Bind failed");
                    return false;
                }
            } catch (Exception e) {
                LOGGER.info("Unable to bind user to LDAP server.", e);
                return false;
            } finally {
                connection.close();
            }
            principals.add(new UserPrincipal(user));
        } else {
            return false;
        }

        try {
            connection = ldapConnectionFactory.getConnection();
        } catch (LdapException e) {
            LOGGER.info("Unable to get LDAP Connection from factory.", e);
            return false;
        }

        if (connection != null) {
            try {
                try {
                    BindResult bindResult = connection.bind(connectionUsername,
                            connectionPassword.toCharArray());

                    if (!bindResult.isSuccess()) {
                        LOGGER.info("Bind failed");
                        return false;
                    }
                } catch (LdapException e) {
                    LOGGER.info("Unable to bind to LDAP server.", e);
                    return false;
                }
                SearchScope scope;
                if (roleSearchSubtree) {
                    scope = SearchScope.WHOLE_SUBTREE;
                } else {
                    scope = SearchScope.SINGLE_LEVEL;
                }
                roleFilter = roleFilter.replaceAll(Pattern.quote("%u"),
                        Matcher.quoteReplacement(user));
                roleFilter = roleFilter.replaceAll(Pattern.quote("%dn"),
                        Matcher.quoteReplacement(userBaseDN));
                roleFilter = roleFilter.replaceAll(Pattern.quote("%fqdn"),
                        Matcher.quoteReplacement(userDn));
                roleFilter = roleFilter.replace("\\", "\\\\");
                ConnectionEntryReader entryReader = connection.search(roleBaseDN, scope, roleFilter,
                        roleNameAttribute);
                SearchResultEntry entry;
                try {
                    while (entryReader.hasNext()) {
                        entry = entryReader.readEntry();
                        Attribute attr = entry.getAttribute(roleNameAttribute);
                        for (ByteString role : attr) {
                            principals.add(new RolePrincipal(role.toString()));
                        }
                    }
                } catch (Exception e) {
                    throw new LoginException(
                            "Can't get user " + user + " roles: " + e.getMessage());
                }
            } finally {
                connection.close();
            }
        } else {
            return false;
        }

        return true;
    }

    @Override
    public boolean abort() throws LoginException {
        return true;
    }

    @Override
    public boolean logout() throws LoginException {
        subject.getPrincipals()
                .removeAll(principals);
        principals.clear();
        ldapConnectionFactory.close();
        ldapConnectionFactory = null;
        return true;
    }

    protected BundleContext getContext() {
        Bundle cxfBundle = FrameworkUtil.getBundle(SslLdapLoginModule.class);
        if (cxfBundle != null) {
            return cxfBundle.getBundleContext();
        }
        return null;
    }

    @Override
    public void initialize(Subject subject, CallbackHandler callbackHandler,
            Map<String, ?> sharedState, Map<String, ?> options) {
        super.initialize(subject, callbackHandler, options);
        BundleContext bundleContext = getContext();
        if (null != bundleContext) {
            ServiceReference ref = null;
            try {
                Map<String, String> option = (Map<String, String>) options;

                ref = bundleContext.getServiceReference(EncryptionService.class.getName());
                EncryptionService encryptionService = (EncryptionService) bundleContext.getService(
                        ref);

                if (encryptionService != null) {
                    String decryptedPassword = encryptionService.decryptValue(
                            option.get(CONNECTION_PASSWORD));
                    option.put(CONNECTION_PASSWORD, decryptedPassword);
                } else {
                    LOGGER.info("Encryption service reference for ldap was null.");
                }

            } catch (SecurityException | IllegalStateException e) {
                LOGGER.info(
                        "Error decrypting connection password passed into ldap configuration: ", e);
            } finally {
                if (ref != null) {
                    bundleContext.ungetService(ref);
                }
            }
            connectionURL = (String) options.get(CONNECTION_URL);
            connectionUsername = (String) options.get(CONNECTION_USERNAME);
            connectionPassword = (String) options.get(CONNECTION_PASSWORD);
            userBaseDN = (String) options.get(USER_BASE_DN);
            userFilter = (String) options.get(USER_FILTER);
            userSearchSubtree = Boolean.parseBoolean((String) options.get(USER_SEARCH_SUBTREE));
            roleBaseDN = (String) options.get(ROLE_BASE_DN);
            roleFilter = (String) options.get(ROLE_FILTER);
            roleNameAttribute = (String) options.get(ROLE_NAME_ATTRIBUTE);
            roleSearchSubtree = Boolean.parseBoolean((String) options.get(ROLE_SEARCH_SUBTREE));
            startTls = Boolean.parseBoolean(String.valueOf(options.get(SSL_STARTTLS)));
            bindMethod = (String) options.get(BIND_METHOD);
            realm = (String) options.get(REALM);
            kdcAddress = (String) options.get(KDC_ADDRESS);

            if (ldapConnectionFactory != null) {
                ldapConnectionFactory.close();
            }

            try {
                ldapConnectionFactory = createLdapConnectionFactory(connectionURL, startTls);
            } catch (LdapException e) {
                LOGGER.info(
                        "Unable to create LDAP Connection Factory. LDAP log in will not be possible.",
                        e);
            }
        } else {
            LOGGER.info("Unable to retrieve Bundle Context!");
        }
    }

    protected LDAPConnectionFactory createLdapConnectionFactory(String url, Boolean startTls)
            throws LdapException {
        boolean useSsl = url.startsWith("ldaps");
        boolean useTls = !url.startsWith("ldaps") && startTls;

        LDAPOptions lo = new LDAPOptions();

        try {
            if (useSsl || useTls) {
                SSLContext sslContext = SSLContext.getDefault();
                lo.setSSLContext(sslContext);
            }
        } catch (GeneralSecurityException e) {
            LOGGER.info("Error encountered while configuring SSL. Secure connection will fail.",
                    e);
        }

        lo.setUseStartTLS(useTls);
        lo.addEnabledCipherSuite(System.getProperty("https.cipherSuites")
                .split(","));
        lo.addEnabledProtocol(System.getProperty("https.protocols")
                .split(","));
        lo.setProviderClassLoader(SslLdapLoginModule.class.getClassLoader());

        String host = url.substring(url.indexOf("://") + 3, url.lastIndexOf(":"));
        Integer port = useSsl ? 636 : 389;
        try {
            port = Integer.valueOf(url.substring(url.lastIndexOf(":") + 1));
        } catch (NumberFormatException ignore) {
        }

        return new LDAPConnectionFactory(host, port, lo);
    }

    /**
     * Added additional logging to the security LOGGER.
     */
    @Override
    public boolean login() throws LoginException {
        try {
            boolean isLoggedIn = doLogin();

            if (!isLoggedIn) {
                SecurityLogger.audit("Username [" + user + "] failed LDAP authentication.");
            }
            return isLoggedIn;

        } catch (Exception le) {
            throw new LoginException("Username [" + user
                    + "] could not log in successfuly using LDAP authentication due to an exception");
        }
    }
}
