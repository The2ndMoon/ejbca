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

package org.ejbca.core.ejb.ca.caadmin;

import java.io.Serializable;
import java.util.HashMap;
import java.util.List;

import javax.persistence.Entity;
import javax.persistence.EntityManager;
import javax.persistence.NoResultException;
import javax.persistence.Query;
import javax.persistence.Table;
import javax.persistence.Transient;

import org.apache.log4j.Logger;
import org.ejbca.core.ejb.JBossUnmarshaller;
import org.ejbca.core.model.UpgradeableDataHashMap;
import org.ejbca.core.model.ca.certificateprofiles.CACertificateProfile;
import org.ejbca.core.model.ca.certificateprofiles.CertificateProfile;
import org.ejbca.core.model.ca.certificateprofiles.EndUserCertificateProfile;
import org.ejbca.core.model.ca.certificateprofiles.RootCACertificateProfile;

/**
 * Representation of a certificate profile (template).
 * 
 * @version $Id$
 */
@Entity
@Table(name="CertificateProfileData")
public class CertificateProfileData implements Serializable {

	private static final long serialVersionUID = 1L;
	private static final Logger log = Logger.getLogger(CertificateProfileData .class);

	private Integer id;
	private String certificateProfileName;
	private Serializable data;
	private int rowVersion = 0;
	private String rowProtection;

	/**
	 * Entity holding data of a certificate profile.
	 */
	public CertificateProfileData(final Integer id, final String certificateprofilename, final CertificateProfile certificateProfile) {
		setId(id);
		setCertificateProfileName(certificateprofilename);
		setCertificateProfile(certificateProfile);
		log.debug("Created certificateprofile " + certificateprofilename);
	}
	
	public CertificateProfileData() { }

	//@Id @Column
	public Integer getId() { return id; }
	public final void setId(final Integer id) { this.id = id; }

	//@Column
	public String getCertificateProfileName() { return certificateProfileName; }
	public final void setCertificateProfileName(final String certificateProfileName) { this.certificateProfileName = certificateProfileName; }

	//@Column @Lob
	public Serializable getDataUnsafe() {
		final HashMap h = JBossUnmarshaller.extractObject(HashMap.class, data);	// This is a workaround for JBoss J2EE CMP Serialization
		if (h != null) {
			setDataUnsafe(h);
		}
		return data;
	}
	/** DO NOT USE! Stick with setData(HashMap data) instead. */
	public final void setDataUnsafe(final Serializable data) { this.data = data; }

	//@Version @Column
	public int getRowVersion() { return rowVersion; }
	public void setRowVersion(final int rowVersion) { this.rowVersion = rowVersion; }

	//@Column @Lob
	public String getRowProtection() { return rowProtection; }
	public void setRowProtection(final String rowProtection) { this.rowProtection = rowProtection; }

	@Transient
	private HashMap getData() { return (HashMap) getDataUnsafe(); }
	private final void setData(final HashMap data) { setDataUnsafe(data); }
	
	/**
	 * Method that returns the certificate profiles and updates it if necessary.
	 */
	@Transient
	public CertificateProfile getCertificateProfile() {
    	return readAndUpgradeProfileInternal();
	}

	/**
	 * Method that saves the certificate profile to database.
	 */
	public final void setCertificateProfile(final CertificateProfile profile) {
		setData((HashMap) profile.saveData());
	}

    /** 
     * Method that upgrades a Certificate Profile, if needed.
     */
    public void upgradeProfile() {
    	readAndUpgradeProfileInternal();
    }
    
    /**
     * We have an internal method for this read operation with a side-effect. 
     * This is because getCertificateProfile() is a read-only method, so the possible side-effect of upgrade will not happen,
     * and therefore this internal method can be called from another non-read-only method, upgradeProfile().
     * @return CertificateProfile
     * 
     * TODO: Verify read-only? apply read-only?
     */
    private CertificateProfile readAndUpgradeProfileInternal() {
        CertificateProfile returnval = null;
        switch (((Integer) (getData().get(CertificateProfile.TYPE))).intValue()) {
            case CertificateProfile.TYPE_ROOTCA:
                returnval = new RootCACertificateProfile();
                break;
            case CertificateProfile.TYPE_SUBCA:
                returnval = new CACertificateProfile();
                break;
            case CertificateProfile.TYPE_ENDENTITY:
            default :
                returnval = new EndUserCertificateProfile();
        }
        final HashMap data = getData();
        // If CertificateProfile-data is upgraded we want to save the new data, so we must get the old version before loading the data 
        // and perhaps upgrading
        final float oldversion = ((Float) data.get(UpgradeableDataHashMap.VERSION)).floatValue();
        // Load the profile data, this will potentially upgrade the CertificateProfile
        returnval.loadData(data);
        if (Float.compare(oldversion, returnval.getVersion()) != 0) {
        	// Save new data versions differ
        	setCertificateProfile(returnval);
        }
        return returnval;
    }
    
	//
	// Search functions. 
	//

	/** @return the found entity instance or null if the entity does not exist */
	public static CertificateProfileData findById(final EntityManager entityManager, final Integer id) {
		return entityManager.find(CertificateProfileData.class, id);
	}
	
	/**
	 * @throws javax.persistence.NonUniqueResultException if more than one entity with the name exists
	 * @return the found entity instance or null if the entity does not exist
	 */
	public static CertificateProfileData findByProfileName(final EntityManager entityManager, final String certificateProfileName) {
		CertificateProfileData ret = null;
		try {
			final Query query = entityManager.createQuery("SELECT a FROM CertificateProfileData a WHERE a.certificateProfileName=:certificateProfileName");
			query.setParameter("certificateProfileName", certificateProfileName);
			ret = (CertificateProfileData) query.getSingleResult();
		} catch (NoResultException e) {
		}
		return ret;
	}

	/** @return return the query results as a List. */
	public static List<CertificateProfileData> findAll(final EntityManager entityManager) {
		final Query query = entityManager.createQuery("SELECT a FROM CertificateProfileData a");
		return query.getResultList();
	}
}
