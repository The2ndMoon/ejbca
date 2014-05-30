/*************************************************************************
 *                                                                       *
 *  EJBCA: The OpenSource Certificate Authority                          *
 *                                                                       *
 *  This software is free software; you can redistribute it and/or       *
 *  modify it under the terms of the GNU Lesser General Public           *
 *  License as published by the Free Software Foundation; either         *
 *  version 2.1 of the License, or any later version.                    *
 *                                                                       *
 *  See terms of license at gnu.org.                                     *
 *                                                                       *
 *************************************************************************/
package org.cesecore.keys.token;

import static org.junit.Assert.assertNotNull;

import java.io.File;
import java.util.Properties;

import org.cesecore.CaTestUtils;
import org.cesecore.authentication.tokens.AuthenticationToken;
import org.cesecore.authorization.AuthorizationDeniedException;
import org.cesecore.certificates.ca.CADoesntExistsException;
import org.cesecore.certificates.ca.CaSessionRemote;
import org.cesecore.certificates.ca.X509CA;
import org.cesecore.keys.token.p11.Pkcs11SlotLabelType;
import org.cesecore.keys.token.p11.exception.NoSuchSlotException;
import org.cesecore.util.EjbRemoteHelper;

/**
 * @version $Id$
 *
 */
public class CryptoTokenTestUtils {

    private static final String TOKEN_PIN = "userpin1";

    private static final String UTIMACO_PKCS11_LINUX_LIB = "/etc/utimaco/libcs2_pkcs11.so";
    private static final String UTIMACO_PKCS11_WINDOWS_LIB = "C:/Program Files/Utimaco/SafeGuard CryptoServer/Lib/cs2_pkcs11.dll";
    private static final String LUNASA_PKCS11_LINUX_LIB = "/usr/lunasa/lib/libCryptoki2_64.so";
    private static final String LUNASA_PKCS11_LINUX32_LIB = "/usr/lunasa/lib/libCryptoki2.so";
    private static final String PROTECTSERVER_PKCS11_LINUX_LIB = "/opt/PTK/lib/libcryptoki.so"; // this symlink is set by safeNet-install.sh->"5 Set the default cryptoki and/or hsm link". Use it instead of symlinking manually.
    private static final String PROTECTSERVER_PKCS11_LINUX64_LIB = "/opt/ETcpsdk/lib/linux-x86_64/libcryptoki.so";
    private static final String PROTECTSERVER_PKCS11_LINUX32_LIB = "/opt/ETcpsdk/lib/linux-i386/libcryptoki.so";
    private static final String PROTECTSERVER_PKCS11_WINDOWS_LIB = "C:/Program Files/SafeNet/ProtectToolkit C SDK/bin/sw/cryptoki.dll";
    
    public static X509CA createTestCAWithSoftCryptoToken(AuthenticationToken authenticationToken, String dN) throws Exception {
        CaSessionRemote caSession = EjbRemoteHelper.INSTANCE.getRemoteSession(CaSessionRemote.class);
        CryptoTokenManagementSessionRemote cryptoTokenManagementSession = EjbRemoteHelper.INSTANCE.getRemoteSession(CryptoTokenManagementSessionRemote.class);
        
        X509CA x509ca = CaTestUtils.createTestX509CA(dN, "foo123".toCharArray(), false);
        // Remove any lingering test CA before starting the tests
        try {
            final int oldCaCryptoTokenId = caSession.getCAInfo(authenticationToken, x509ca.getCAId()).getCAToken().getCryptoTokenId();
            cryptoTokenManagementSession.deleteCryptoToken(authenticationToken, oldCaCryptoTokenId);
        } catch (CADoesntExistsException e) {
            // Ok. The old test run cleaned up everything properly.
        }
        caSession.removeCA(authenticationToken, x509ca.getCAId());
        // Now add the test CA so it is available in the tests
        caSession.addCA(authenticationToken, x509ca);
        return x509ca;
    }
    
