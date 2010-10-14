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

import java.security.cert.Certificate;
import java.security.cert.X509CRL;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.Iterator;

import javax.ejb.EJB;
import javax.ejb.EJBException;
import javax.ejb.FinderException;
import javax.ejb.Stateless;
import javax.ejb.TransactionAttribute;
import javax.ejb.TransactionAttributeType;
import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;

import org.apache.log4j.Logger;
import org.cesecore.core.ejb.log.LogSessionLocal;
import org.ejbca.core.ejb.JndiHelper;
import org.ejbca.core.ejb.ca.publisher.PublisherSessionLocal;
import org.ejbca.core.ejb.ca.store.CRLData;
import org.ejbca.core.ejb.ca.store.CertificateData;
import org.ejbca.core.ejb.ca.store.CertificateStoreSessionLocal;
import org.ejbca.core.model.InternalResources;
import org.ejbca.core.model.SecConst;
import org.ejbca.core.model.ca.caadmin.CA;
import org.ejbca.core.model.ca.caadmin.CAInfo;
import org.ejbca.core.model.ca.caadmin.X509CAInfo;
import org.ejbca.core.model.ca.catoken.CATokenOfflineException;
import org.ejbca.core.model.ca.crl.RevokedCertInfo;
import org.ejbca.core.model.ca.store.CRLInfo;
import org.ejbca.core.model.log.Admin;
import org.ejbca.core.model.log.LogConstants;
import org.ejbca.util.CertTools;

/**
 * The name is kept for historic reasons. This Session Bean is used for creating and retrieving CRLs and information about CRLs.
 * CRLs are signed using RSASignSessionBean.
 * 
 * @version $Id$
 *
 */
@Stateless(mappedName = JndiHelper.APP_JNDI_PREFIX + "CreateCRLSessionRemote")
@TransactionAttribute(TransactionAttributeType.REQUIRED)
public class CreateCRLSessionBean implements CreateCRLSessionLocal, CreateCRLSessionRemote{

    private static final long serialVersionUID = 1L;

    private static final Logger log = Logger.getLogger(CreateCRLSessionBean.class);
    /** Internal localization of logs and errors */
    private static final InternalResources intres = InternalResources.getInstance();
    
    @PersistenceContext(unitName="ejbca")
    private EntityManager entityManager;

    @EJB
    private CertificateStoreSessionLocal certificateStoreSession;
    @EJB
    private PublisherSessionLocal publisherSession;
    @EJB
    private LogSessionLocal logSession;

	/** Same as generating a new CRL but this is in a new separate transaction.
	 */
    @TransactionAttribute(TransactionAttributeType.REQUIRES_NEW)
    public void runNewTransaction(Admin admin, CA ca) throws CATokenOfflineException {
    	run(admin, ca);
    }

