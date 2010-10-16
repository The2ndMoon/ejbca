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
package org.ejbca.ui.web.protocol;

import java.math.BigInteger;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;

import org.apache.log4j.Logger;
import org.ejbca.config.OcspConfiguration;
import org.ejbca.core.ejb.ca.store.CertificateStatus;
import org.ejbca.core.model.log.Admin;
import org.ejbca.core.protocol.ocsp.CertificateCache;
import org.ejbca.util.CertTools;

/**
 * Data to be used both in servlet and session object.
 * 
 * @author primelars
 * @version  $Id$
 *
 */
abstract public class OCSPData {

    private static final Logger m_log = Logger.getLogger(OCSPData.class);
    
    public final Admin m_adm = new Admin(Admin.TYPE_INTERNALUSER);

    /** Cache time counter, set and used by loadPrivateKeys (external responder) */
    public long mKeysValidTo = 0;

    /** Cache of CA certificates (and chain certs) for CAs handles by this responder */
    public CertificateCache m_caCertCache = null;

    /** String used to identify default responder id, used to generate responses when a request
     * for a certificate not signed by a CA on this server is received.
     */
    final String m_defaultResponderId = OcspConfiguration.getDefaultResponderId();

    /** Generates an EJBCA caid from a CA certificate, or looks up the default responder certificate.
     * 
     * @param cacert the CA certificate to get the CAid from. If this is null, the default responder CA cert  is looked up and used
     * @return int 
     */
     public int getCaid( X509Certificate cacert ) {
        X509Certificate cert = cacert;
        if (cacert == null) {
            m_log.debug("No correct CA-certificate available to sign response, signing with default CA: "+this.m_defaultResponderId);
            cert = this.m_caCertCache.findLatestBySubjectDN(this.m_defaultResponderId);           
        }

        int result = CertTools.stringToBCDNString(cert.getSubjectDN().toString()).hashCode();
        if (m_log.isDebugEnabled()) {
            m_log.debug( cert.getSubjectDN() + " has caid: " + result );
        }
        return result;
    }
    /**
     * Get revocation status of a certificate
     * @param issuerDN
     * @param serialNumber
     * @return the status
     */
    abstract public CertificateStatus getStatus(String issuerDN, BigInteger serialNumber);
    /**
     * Search for certificate.
     * @param adm
     * @param issuerDN
     * @param serno
     * @return the certificate
     */
    abstract protected Certificate findCertificateByIssuerAndSerno(Admin adm, String issuerDN, BigInteger serno);
}
