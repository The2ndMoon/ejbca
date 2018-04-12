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
import java.security.cert.CertificateException;
import java.util.Date;

import javax.persistence.EntityManager;
import javax.persistence.PostLoad;
import javax.persistence.PrePersist;
import javax.persistence.PreUpdate;
import javax.persistence.Transient;

import org.apache.log4j.Logger;
import org.cesecore.certificates.endentity.EndEntityConstants;
import org.cesecore.dbprotection.ProtectedData;
import org.cesecore.util.Base64;
import org.cesecore.util.CertTools;

/**
 * Used as base class for NoConflictCertificateData and CertificateData to group the common logic for those entites
 *
 * @version $Id: ProtectedCertificateData.java 28264 2018-04-09 15:56:54Z tarmo $
 */
public abstract class BaseCertificateData extends ProtectedData {
    
    public abstract String getBase64Cert();
    public abstract String getFingerprint();
    public abstract String getSubjectDN();
    public abstract String getIssuerDN();
    public abstract String getSerialNumber();
    public abstract String getUsername();
    public abstract String getSubjectAltName();
    public abstract Integer getCertificateProfileId();
    public abstract int getStatus();
    public abstract Long getNotBefore();
    public abstract long getExpireDate();
    public abstract int getRevocationReason();
    public abstract long getRevocationDate();
    public abstract String getSubjectKeyId();
    public abstract Long getUpdateTime();
    public abstract Integer getEndEntityProfileId();
    public abstract String getCaFingerprint();
    public abstract int getType();
    public abstract String getTag();
    public abstract int getRowVersion();
    
    public abstract void setStatus(int status);
    public abstract void setRevocationDate(long revocationDate);
    public abstract void setRevocationDate(Date revocationDate);
    public abstract void setRevocationReason(int revocationReason);
    public abstract void setUpdateTime(Long updateTime);
    
    private static final Logger log = Logger.getLogger(BaseCertificateData.class);
    

    protected abstract String getClassName();
    
    /**
     * Return the certificate. From this table if contained here. From {link Base64CertData} if contained there.
     * @param entityManager To be used if the cert is in the {@link Base64CertData} table.
     * @return The certificate
     */
    @Transient
    public String getBase64Cert(EntityManager entityManager) {
        if (getBase64Cert() != null && getBase64Cert().length() > 0) {
            return getBase64Cert(); // the cert was in this table.
        }
        // try the other table.
        final Base64CertData res = Base64CertData.findByFingerprint(entityManager, getFingerprint());
        if (res == null) {
            String message = new StringBuilder()
                    .append("No ")
                    .append(getClassName())
                    .append(" found with fingerprint ")
                    .append(getFingerprint())
                    .append(" for '")
                    .append(getSubjectDN())
                    .append("' issued by '") 
                    .append(getIssuerDN())
                    .append("'.")
                    .toString();
            log.info(message);
            return null;
        }
        // it was in the other table.
        return res.getBase64Cert();
    }
    
    /**
     * Returns the certificate as an object.
     *
     * @return The certificate or null if it doesn't exist or is blank/null in the database
     */
    @Transient
    public Certificate getCertificate(EntityManager entityManager) {
        try {
            String certEncoded = getBase64Cert(entityManager);
            if (certEncoded == null || certEncoded.isEmpty()) {
                if (log.isDebugEnabled()) {
                    log.debug(getClassName() + " data was null or empty. Fingerprint of certificate: " + getFingerprint());
                }
                return null;
            }
            return CertTools.getCertfromByteArray(Base64.decode(certEncoded.getBytes()), Certificate.class);
        } catch (CertificateException ce) {
            log.error("Can't decode certificate.", ce);
            return null;
        }
    }
    
    /**
     * Returns the certificate as an object.
     *
     * @return The certificate or null if it doesn't exist or is blank/null in the database
     */
    @Transient
    public Certificate getCertificate(final Base64CertData base64CertData) {
        try {
            String certEncoded = null;
            if (getBase64Cert() != null && getBase64Cert().length()>0 ) {
                certEncoded = getBase64Cert();
            } else if (base64CertData!=null) {
                certEncoded = base64CertData.getBase64Cert();
            }
            if (certEncoded==null || certEncoded.isEmpty()) {
                if (log.isDebugEnabled()) {
                    String message = getClassName() + " data was null or empty. Fingerprint of certificate: " + getFingerprint();
                    log.debug(message);
                }
                return null;
            }
            return CertTools.getCertfromByteArray(Base64.decode(certEncoded.getBytes()), Certificate.class);
        } catch (CertificateException ce) {
            log.error("Can't decode " + getClassName() + ".", ce);
            return null;
        }
    }
    
    /**
     * Serialnumber formated as BigInteger.toString(16).toUpperCase(), or just as it is in DB if not encodable to hex.
     *
     * @return serial number in hex format
     */
    @Transient
    public String getSerialNumberHex() throws NumberFormatException {
        try {
            return new BigInteger(getSerialNumber(), 10).toString(16).toUpperCase();
        } catch (NumberFormatException e) {
            return getSerialNumber();
        }
    }
    
    /** @return the end entity profile this certificate was issued under or 0 if the information is not available. */
    @Transient
    public int getEndEntityProfileIdOrZero() {
        return getEndEntityProfileId() == null ? EndEntityConstants.NO_END_ENTITY_PROFILE : getEndEntityProfileId();
    }
    
    /**
     * DN of subject in certificate
     *
     * @return subject dn. If it is null, return empty string
     */
    @Transient
    public String getSubjectDnNeverNull() {
        final String subjectDn = getSubjectDN();
        return subjectDn == null ? "" : subjectDn;
    }
    
    
    //
    // Start Database integrity protection methods
    //
    
    @Transient
    @Override
    protected int getProtectVersion() {
        return 3;
    }

    @PrePersist
    @PreUpdate
    @Override
    protected void protectData() {
        super.protectData();
    }

    @PostLoad
    @Override
    protected void verifyData() {
        super.verifyData();
    }

    @Override
    @Transient
    protected String getRowId() {
        return getFingerprint();
    }
    //
    // End Database integrity protection methods
    //
}