    /**
     * Method that checks if the CRL is needed to be updated for the CA and creates the CRL, if necessary. A CRL is created:
     * 1. if the current CRL expires within the crloverlaptime (milliseconds)
     * 2. if a CRL issue interval is defined (>0) a CRL is issued when this interval has passed, even if the current CRL is still valid
     *  
     * @param admin administrator performing the task
     * @param ca the CA this operation regards
     * @param addtocrloverlaptime given in milliseconds and added to the CRL overlap time, if set to how often this method is run (poll time), it can be used to issue a new CRL if the current one expires within
     * the CRL overlap time (configured in CA) and the poll time. The used CRL overlap time will be (crloverlaptime + addtocrloverlaptime) 
     *
     * @return true if a CRL was created
     * @throws EJBException if communication or system error occurs
     */
    @TransactionAttribute(TransactionAttributeType.REQUIRES_NEW)
    public boolean runNewTransactionConditioned(Admin admin, CA ca, long addtocrloverlaptime) throws CATokenOfflineException {
    	boolean ret = false;
    	Date currenttime = new Date();
    	CAInfo cainfo = ca.getCAInfo();
    	try {
    		if (cainfo.getStatus() == SecConst.CA_EXTERNAL) {
    			log.debug("Not trying to generate CRL for external CA "+cainfo.getName());
    		} else if (cainfo.getStatus() == SecConst.CA_WAITING_CERTIFICATE_RESPONSE) {
    			log.debug("Not trying to generate CRL for CA "+cainfo.getName() +" awaiting certificate response.");
    		} else {
    			if (cainfo instanceof X509CAInfo) {
    				Collection<Certificate> certs = cainfo.getCertificateChain();
    				final Certificate cacert;
    				if (!certs.isEmpty()) {
    					cacert = certs.iterator().next();   
    				} else {
    					cacert = null;
    				}
    				// Don't create CRLs if the CA has expired
    				if ( (cacert != null) && (CertTools.getNotAfter(cacert).after(new Date())) ) {
    					if (cainfo.getStatus() == SecConst.CA_OFFLINE )  {
    						String msg = intres.getLocalizedMessage("createcrl.caoffline", cainfo.getName(), new Integer(cainfo.getCAId()));            	    			    	   
    						log.info(msg);
    						logSession.log(admin, cainfo.getCAId(), LogConstants.MODULE_CA, new java.util.Date(),null, null, LogConstants.EVENT_INFO_CREATECRL, msg);
    					} else {
    						try {
    							if (log.isDebugEnabled()) {
    								log.debug("Checking to see if CA '"+cainfo.getName()+"' ("+cainfo.getCAId()+") needs CRL generation.");
    							}
    							final String certSubjectDN = CertTools.getSubjectDN(cacert);
    							CRLInfo crlinfo = getLastCRLInfo(admin,certSubjectDN,false);
    							if (log.isDebugEnabled()) {
    								if (crlinfo == null) {
    									log.debug("Crlinfo was null");
    								} else {
    									log.debug("Read crlinfo for CA: "+cainfo.getName()+", lastNumber="+crlinfo.getLastCRLNumber()+", expireDate="+crlinfo.getExpireDate());
    								}    			            	   
    							}
    							long crlissueinterval = cainfo.getCRLIssueInterval();
    							if (log.isDebugEnabled()) {
    								log.debug("crlissueinterval="+crlissueinterval);
    								log.debug("crloverlaptime="+cainfo.getCRLOverlapTime());                            	   
    							}
    							long overlap = cainfo.getCRLOverlapTime() + addtocrloverlaptime; // Overlaptime is in minutes, default if crlissueinterval == 0
    							long nextUpdate = 0; // if crlinfo == 0, we will issue a crl now
    							if (crlinfo != null) {
    								// CRL issueinterval in hours. If this is 0, we should only issue a CRL when
    								// the old one is about to expire, i.e. when currenttime + overlaptime > expiredate
    								// if isseuinterval is > 0 we will issue a new CRL when currenttime > createtime + issueinterval
    								nextUpdate = crlinfo.getExpireDate().getTime(); // Default if crlissueinterval == 0
    								if (crlissueinterval > 0) {
    									long u = crlinfo.getCreateDate().getTime() + crlissueinterval;
    									// If this period for some reason (we missed to issue some?) is larger than when the CRL expires,
    									// we need to issue one when the CRL expires
    									if ((u + overlap) < nextUpdate) {
    										nextUpdate = u;
    										// When we issue CRLs before the real expiration date we don't use overlap
    										overlap = 0;
    									}
    								}                                   
    								log.debug("Calculated nextUpdate to "+nextUpdate);
    							} else {
    								String msg = intres.getLocalizedMessage("createcrl.crlinfonull", cainfo.getName());            	    			    	   
    								log.info(msg);
    							}
    							if ((currenttime.getTime() + overlap) >= nextUpdate) {
    								if (log.isDebugEnabled()) {
    									log.debug("Creating CRL for CA, because:"+currenttime.getTime()+overlap+" >= "+nextUpdate);    			            		   
    								}
    								run(admin, ca);
    								//this.runNewTransaction(admin, cainfo.getSubjectDN());
    								ret = true;
    								//createdcrls++;
    							}

    						} catch (CATokenOfflineException e) {
    							String msg = intres.getLocalizedMessage("createcrl.caoffline", cainfo.getName(), new Integer(cainfo.getCAId()));            	    			    	   
    							log.error(msg);
    							logSession.log(admin, cainfo.getCAId(), LogConstants.MODULE_CA, new java.util.Date(),null, null, LogConstants.EVENT_ERROR_CREATECRL, msg);
    						}
    					}
    				} else if (cacert != null) {
    					log.debug("Not creating CRL for expired CA "+cainfo.getName()+". CA subjectDN='"+CertTools.getSubjectDN(cacert)+"', expired: "+CertTools.getNotAfter(cacert));    			    	   
    				} else {
    					log.debug("Not creating CRL for CA without CA certificate: "+cainfo.getName());    			    	           			    	   
    				}
    			}                           				   
    		}
    	} catch(Exception e) {
    		String msg = intres.getLocalizedMessage("createcrl.generalerror", new Integer(cainfo.getCAId()));            	    			    	   
    		log.error(msg, e);
    		logSession.log(admin, cainfo.getCAId(), LogConstants.MODULE_CA, new java.util.Date(),null, null, LogConstants.EVENT_ERROR_CREATECRL,msg,e);
    		if (e instanceof EJBException) {
    			throw (EJBException)e;
    		}
    		throw new EJBException(e);
    	}
    	return ret;
    }

