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

package org.ejbca.ui.web.protocol.ocsp;

import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;
import java.security.KeyStore.PasswordProtection;
import java.util.Set;

import org.apache.log4j.Logger;
import org.ejbca.config.OcspConfiguration;
import org.ejbca.ui.web.protocol.OCSPData;
import org.ejbca.util.keystore.P11Slot;

/**
 * The data of the session.
 * 
 * @author primelars
 * @version  $Id$
 *
 */
class SessionData {
    SessionData(P11Slot _slot, OCSPData _data, String _webURL, int renewTimeBeforeCertExpiresInSeconds, String storePassword, String _cardPassword, String keystoreDirectoryName, Set<String> _keyAlias, boolean _doNotStorePasswordsInMemory, String p11Password) {
        this.slot = _slot;
        this.data = _data;
        this.webURL = _webURL;
        this.mRenewTimeBeforeCertExpiresInSeconds = renewTimeBeforeCertExpiresInSeconds;
        this.mStorePassword = storePassword;
        this.cardPassword = _cardPassword;
        this.mKeystoreDirectoryName = keystoreDirectoryName;
        this.keyAlias = _keyAlias;
        this.doNotStorePasswordsInMemory = _doNotStorePasswordsInMemory;
        this.mP11Password = p11Password;
    }
    /**
     * Log object.
     */
    static final private Logger m_log = Logger.getLogger(SessionData.class);
    /**
     * Reference to the object that all information about the PKCS#11 slot.
     */
    final P11Slot slot;
    /**
     * {@link #isNotReloadingP11Keys} tells if the servlet is ready to be used. It is false during the time when the HSM has failed until its keys is reloaded.
     */
    boolean isNotReloadingP11Keys=true;
    /**
     * Reference to the servlet object data.
     */
    final OCSPData data;
    /**
     * User password for the PKCS#11 slot. Used to logon to the slot.
     */
    String mP11Password;
    /**
     * Password should not be stored in memory if this is true.
     */
    final boolean doNotStorePasswordsInMemory;
    /**
     * The URL of the EJBCAWS used when "rekeying" is activated.
     */
    final String webURL;
    /**
     * The time before a OCSP signing certificate will expire that it should be removed.
     */
    final int mRenewTimeBeforeCertExpiresInSeconds;
    /**
     * The password to all soft key stores.
     */
    String mStorePassword;
    /**
     * Password for all soft keys.
     */
    String mKeyPassword = OcspConfiguration.getKeyPassword();
    /**
     * Class name for "card" implementation.
     */
    final String hardTokenClassName = OcspConfiguration.getHardTokenClassName();
    /**
     * Card password.
     */
    String cardPassword;
    /**
     * The directory containing all soft keys (p12s or jks) and all card certificates.
     */
    final String mKeystoreDirectoryName;
    /**
     * 
     */
    final Set<String> keyAlias;
    /**
     * Set time for next key update.
     * @param currentTime the time from which the to measure next update.
     */
    void setNextKeyUpdate(final long currentTime) {
        // Update cache time
        // If getSigningCertsValidTime() == 0 we set reload time to Long.MAX_VALUE, which should be forever, so the cache is never refreshed
        this.data.mKeysValidTo = OcspConfiguration.getSigningCertsValidTime()>0 ? currentTime+OcspConfiguration.getSigningCertsValidTime() : Long.MAX_VALUE;
        m_log.debug("time: "+currentTime+" next update: "+this.data.mKeysValidTo);
    }
    /**
     * Gets the P11 slot user password used to logon to a P11 session.
     * @param password Password to be used. Set to null if configured should be used
     * @return The password.
     */
    public PasswordProtection getP11Pwd(String password) {
        if ( password!=null ) {
            if ( this.mP11Password!=null ) {
                 m_log.error("Trying to activate even tought password has been configured.");
                 return null;
            }
            return new PasswordProtection(password.toCharArray());
        }
        if ( this.mP11Password!=null ) {
            return new PasswordProtection(this.mP11Password.toCharArray());
        }
        return null;
    }
    /**
     * Tells if we should renew a key before the certificate expires.
     * @return true if we should renew the key.
     */
    boolean doKeyRenewal() {
        return this.webURL!=null && this.webURL.length()>0 && this.mRenewTimeBeforeCertExpiresInSeconds>=0;
    }
    /**
     * Tests a key.
     * @param privateKey The private part of the key.
     * @param publicKey The public part of the key.
     * @param alias The alias of the for the key. Just used for debug output.
     * @param providerName The provider name.
     * @return True if the key is OK.
     * @throws Exception
     */
    static boolean signTest(PrivateKey privateKey, PublicKey publicKey, String alias, String providerName) throws Exception {
        final String sigAlgName = "SHA1withRSA";
        final byte signInput[] = "Lillan gick on roaden ut.".getBytes();
        final byte signBA[];
        {
            final Signature signature = Signature.getInstance(sigAlgName, providerName);
            signature.initSign( privateKey );
            signature.update( signInput );
            signBA = signature.sign();
        }
        {
            final Signature signature = Signature.getInstance(sigAlgName);
            signature.initVerify(publicKey);
            signature.update(signInput);
            final boolean result = signature.verify(signBA);
            if (m_log.isDebugEnabled()) {
                m_log.debug("Signature test of key "+alias+
                        ": signature length " + signBA.length +
                        "; first byte " + Integer.toHexString(0xff&signBA[0]) +
                        "; verifying " + result);               
            }
            return result;
        }
    }
    /* (non-Javadoc)
     * @see java.lang.Object#finalize()
     */
    @Override
    protected void finalize() throws Throwable {
        m_log.info("Object finalized");
        super.finalize();
    }
}
