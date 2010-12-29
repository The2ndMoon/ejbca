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
 
/*
 * RevokedInfoView.java
 *
 * Created on den 1 maj 2002, 07:55
 */
package org.ejbca.ui.web;

import java.io.Serializable;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Date;

import org.ejbca.core.ejb.ca.store.CertificateStatus;
import org.ejbca.core.model.SecConst;
import org.ejbca.core.model.ca.crl.RevokedCertInfo;

/** View of certificate revocation status
 *
 * @version $Id$
 */
public class RevokedInfoView implements Serializable {

	private static final long serialVersionUID = 1L;

	// Private fields.
    private CertificateStatus revokedcertinfo;
    private BigInteger certserno;

    /**
     * Creates a new instance of RevokedInfoView
     *
     * @param revokedcertinfo DOCUMENT ME!
     */
    public RevokedInfoView(CertificateStatus revokedcertinfo, BigInteger certSerno) {
        this.revokedcertinfo = revokedcertinfo;
        this.certserno = certSerno;
    }

    // Public methods.
    public String getCertificateSerialNumberAsString() {
        return this.certserno.toString(16);
    }

    /**
     * DOCUMENT ME!
     *
     * @return DOCUMENT ME!
     */
    public BigInteger getCertificateSerialNumber() {
        return this.certserno;
    }

    /**
     * DOCUMENT ME!
     *
     * @return DOCUMENT ME!
     */
    public Date getRevocationDate() {
        return this.revokedcertinfo.revocationDate;
    }

    /**
     * DOCUMENT ME!
     *
     * @return DOCUMENT ME!
     */
    public String[] getRevocationReasons() {
        String[] dummy = { "" };
        ArrayList<String> reasons = new ArrayList<String>();
        int reason = this.revokedcertinfo.revocationReason;

        if ((reason >= 0) && (reason < SecConst.HIGN_REASON_BOUNDRARY)) {
            // Add this reason.
            reasons.add(SecConst.reasontexts[reason]);
        }
        return (String[]) reasons.toArray(dummy);
    }

    public boolean isRevoked(){
    	return this.revokedcertinfo.revocationReason != RevokedCertInfo.NOT_REVOKED;
    }
}