    /** Same as generating a new delta CRL but this is in a new separate transaction.
     * @param admin administrator performing the task
     * @param ca the CA this operation regards
     */
    @TransactionAttribute(TransactionAttributeType.REQUIRES_NEW)
    public byte[] runDeltaCRLnewTransaction(Admin admin, CA ca)  {
    	return runDeltaCRL(admin, ca, -1, -1);
    }

    /**
     * Method that checks if the delta CRL needs to be updated and then creates it.
     *
     * @param admin administrator performing the task
     * @param ca the CA this operation regards
     * @param crloverlaptime A new delta CRL is created if the current one expires within the crloverlaptime given in milliseconds
	 * 
     * @return true if a Delta CRL was created
     * @throws EJBException if communication or system error occurrs
     */
    @TransactionAttribute(TransactionAttributeType.REQUIRES_NEW)
    public boolean runDeltaCRLnewTransactionConditioned(Admin admin, CA ca, long crloverlaptime) {
    	boolean ret = false;
		Date currenttime = new Date();
		CAInfo cainfo = ca.getCAInfo();
		try{
			if (cainfo.getStatus() == SecConst.CA_EXTERNAL) {
				log.debug("Not trying to generate delta CRL for external CA "+cainfo.getName());
			} else if (cainfo.getStatus() == SecConst.CA_WAITING_CERTIFICATE_RESPONSE) {
				log.debug("Not trying to generate delta CRL for CA "+cainfo.getName() +" awaiting certificate response.");
			} else {
				if (cainfo instanceof X509CAInfo) {
					Collection<Certificate> certs = cainfo.getCertificateChain();
					final Certificate cacert;
					if (!certs.isEmpty()) {
						cacert = certs.iterator().next();   
					} else {
					    cacert = null;
					}
					// Don't create CRLs if the CA has expired
					if ( (cacert != null) && (CertTools.getNotAfter(cacert).after(new Date())) ) {
    					if(cainfo.getDeltaCRLPeriod() > 0) {
    						if (cainfo.getStatus() == SecConst.CA_OFFLINE) {
    							String msg = intres.getLocalizedMessage("createcrl.caoffline", cainfo.getName(), new Integer(cainfo.getCAId()));            	    			    	   
    							log.error(msg);
    							logSession.log(admin, cainfo.getCAId(), LogConstants.MODULE_CA, new java.util.Date(),null, null, LogConstants.EVENT_ERROR_CREATECRL, msg);
    						} else {
    							if (log.isDebugEnabled()) {
    								log.debug("Checking to see if CA '"+cainfo.getName()+"' needs Delta CRL generation.");
    							}
    							final String certSubjectDN = CertTools.getSubjectDN(cacert);
    							CRLInfo deltacrlinfo = getLastCRLInfo(admin, certSubjectDN, true);
    							if (log.isDebugEnabled()) {
    								if (deltacrlinfo == null) {
    									log.debug("DeltaCrlinfo was null");
    								} else {
    									log.debug("Read deltacrlinfo for CA: "+cainfo.getName()+", lastNumber="+deltacrlinfo.getLastCRLNumber()+", expireDate="+deltacrlinfo.getExpireDate());
    								}    			            	   
    							}
    							if((deltacrlinfo == null) || ((currenttime.getTime() + crloverlaptime) >= deltacrlinfo.getExpireDate().getTime())){
    								runDeltaCRL(admin, ca, -1, -1);
    								ret = true;
    							}
    						}
    					}
					} else if (cacert != null) {
						log.debug("Not creating delta CRL for expired CA "+cainfo.getName()+". CA subjectDN='"+CertTools.getSubjectDN(cacert)+"', expired: "+CertTools.getNotAfter(cacert));    			    	   
					} else {
						log.debug("Not creating delta CRL for CA without CA certificate: "+cainfo.getName());    			    	           			    	   
					}
				}    					
		   }
		}catch(Exception e) {
        	String msg = intres.getLocalizedMessage("createcrl.generalerror", new Integer(cainfo.getCAId()));            	    			    	   
        	log.error(msg, e);
        	logSession.log(admin, cainfo.getCAId(), LogConstants.MODULE_CA, new java.util.Date(),null, null, LogConstants.EVENT_ERROR_CREATECRL,msg,e);
        	if (e instanceof EJBException) {
        		throw (EJBException)e;
        	}
        	throw new EJBException(e);
		}
		return ret;
    }

