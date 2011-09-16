/*************************************************************************
 *                                                                       *
 *  CESeCore: CE Security Core                                           *
 *                                                                       *
 *  This software is free software; you can redistribute it and/or       *
 *  modify it under the terms of the GNU Lesser General Public           *
 *  License as published by the Free Software Foundation; either         *
 *  version 2.1 of the License, or any later version.                    *
 *                                                                       *
 *  See terms of license at gnu.org.                                     *
 *                                                                       *
 *************************************************************************/
package org.cesecore.certificates.certificate;

import java.math.BigInteger;
import java.security.cert.Certificate;
import java.util.Collection;
import java.util.List;

import javax.ejb.Remote;

import org.cesecore.authentication.tokens.AuthenticationToken;
import org.cesecore.authorization.AuthorizationDeniedException;

/**
 * This session bean should under no circumstances be included in the release version of CESeCore.
 * It allows removal of certificates, and may be used only for functional tests to clean up after
 * themselves.
 * 
 * @version $Id$
 */
@Remote
public interface InternalCertificateStoreSessionRemote {
    /**
     * This method removes the given certificate(s) by serial number.
     * 
     * @param serno Serial number of the certificate(s) to remove.
     */
    void removeCertificate(BigInteger serno);

    /**
     * This method removes the given certificate(s) by fingerprint (primary key).
     * @see org.cesecore.util.CertTools#getFingerprintAsString(java.lang.String)
     * 
     * @param fingerprint fingerprint of the certificate(s) to remove.
     */
    void removeCertificate(String fingerprint);

    /**
     * Removes the given {@link Certificate} by its fingerprint.
     * 
     * @param certificate The Certificate whose corresponding CertificateData is to be removed.
     */
    void removeCertificate(Certificate certificate);
    
    /** To allow testing of Local-only method */
    List<Object[]> findExpirationInfo(Collection<String> cas, long activeNotifiedExpireDateMin, long activeNotifiedExpireDateMax, long activeExpireDateMin);
     
    Collection<Certificate> findCertificatesByIssuer(String issuerDN);

	/**
	 * Removes a CRL from the database, does not throw any errors if the CRL does not exist.
	 *
	 * @param admin Administrator performing the operation
	 * @param fingerprint the fingerprint of the CRL to remove
	 * 
     * @throws AuthorizationDeniedException (rollback) if admin was not authorized to remove CRL
	 */
	void removeCRL(final AuthenticationToken admin, final String fingerprint) throws AuthorizationDeniedException;

}