    public static int createSoftCryptoToken(AuthenticationToken authenticationToken, String cryptoTokenName) throws AuthorizationDeniedException,
            CryptoTokenOfflineException, CryptoTokenAuthenticationFailedException, CryptoTokenNameInUseException, NoSuchSlotException {
        CryptoTokenManagementSessionRemote cryptoTokenManagementSession = EjbRemoteHelper.INSTANCE
                .getRemoteSession(CryptoTokenManagementSessionRemote.class);
        // Remove any old CryptoToken created by this setup
        final Integer oldCryptoTokenId = cryptoTokenManagementSession.getIdFromName(cryptoTokenName);
        if (oldCryptoTokenId != null) {
            cryptoTokenManagementSession.deleteCryptoToken(authenticationToken, oldCryptoTokenId.intValue());
        }
        return cryptoTokenManagementSession.createCryptoToken(authenticationToken, cryptoTokenName, SoftCryptoToken.class.getName(), null, null,
                "foo123".toCharArray());
    }
    
    public static int createPKCS11Token(AuthenticationToken authenticationToken, String cryptoTokenName, boolean useAutoActivationPin)
            throws NoSuchSlotException, AuthorizationDeniedException, CryptoTokenOfflineException, CryptoTokenAuthenticationFailedException,
            CryptoTokenNameInUseException {
        CryptoTokenManagementSessionRemote cryptoTokenManagementSession = EjbRemoteHelper.INSTANCE
                .getRemoteSession(CryptoTokenManagementSessionRemote.class);
        // Remove any old CryptoToken created by this setup
        final Integer oldCryptoTokenId = cryptoTokenManagementSession.getIdFromName(cryptoTokenName);
        if (oldCryptoTokenId != null) {
            cryptoTokenManagementSession.deleteCryptoToken(authenticationToken, oldCryptoTokenId.intValue());
        }
        Properties prop = new Properties();
        String hsmlib = getHSMLibrary();
        assertNotNull(hsmlib);
        prop.setProperty(PKCS11CryptoToken.SHLIB_LABEL_KEY, hsmlib);
        prop.setProperty(PKCS11CryptoToken.SLOT_LABEL_VALUE, "1");
        prop.setProperty(PKCS11CryptoToken.SLOT_LABEL_TYPE, Pkcs11SlotLabelType.SLOT_NUMBER.getKey());
        prop.setProperty(CryptoToken.ALLOW_EXTRACTABLE_PRIVATE_KEY, "True");     
        CryptoToken cryptoToken = CryptoTokenFactory.createCryptoToken(PKCS11CryptoToken.class.getName(), prop, null, 111, "P11 CryptoToken");
        Properties cryptoTokenProperties = cryptoToken.getProperties();
        if (useAutoActivationPin) {
            cryptoTokenProperties.setProperty(CryptoToken.AUTOACTIVATE_PIN_PROPERTY, TOKEN_PIN);
        }
        cryptoToken.setProperties(cryptoTokenProperties);
        return cryptoTokenManagementSession.createCryptoToken(authenticationToken, cryptoTokenName, SoftCryptoToken.class.getName(), null, null,
                "foo123".toCharArray());
    }
    
    public static String getHSMLibrary() {
        final File utimacoCSLinux = new File(UTIMACO_PKCS11_LINUX_LIB);
        final File utimacoCSWindows = new File(UTIMACO_PKCS11_WINDOWS_LIB);
        final File lunaSALinux64 = new File(LUNASA_PKCS11_LINUX_LIB);
        final File lunaSALinux32 = new File(LUNASA_PKCS11_LINUX32_LIB);
        final File protectServerLinux = new File(PROTECTSERVER_PKCS11_LINUX_LIB);
        final File protectServerLinux64 = new File(PROTECTSERVER_PKCS11_LINUX64_LIB);
        final File protectServerLinux32 = new File(PROTECTSERVER_PKCS11_LINUX32_LIB);
        final File protectServerWindows = new File(PROTECTSERVER_PKCS11_WINDOWS_LIB);
        String ret = null;
        if (utimacoCSLinux.exists()) {
            ret = utimacoCSLinux.getAbsolutePath();
        } else if (utimacoCSWindows.exists()) {
            ret = utimacoCSWindows.getAbsolutePath();
        } else if (lunaSALinux64.exists()) {
            ret = lunaSALinux64.getAbsolutePath();
        } else if (lunaSALinux32.exists()) {
            ret = lunaSALinux32.getAbsolutePath();
        } else if (protectServerLinux64.exists()) {
            ret = protectServerLinux64.getAbsolutePath();
        } else if (protectServerLinux32.exists()) {
            ret = protectServerLinux32.getAbsolutePath();
        } else if (protectServerLinux.exists()) {
            ret = protectServerLinux.getAbsolutePath();
        } else if (protectServerWindows.exists()) {
            ret = protectServerWindows.getAbsolutePath();
        }
        return ret;
    }
}