	/**
	 * Generates a new CRL by looking in the database for revoked certificates and generating a
	 * CRL. This method also "archives" certificates when after they are no longer needed in the CRL. 
	 *
	 * @param admin administrator performing the task
     * @param ca the CA this operation regards
	 * @return fingerprint (primarey key) of the generated CRL or null if generation failed
	 * 
	 * @throws EJBException if a communications- or system error occurs
	 */
    public String run(Admin admin, CA ca) throws CATokenOfflineException {
    	log.trace(">run()");
        if (ca == null) {
            throw new EJBException("No CA specified.");
        }
        CAInfo cainfo = ca.getCAInfo();
        int caid = cainfo.getCAId();
        String ret = null;
        try {
            final String caCertSubjectDN; // DN from the CA issuing the CRL to be used when searching for the CRL in the database.
            {
            	final Collection<Certificate> certs = cainfo.getCertificateChain();
            	final Certificate cacert = !certs.isEmpty() ? certs.iterator().next(): null;
            	caCertSubjectDN = cacert!=null ? CertTools.getSubjectDN(cacert) : null;
            }
            // We can not create a CRL for a CA that is waiting for certificate response
            if ( caCertSubjectDN!=null && cainfo.getStatus()==SecConst.CA_ACTIVE )  {
            	long crlperiod = cainfo.getCRLPeriod();
            	// Find all revoked certificates for a complete CRL
            	Collection<RevokedCertInfo> revcerts = certificateStoreSession.listRevokedCertInfo(admin, caCertSubjectDN, -1);
            	log.debug("Found "+revcerts.size()+" revoked certificates.");

            	// Go through them and create a CRL, at the same time archive expired certificates
            	Date now = new Date();
            	Date check = new Date(now.getTime() - crlperiod);
            	Iterator<RevokedCertInfo> iter = revcerts.iterator();
            	while (iter.hasNext()) {
            		RevokedCertInfo data = iter.next();
            		// We want to include certificates that was revoked after the last CRL was issued, but before this one
            		// so the revoked certs are included in ONE CRL at least. See RFC5280 section 3.3.
            		if ( data.getExpireDate().before(check) ) {
            			// Certificate has expired, set status to archived in the database 
            			setArchivedStatus(data.getCertificateFingerprint());
            		} else {
                		Date revDate = data.getRevocationDate();
            			if (revDate == null) {
            				data.setRevocationDate(now);
            				CertificateData certdata = CertificateData.findByFingerprint(entityManager, data.getCertificateFingerprint());
            				if (certdata == null) {
            					throw new FinderException("No certificate with fingerprint " + data.getCertificateFingerprint());
            				}
            				// Set revocation date in the database
            				certdata.setRevocationDate(now);
            			}
            		}
            	}
            	// a full CRL
            	byte[] crlBytes = createCRL(admin, ca, revcerts, -1);
            	if (crlBytes != null) {
                	ret = CertTools.getFingerprintAsString(crlBytes);            		
            	}
            	// This is logged in the database by SignSession 
            	String msg = intres.getLocalizedMessage("createcrl.createdcrl", cainfo.getName(), cainfo.getSubjectDN());            	
            	log.info(msg);
            	// This debug logging is very very heavy if you have large CRLs. Please don't use it :-)
//          	if (log.isDebugEnabled()) {
//          	X509CRL crl = CertTools.getCRLfromByteArray(crlBytes);
//          	debug("Created CRL with expire date: "+crl.getNextUpdate());
//          	FileOutputStream fos = new FileOutputStream("c:\\java\\srvtestcrl.der");
//          	fos.write(crl.getEncoded());
//          	fos.close();
//          	}
            } else {
            	String msg = intres.getLocalizedMessage("createcrl.errornotactive", cainfo.getName(), new Integer(caid), cainfo.getStatus());            	    			    	   
            	log.info(msg);            	
            }
        } catch (CATokenOfflineException e) {
            throw e;            
        } catch (Exception e) {
        	String msg = intres.getLocalizedMessage("createcrl.errorcreate", new Integer(caid));            	
            log.error(msg, e);
            logSession.log(admin, caid, LogConstants.MODULE_CA, new java.util.Date(),null, null, LogConstants.EVENT_ERROR_CREATECRL, msg, e);
            throw new EJBException(e);
        }
        log.trace("<run()");
        return ret;
    }

	/**
	 * This method sets the "archived" certificates status. Normally this is done by the CRL-creation process.
	 * This is also used from the createLotsOfCertsPerUser test.
	 *
	 * @param certificateFingerprint is the fingerprint of the certifiate
	 * @throws FinderException is thrown when no such certificate exists
	 */
    public void setArchivedStatus(String certificateFingerprint) throws FinderException {
    	CertificateData certdata = CertificateData.findByFingerprint(entityManager, certificateFingerprint);
    	if (certdata == null) {
    		throw new FinderException("No certificate w fingerprint " + certificateFingerprint);
    	}
		certdata.setStatus(SecConst.CERT_ARCHIVED);
    }
    
