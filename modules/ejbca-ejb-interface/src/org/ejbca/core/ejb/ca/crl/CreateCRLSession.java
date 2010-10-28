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
package org.ejbca.core.ejb.ca.crl;

import java.util.Collection;

import org.ejbca.core.model.ca.catoken.CATokenOfflineException;

/** Session bean generating CRLs
 * 
 * @version $Id$
 */
public interface CreateCRLSession {

    /**
     * Method that checks if the CRL is needed to be updated for the CA and
     * creates the CRL, if neccessary. A CRL is created: 1. if the current CRL
     * expires within the crloverlaptime (milliseconds) 2. if a CRL issue
     * interval is defined (>0) a CRL is issued when this interval has passed,
     * even if the current CRL is still valid
     * 
     * @param admin
     *            administrator performing the task
     * @param ca
     *            the CA this operation regards
     * @param addtocrloverlaptime
     *            given in milliseconds and added to the CRL overlap time, if
     *            set to how often this method is run (poll time), it can be
     *            used to issue a new CRL if the current one expires within the
     *            CRL overlap time (configured in CA) and the poll time. The
     *            used CRL overlap time will be (crloverlaptime +
     *            addtocrloverlaptime)
     * @return true if a CRL was created
     * @throws javax.ejb.EJBException
     *             if communication or system error occurrs
     */
    public boolean runNewTransactionConditioned(org.ejbca.core.model.log.Admin admin, org.ejbca.core.model.ca.caadmin.CA ca, long addtocrloverlaptime)
            throws CATokenOfflineException;

    /**
     * Method that checks if the delta CRL needs to be updated and then creates
     * it.
     * 
     * @param admin
     *            administrator performing the task
     * @param ca
     *            the CA this operation regards
     * @param crloverlaptime
     *            A new delta CRL is created if the current one expires within
     *            the crloverlaptime given in milliseconds
     * @return true if a Delta CRL was created
     * @throws javax.ejb.EJBException
     *             if communication or system error occurrs
     */
    public boolean runDeltaCRLnewTransactionConditioned(org.ejbca.core.model.log.Admin admin, org.ejbca.core.model.ca.caadmin.CA ca, long crloverlaptime);

    /**
     * Generates a new CRL by looking in the database for revoked certificates
     * and generating a CRL. This method also "archives" certificates when after
     * they are no longer needed in the CRL.
     * 
     * @param admin
     *            administrator performing the task
     * @param ca
     *            the CA this operation regards
     * @return fingerprint (primarey key) of the generated CRL or null if
     *         generation failed
     * @throws javax.ejb.EJBException
     *             if a communications- or system error occurs
     */
    public java.lang.String run(org.ejbca.core.model.log.Admin admin, org.ejbca.core.model.ca.caadmin.CA ca) throws CATokenOfflineException;

    /**
     * Generates a new Delta CRL by looking in the database for revoked
     * certificates since the last complete CRL issued and generating a CRL with
     * the difference. If either of baseCrlNumber or baseCrlCreateTime is -1
     * this method will try to query the database for the last complete CRL.
     * 
     * @param admin
     *            administrator performing the task
     * @param ca
     *            the CA this operation regards
     * @param baseCrlNumber
     *            base crl number to be put in the delta CRL, this is the CRL
     *            number of the previous complete CRL. If value is -1 the value
     *            is fetched by querying the database looking for the last
     *            complete CRL.
     * @param baseCrlCreateTime
     *            the time the base CRL was issued. If value is -1 the value is
     *            fetched by querying the database looking for the last complete
     *            CRL.
     * @return the bytes of the Delta CRL generated or null of no delta CRL was
     *         generated.
     * @throws javax.ejb.EJBException
     *             if a communications- or system error occurs
     */
    public byte[] runDeltaCRL(org.ejbca.core.model.log.Admin admin, org.ejbca.core.model.ca.caadmin.CA ca, int baseCrlNumber, long baseCrlCreateTime);

    /**
     * Retrieves the latest CRL issued by this CA.
     * 
     * @param admin
     *            Administrator performing the operation
     * @param issuerdn
     *            the CRL issuers DN (CAs subject DN)
     * @param deltaCRL
     *            true to get the latest deltaCRL, false to get the
     *            latestcomplete CRL
     * @return byte[] with DER encoded X509CRL or null of no CRLs have been
     *         issued.
     */
    public byte[] getLastCRL(org.ejbca.core.model.log.Admin admin, java.lang.String issuerdn, boolean deltaCRL);

    /**
     * Retrieves the information about the lastest CRL issued by this CA.
     * Retreives less information than getLastCRL, i.e. not the actual CRL data.
     * 
     * @param admin
     *            Administrator performing the operation
     * @param issuerdn
     *            the CRL issuers DN (CAs subject DN)
     * @param deltaCRL
     *            true to get the latest deltaCRL, false to get the
     *            latestcomplete CRL
     * @return CRLInfo of last CRL by CA or null if no CRL exists.
     */
    public org.ejbca.core.model.ca.store.CRLInfo getLastCRLInfo(org.ejbca.core.model.log.Admin admin, java.lang.String issuerdn, boolean deltaCRL);

    /**
     * Retrieves the information about the specified CRL. Retreives less
     * information than getLastCRL, i.e. not the actual CRL data.
     * 
     * @param admin
     *            Administrator performing the operation
     * @param fingerprint
     *            fingerprint of the CRL
     * @return CRLInfo of CRL or null if no CRL exists.
     */
    public org.ejbca.core.model.ca.store.CRLInfo getCRLInfo(org.ejbca.core.model.log.Admin admin, java.lang.String fingerprint);

    /**
     * Retrieves the highest CRLNumber issued by the CA.
     * 
     * @param admin
     *            Administrator performing the operation
     * @param issuerdn
     *            the subjectDN of a CA certificate
     * @param deltaCRL
     *            true to get the latest deltaCRL, false to get the latest
     *            complete CRL
     */
    public int getLastCRLNumber(org.ejbca.core.model.log.Admin admin, java.lang.String issuerdn, boolean deltaCRL);

    /**
     * (Re-)Publish the last CRLs for a CA.
     * 
     * @param admin
     *            Information about the administrator or admin preforming the
     *            event.
     * @param caCert
     *            The certificate for the CA to publish CRLs for
     * @param usedpublishers
     *            a collection if publisher id's (Integer) indicating which
     *            publisher that should be used.
     * @param caDataDN
     *            DN from CA data. If a the CA certificate does not have a DN
     *            object to be used by the publisher this DN could be searched
     *            for the object.
     * @param doPublishDeltaCRL
     *            should delta CRLs be published?
     */
    public void publishCRL(org.ejbca.core.model.log.Admin admin, java.security.cert.Certificate caCert, Collection<Integer> usedpublishers,
            java.lang.String caDataDN, boolean doPublishDeltaCRL);
}