    /**
     * Generates a new Delta CRL by looking in the database for revoked certificates since the last complete CRL issued and generating a
     * CRL with the difference. If either of baseCrlNumber or baseCrlCreateTime is -1 this method will try to query the database for the last complete CRL.
     *
     * @param admin administrator performing the task
     * @param ca the CA this operation regards
     * @param baseCrlNumber base crl number to be put in the delta CRL, this is the CRL number of the previous complete CRL. If value is -1 the value is fetched by querying the database looking for the last complete CRL.
     * @param baseCrlCreateTime the time the base CRL was issued. If value is -1 the value is fetched by querying the database looking for the last complete CRL. 
     * @return the bytes of the Delta CRL generated or null of no delta CRL was generated.
     * 
     * @throws EJBException if a communications- or system error occurs
     */
    public byte[] runDeltaCRL(Admin admin, CA ca, int baseCrlNumber, long baseCrlCreateTime)  {
		if (ca == null) {
			throw new EJBException("No CA specified.");
		}
		CAInfo cainfo = ca.getCAInfo();
    	if (log.isTraceEnabled()) {
        	log.trace(">runDeltaCRL: "+cainfo.getSubjectDN());
    	}
    	byte[] crlBytes = null;
    	final int caid = cainfo.getCAId();
    	try {
    		final String caCertSubjectDN; {
    		    final Collection<Certificate> certs = cainfo.getCertificateChain();
    		    final Certificate cacert = !certs.isEmpty() ? certs.iterator().next(): null;
                caCertSubjectDN = cacert!=null ? CertTools.getSubjectDN(cacert) : null;
            }
    		if (caCertSubjectDN!=null && cainfo instanceof X509CAInfo) { // Only create CRLs for X509 CAs
    			if ( (baseCrlNumber == -1) && (baseCrlCreateTime == -1) ) {
        			CRLInfo basecrlinfo = getLastCRLInfo(admin, caCertSubjectDN, false);
        			baseCrlCreateTime = basecrlinfo.getCreateDate().getTime();
        			baseCrlNumber = basecrlinfo.getLastCRLNumber();    				
    			}
    			// Find all revoked certificates
    			Collection<RevokedCertInfo> revcertinfos = certificateStoreSession.listRevokedCertInfo(admin, caCertSubjectDN, baseCrlCreateTime);
    			log.debug("Found "+revcertinfos.size()+" revoked certificates.");
    			// Go through them and create a CRL, at the same time archive expired certificates
    			ArrayList<RevokedCertInfo> certs = new ArrayList<RevokedCertInfo>();
    			Iterator<RevokedCertInfo> iter = revcertinfos.iterator();
    			while (iter.hasNext()) {
    				RevokedCertInfo ci = iter.next();
    				if (ci.getRevocationDate() == null) {
    					ci.setRevocationDate(new Date());
    				}
    				certs.add(ci);
    			}
    			// create a delta CRL
    			crlBytes = createCRL(admin, ca, certs, baseCrlNumber);
    			X509CRL crl = CertTools.getCRLfromByteArray(crlBytes);
    			log.debug("Created delta CRL with expire date: "+crl.getNextUpdate());
    		}
    	} catch (Exception e) {
    		logSession.log(admin, caid, LogConstants.MODULE_CA, new java.util.Date(),null, null, LogConstants.EVENT_ERROR_CREATECRL,e.getMessage());
    		throw new EJBException(e);
    	}
    	if (log.isTraceEnabled()) {
        	log.trace("<runDeltaCRL: "+cainfo.getSubjectDN());
    	}
		return crlBytes;
    }
        
    /**
     * Requests for a CRL to be created with the passed (revoked) certificates.
     *
     * @param admin administrator performing the task
     * @param ca the CA this operation regards
     * @param certs collection of RevokedCertInfo object.
     * @param basecrlnumber the CRL number of the Base CRL to generate a deltaCRL, -1 to generate a full CRL
     * @return The newly created CRL in DER encoded byte form or null, use CertTools.getCRLfromByteArray to convert to X509CRL.
     * @throws CATokenOfflineException 
     */
    public byte[] createCRL(Admin admin, CA ca, Collection<RevokedCertInfo> certs, int basecrlnumber) throws CATokenOfflineException {
        log.trace(">createCRL()");
        byte[] crlBytes = null; // return value
        try {
            if ( (ca.getStatus() != SecConst.CA_ACTIVE) && (ca.getStatus() != SecConst.CA_WAITING_CERTIFICATE_RESPONSE) ) {
                String msg = intres.getLocalizedMessage("signsession.canotactive", ca.getSubjectDN());
                logSession.log(admin, ca.getCAId(), LogConstants.MODULE_CA, new java.util.Date(), null, null, LogConstants.EVENT_ERROR_CREATECERTIFICATE, msg);
                throw new CATokenOfflineException(msg);
            }
            final X509CRL crl;
            final String certSubjectDN = CertTools.getSubjectDN(ca.getCACertificate());
            int fullnumber = getLastCRLNumber(admin, certSubjectDN, false);
            int deltanumber = getLastCRLNumber(admin, certSubjectDN, true);
            // nextCrlNumber: The highest number of last CRL (full or delta) and increased by 1 (both full CRLs and deltaCRLs share the same series of CRL Number)
            int nextCrlNumber = ( (fullnumber > deltanumber) ? fullnumber : deltanumber ) +1; 
            boolean deltaCRL = (basecrlnumber > -1);
            if (deltaCRL) {
            	// Workaround if transaction handling fails so that crlNumber for deltaCRL would happen to be the same
            	if (nextCrlNumber == basecrlnumber) {
            		nextCrlNumber++;
            	}
            	crl = (X509CRL) ca.generateDeltaCRL(certs, nextCrlNumber, basecrlnumber);	
            } else {
            	crl = (X509CRL) ca.generateCRL(certs, nextCrlNumber);
            }
            if (crl != null) {
                String msg = intres.getLocalizedMessage("signsession.createdcrl", new Integer(nextCrlNumber), ca.getName(), ca.getSubjectDN());
                logSession.log(admin, ca.getCAId(), LogConstants.MODULE_CA, new java.util.Date(), null, null, LogConstants.EVENT_INFO_CREATECRL, msg);

                // Store CRL in the database
                String fingerprint = CertTools.getFingerprintAsString(ca.getCACertificate());
                crlBytes = crl.getEncoded();            	
                log.debug("Storing CRL in certificate store.");
                storeCRL(admin, crlBytes, fingerprint, nextCrlNumber, crl.getIssuerDN().getName(), crl.getThisUpdate(), crl.getNextUpdate(), (deltaCRL ? 1 : -1));
                // Store crl in ca CRL publishers.
                log.debug("Storing CRL in publishers");
                publisherSession.storeCRL(admin, ca.getCRLPublishers(), crlBytes, fingerprint, ca.getSubjectDN());
            }
        } catch (CATokenOfflineException ctoe) {
            String msg = intres.getLocalizedMessage("error.catokenoffline", ca.getSubjectDN());
            log.error(msg, ctoe);
            logSession.log(admin, ca.getCAId(), LogConstants.MODULE_CA, new java.util.Date(), null, null, LogConstants.EVENT_ERROR_CREATECRL, msg, ctoe);
            throw ctoe;
        } catch (Exception e) {
        	logSession.log(admin, ca.getCAId(), LogConstants.MODULE_CA, new java.util.Date(), null, null, LogConstants.EVENT_ERROR_CREATECRL, intres.getLocalizedMessage("signsession.errorcreatecrl"), e);
            throw new EJBException(intres.getLocalizedMessage("signsession.errorcreatecrl"), e);
        }
        log.trace("<createCRL()");
        return crlBytes;
    }

    /**
     * Stores a CRL
     *
     * @param incrl  The DER coded CRL to be stored.
     * @param cafp   Fingerprint (hex) of the CAs certificate.
     * @param number CRL number.
     * @param issuerDN the issuer of the CRL
     * @param thisUpdate when this CRL was created
     * @param nextUpdate when this CRL expires
     * @param deltaCRLIndicator -1 for a normal CRL and 1 for a deltaCRL
     * @return true if storage was successful.
     */
    public boolean storeCRL(Admin admin, byte[] incrl, String cafp, int number, String issuerDN, Date thisUpdate, Date nextUpdate, int deltaCRLIndicator) {
    	if (log.isTraceEnabled()) {
        	log.trace(">storeCRL(" + cafp + ", " + number + ")");
    	}
        try {
        	boolean deltaCRL = deltaCRLIndicator > 0;
        	int lastNo = getLastCRLNumber(admin, issuerDN, deltaCRL);
        	if (number <= lastNo) {
        		// There is already a CRL with this number, or a later one stored. Don't create duplicates
            	String msg = intres.getLocalizedMessage("store.storecrlwrongnumber", number, lastNo);            	
            	logSession.log(admin, LogConstants.INTERNALCAID, LogConstants.MODULE_CA, new java.util.Date(), null, null, LogConstants.EVENT_ERROR_STORECRL, msg);        		
        	}
        	entityManager.persist(new CRLData(incrl, number, issuerDN, thisUpdate, nextUpdate, cafp, deltaCRLIndicator));
        	String msg = intres.getLocalizedMessage("store.storecrl", new Integer(number), null);            	
        	logSession.log(admin, issuerDN.toString().hashCode(), LogConstants.MODULE_CA, new java.util.Date(), null, null, LogConstants.EVENT_INFO_STORECRL, msg);
        } catch (Exception e) {
        	String msg = intres.getLocalizedMessage("store.storecrl");            	
            logSession.log(admin, LogConstants.INTERNALCAID, LogConstants.MODULE_CA, new java.util.Date(), null, null, LogConstants.EVENT_ERROR_STORECRL, msg);
            throw new EJBException(e);
        }
        log.trace("<storeCRL()");
        return true;
    }

    /**
     * Retrieves the latest CRL issued by this CA.
     *
     * @param admin Administrator performing the operation
     * @param issuerdn the CRL issuers DN (CAs subject DN)
     * @param deltaCRL true to get the latest deltaCRL, false to get the latestcomplete CRL
     * @return byte[] with DER encoded X509CRL or null of no CRLs have been issued.
     */
    @TransactionAttribute(TransactionAttributeType.SUPPORTS)
    public byte[] getLastCRL(Admin admin, String issuerdn, boolean deltaCRL) {
    	if (log.isTraceEnabled()) {
        	log.trace(">getLastCRL(" + issuerdn + ", "+deltaCRL+")");
    	}
        try {
            int maxnumber = getLastCRLNumber(admin, issuerdn, deltaCRL);
            X509CRL crl = null;
        	CRLData data = CRLData.findByIssuerDNAndCRLNumber(entityManager, issuerdn, maxnumber);
        	if (data != null) {
                crl = data.getCRL();
            }
            log.trace("<getLastCRL()");
            if (crl == null) {
            	String msg = intres.getLocalizedMessage("store.errorgetcrl", issuerdn, maxnumber);            	
                logSession.log(admin, admin.getCaId(), LogConstants.MODULE_CA, new java.util.Date(), null, null, LogConstants.EVENT_ERROR_GETLASTCRL, msg);
                return null;
            }
        	String msg = intres.getLocalizedMessage("store.getcrl", issuerdn, new Integer(maxnumber));            	
            logSession.log(admin, crl.getIssuerDN().toString().hashCode(), LogConstants.MODULE_CA, new java.util.Date(), null, null, LogConstants.EVENT_INFO_GETLASTCRL, msg);
            return crl.getEncoded();
        } catch (Exception e) {
        	String msg = intres.getLocalizedMessage("store.errorgetcrl", issuerdn);            	
            logSession.log(admin, admin.getCaId(), LogConstants.MODULE_CA, new java.util.Date(), null, null, LogConstants.EVENT_ERROR_GETLASTCRL, msg);
            throw new EJBException(e);
        }
    }

    /**
     * Retrieves the information about the lastest CRL issued by this CA. Retreives less information than getLastCRL, i.e. not the actual CRL data.
     *
     * @param admin Administrator performing the operation
     * @param issuerdn the CRL issuers DN (CAs subject DN)
     * @param deltaCRL true to get the latest deltaCRL, false to get the latestcomplete CRL
     * @return CRLInfo of last CRL by CA or null if no CRL exists.
     */
    @TransactionAttribute(TransactionAttributeType.SUPPORTS)
    public CRLInfo getLastCRLInfo(Admin admin, String issuerdn, boolean deltaCRL) {
    	if (log.isTraceEnabled()) {
        	log.trace(">getLastCRLInfo(" + issuerdn + ", "+deltaCRL+")");
    	}
        int crlnumber = 0;
        try {
            crlnumber = getLastCRLNumber(admin, issuerdn, deltaCRL);
            CRLInfo crlinfo = null;
            CRLData data = CRLData.findByIssuerDNAndCRLNumber(entityManager, issuerdn, crlnumber);
            if (data != null) {
                crlinfo = new CRLInfo(data.getIssuerDN(), crlnumber, data.getThisUpdate(), data.getNextUpdate());
            } else {
            	if (deltaCRL && (crlnumber == 0)) {
            		log.debug("No delta CRL exists for CA with dn '"+issuerdn+"'");
            	} else if (crlnumber == 0) {
            		log.debug("No CRL exists for CA with dn '"+issuerdn+"'");
            	} else {
                	String msg = intres.getLocalizedMessage("store.errorgetcrl", issuerdn, new Integer(crlnumber));            	
                    log.error(msg);            		
            	}
                crlinfo = null;
            }
            log.trace("<getLastCRLInfo()");
            return crlinfo;
        } catch (Exception e) {
        	String msg = intres.getLocalizedMessage("store.errorgetcrlinfo", issuerdn);            	
            logSession.log(admin, issuerdn.hashCode(), LogConstants.MODULE_CA, new java.util.Date(), null, null, LogConstants.EVENT_ERROR_GETLASTCRL, msg);
            throw new EJBException(e);
        }
    }

    /**
     * Retrieves the information about the specified CRL. Retreives less information than getLastCRL, i.e. not the actual CRL data.
     *
     * @param admin Administrator performing the operation
     * @param fingerprint fingerprint of the CRL
     * @return CRLInfo of CRL or null if no CRL exists.
     */
    @TransactionAttribute(TransactionAttributeType.SUPPORTS)
    public CRLInfo getCRLInfo(Admin admin, String fingerprint) {
    	if (log.isTraceEnabled()) {
        	log.trace(">getCRLInfo(" + fingerprint+")");
    	}
        try {
            CRLInfo crlinfo = null;
            CRLData data = CRLData.findByFingerprint(entityManager, fingerprint);
            if (data != null) {
                crlinfo = new CRLInfo(data.getIssuerDN(), data.getCrlNumber(), data.getThisUpdate(), data.getNextUpdate());
            } else {
            	log.debug("No CRL exists with fingerprint '"+fingerprint+"'");
            	String msg = intres.getLocalizedMessage("store.errorgetcrl", fingerprint, 0);            	
            	log.error(msg);            		
            }
            log.trace("<getCRLInfo()");
            return crlinfo;
        } catch (Exception e) {
        	String msg = intres.getLocalizedMessage("store.errorgetcrlinfo", fingerprint);            	
            logSession.log(admin, fingerprint.hashCode(), LogConstants.MODULE_CA, new java.util.Date(), null, null, LogConstants.EVENT_ERROR_GETLASTCRL, msg);
            throw new EJBException(e);
        }
    }

    /**
     * Retrieves the highest CRLNumber issued by the CA.
     *
     * @param admin    Administrator performing the operation
     * @param issuerdn the subjectDN of a CA certificate
     * @param deltaCRL true to get the latest deltaCRL, false to get the latest complete CRL
     */
    @TransactionAttribute(TransactionAttributeType.SUPPORTS)
    public int getLastCRLNumber(Admin admin, String issuerdn, boolean deltaCRL) {
    	if (log.isTraceEnabled()) {
        	log.trace(">getLastCRLNumber(" + issuerdn + ", "+deltaCRL+")");
    	}
    	int maxnumber = 0;
    	Integer result = CRLData.findHighestCRLNumber(entityManager, issuerdn, deltaCRL);
    	if (result != null) {
    		maxnumber = result.intValue();
    	}
    	if (log.isTraceEnabled()) {
            log.trace("<getLastCRLNumber(" + maxnumber + ")");
    	}
    	return maxnumber;
    }

    /**
     * (Re-)Publish the last CRLs for a CA.
     *
     * @param admin            Information about the administrator or admin preforming the event.
     * @param caCert           The certificate for the CA to publish CRLs for
     * @param usedpublishers   a collection if publisher id's (Integer) indicating which publisher that should be used.
     * @param caDataDN         DN from CA data. If a the CA certificate does not have a DN object to be used by the publisher this DN could be searched for the object.
     * @param doPublishDeltaCRL should delta CRLs be published?
     */
    public void publishCRL(Admin admin, Certificate caCert, Collection<Integer> usedpublishers, String caDataDN, boolean doPublishDeltaCRL) {
    	if ( usedpublishers==null ) {
    		return;
    	}
    	final String issuerDN = CertTools.getSubjectDN(caCert);
    	final String caCertFingerprint = CertTools.getFingerprintAsString(caCert);
    	final byte crl[] = getLastCRL(admin, issuerDN, false);
    	if ( crl!=null ) {
    		publisherSession.storeCRL(admin, usedpublishers, crl, caCertFingerprint, caDataDN);
    	}
    	if ( !doPublishDeltaCRL ) {
    		return;
    	}
    	final byte deltaCrl[] = getLastCRL(admin, issuerDN, true);
    	if ( deltaCrl!=null ) {
    		publisherSession.storeCRL(admin, usedpublishers, deltaCrl, caCertFingerprint, caDataDN);
    	}
    }
}
