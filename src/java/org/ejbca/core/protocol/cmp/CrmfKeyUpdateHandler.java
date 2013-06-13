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

package org.ejbca.core.protocol.cmp;

import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.PublicKey;
import java.security.cert.X509Certificate;
import java.util.List;

import javax.ejb.FinderException;

import org.apache.commons.lang.StringUtils;
import org.apache.log4j.Logger;
import org.bouncycastle.asn1.crmf.CertReqMessages;
import org.bouncycastle.asn1.crmf.CertReqMsg;
import org.bouncycastle.asn1.x500.X500Name;
import org.cesecore.CesecoreException;
import org.cesecore.authentication.tokens.AuthenticationToken;
import org.cesecore.authorization.AuthorizationDeniedException;
import org.cesecore.authorization.control.AccessControlSession;
import org.cesecore.certificates.ca.CADoesntExistsException;
import org.cesecore.certificates.ca.CaSessionLocal;
import org.cesecore.certificates.certificate.CertificateStoreSession;
import org.cesecore.certificates.certificate.request.FailInfo;
import org.cesecore.certificates.certificate.request.ResponseMessage;
import org.cesecore.certificates.certificate.request.ResponseStatus;
import org.cesecore.certificates.certificateprofile.CertificateProfileSession;
import org.cesecore.certificates.endentity.EndEntityConstants;
import org.cesecore.certificates.endentity.EndEntityInformation;
import org.cesecore.certificates.util.AlgorithmTools;
import org.ejbca.config.CmpConfiguration;
import org.ejbca.core.EjbcaException;
import org.ejbca.core.ejb.authentication.web.WebAuthenticationProviderSessionLocal;
import org.ejbca.core.ejb.ca.sign.SignSession;
import org.ejbca.core.ejb.ra.EndEntityAccessSession;
import org.ejbca.core.ejb.ra.EndEntityManagementSession;
import org.ejbca.core.ejb.ra.raadmin.EndEntityProfileSessionLocal;
import org.ejbca.core.model.InternalEjbcaResources;
import org.ejbca.core.model.approval.WaitingForApprovalException;
import org.ejbca.core.model.ra.raadmin.UserDoesntFullfillEndEntityProfile;
import org.ejbca.core.protocol.cmp.authentication.EndEntityCertificateAuthenticationModule;

/**
 * Message handler for update messages using the CRMF format for the request itself.
 * 
 * @version $Id$
 */
public class CrmfKeyUpdateHandler extends BaseCmpMessageHandler implements ICmpMessageHandler {
    
    private static final Logger LOG = Logger.getLogger(CrmfKeyUpdateHandler.class);
    /** Internal localization of logs and errors */
    private static final InternalEjbcaResources INTRES = InternalEjbcaResources.getInstance();

    /** strings for error messages defined in internal resources */
    private static final String CMP_ERRORGENERAL = "cmp.errorgeneral";

    private final AccessControlSession authorizationSession;
    private final CertificateStoreSession certStoreSession;
    private final EndEntityAccessSession endEntityAccessSession;
    private final EndEntityManagementSession endEntityManagementSession;
    private final SignSession signSession;
    private final WebAuthenticationProviderSessionLocal authenticationProviderSession;
   

    /**
     * Used only by unit test.
     */
    public CrmfKeyUpdateHandler() {
        super();
        this.signSession =null;
        this.endEntityAccessSession = null;
        this.certStoreSession = null;
        this.authorizationSession = null;
        this.authenticationProviderSession = null;
        this.endEntityManagementSession = null;
    }
    
    /**
     * Construct the message handler.
     * @param admin
     * @param caSession
     * @param certificateProfileSession
     * @param certificateRequestSession
     * @param endEntityProfileSession
     * @param signSession
     * @param endEntityManagementSession
     */
    public CrmfKeyUpdateHandler(final AuthenticationToken admin, String configAlias, CaSessionLocal caSession, CertificateProfileSession certificateProfileSession, 
            EndEntityAccessSession endEntityAccessSession, EndEntityProfileSessionLocal endEntityProfileSession, SignSession signSession, 
            CertificateStoreSession certStoreSession, AccessControlSession authSession, WebAuthenticationProviderSessionLocal authProviderSession, 
            EndEntityManagementSession endEntityManagementSession) {
        
        super(admin, configAlias, caSession, endEntityProfileSession, certificateProfileSession);
        this.signSession = signSession;
        this.endEntityAccessSession = endEntityAccessSession;
        this.certStoreSession = certStoreSession;
        this.authorizationSession = authSession;
        this.authenticationProviderSession = authProviderSession;
        this.endEntityManagementSession = endEntityManagementSession;

    }

    /**
     * Handles the CMP message
     * 
     * Expects the CMP message to be a CrmfRequestMessage. The message is authenticated using 
     * EndEntityCertificateAuthenticationModule in client mode. It used the attached certificate 
     * to find then End Entity which this certificate belongs to and requesting for a new certificate 
     * to be generated. 
     * 
     * If automatic update of the key (same as certificate renewal), the end entity's status is set to 
     * 'NEW' before processing the request. If using the same old keys in the new certificate is not allowed, 
     * a check is made to insure the the key specified in the request is not the same as the key of the attached 
     * certificate.
     * 
     * The KeyUpdateRequet is processed only in client mode.
     * 
     * @param msg
     * @throws AuthorizationDeniedException when the concerned end entity is not found
     * @throws CADoesntExistsException when updating the end entity fails
     * @throws UserDoesntFullfillEndEntityProfile when updating the end entity fails
     * @throws WaitingForApprovalException when updating the end entity fails
     * @throws EjbcaException when updating the end entity fails
     * @throws FinderException when end entity status fails to update
     * @throws CesecoreException
     * @throws InvalidKeyException when failing to read the key from the crmf request
     * @throws NoSuchAlgorithmException when failing to read the key from the crmf request
     * @throws NoSuchProviderException when failing to read the key from the crmf request
     */
    public ResponseMessage handleMessage(final BaseCmpMessage msg, boolean authenticated) {
        if (LOG.isTraceEnabled()) {
            LOG.trace(">handleMessage");
        }
        
        if(LOG.isDebugEnabled()) {
            LOG.debug("CMP running on RA mode: " + CmpConfiguration.getRAOperationMode(this.confAlias));
        }

        ResponseMessage resp = null;
        try {

            CrmfRequestMessage crmfreq = null;
            if (msg instanceof CrmfRequestMessage) {
                crmfreq = (CrmfRequestMessage) msg;
                crmfreq.getMessage();               
                
                // Authenticate the request
                EndEntityCertificateAuthenticationModule eecmodule = new EndEntityCertificateAuthenticationModule(getEECCA(), this.confAlias);
                eecmodule.setSession(this.admin, this.caSession, this.certStoreSession, this.authorizationSession, this.endEntityProfileSession, 
                        this.endEntityAccessSession, authenticationProviderSession, endEntityManagementSession);
                if(!eecmodule.verifyOrExtract(crmfreq.getPKIMessage(), null, authenticated)) {
                    String errMsg = eecmodule.getErrorMessage();
                    if( errMsg == null) {
                        errMsg = "Failed to verify the request";
                    }
                    LOG.info(errMsg);
                    return CmpMessageHelper.createUnprotectedErrorMessage(msg, ResponseStatus.FAILURE, FailInfo.BAD_MESSAGE_CHECK, errMsg);
                }
                
                if(LOG.isDebugEnabled()) {
                    LOG.debug("The CMP KeyUpdate request was verified successfully");
                }
                // Get the certificate attached to the request
                X509Certificate oldCert = (X509Certificate) eecmodule.getExtraCert();
            
                // Find the end entity that the certificate belongs to
                String subjectDN = null;
                String issuerDN = null;

                if(CmpConfiguration.getRAOperationMode(this.confAlias)) {
                    CertReqMessages kur = (CertReqMessages) crmfreq.getPKIMessage().getBody().getContent();
                    CertReqMsg certmsg;
                    try {
                        certmsg = kur.toCertReqMsgArray()[0];
                    } catch(Exception e) {
                        LOG.debug("Could not parse the revocation request. Trying to parse it as novosec generated message.");
                        certmsg = CmpMessageHelper.getNovosecCertReqMsg(kur);
                        LOG.debug("Succeeded in parsing the novosec generated request.");
                    }
                    X500Name dn = certmsg.getCertReq().getCertTemplate().getSubject();
                    if(dn != null) {
                        subjectDN = dn.toString();
                    }
                    dn = certmsg.getCertReq().getCertTemplate().getIssuer();
                    if(dn != null) {
                        issuerDN = dn.toString();
                    }
                } else {
                    subjectDN = oldCert.getSubjectDN().toString(); 
                    issuerDN = oldCert.getIssuerDN().toString();
                }

                if(subjectDN == null) {
                    final String errMsg = "Cannot find a SubjectDN in the request";
                    LOG.info(errMsg);
                    return CmpMessageHelper.createUnprotectedErrorMessage(msg, ResponseStatus.FAILURE, FailInfo.BAD_REQUEST, errMsg);
                }

                if(LOG.isDebugEnabled()) {
                    LOG.debug("Looking for an end entity with subjectDN: " + subjectDN);
                }


                EndEntityInformation userdata = null;
                if(issuerDN == null) {
                    if(LOG.isDebugEnabled()) {
                        LOG.debug("The CMP KeyUpdateRequest did not specify an issuer");
                    }
                    List<EndEntityInformation> userdataList = endEntityAccessSession.findUserBySubjectDN(admin, subjectDN);
                    if (userdataList.size() > 0) {
                        userdata = userdataList.get(0);
                    }
                    if (userdataList.size() > 1) {
                        LOG.warn("Multiple end entities with subject DN " + subjectDN + " were found. This may lead to unexpected behavior.");
                    }
                } else {
                    List<EndEntityInformation> userdataList = endEntityAccessSession.findUserBySubjectAndIssuerDN(admin, subjectDN, issuerDN);
                    if (userdataList.size() > 0) {
                        userdata = userdataList.get(0);
                    }
                    if (userdataList.size() > 1) {
                        LOG.warn("Multiple end entities with subject DN " + subjectDN + " and issuer DN" + issuerDN
                                + " were found. This may lead to unexpected behavior.");
                    }
                }

                if(userdata == null) {
                    final String errMsg = INTRES.getLocalizedMessage("cmp.infonouserfordn", subjectDN);
                    LOG.info(errMsg);
                    return CmpMessageHelper.createUnprotectedErrorMessage(msg, ResponseStatus.FAILURE, FailInfo.BAD_MESSAGE_CHECK, errMsg);
                }

                if(LOG.isDebugEnabled()) {
                    LOG.debug("Found user '" + userdata.getUsername() + "'");
                }
            
                // The password that should be used to obtain the new certificate
                String password = StringUtils.isNotEmpty(userdata.getPassword()) ? userdata.getPassword() : eecmodule.getAuthenticationString();
                
                // Set the appropriate parameters in the end entity
                userdata.setPassword(password);
                endEntityManagementSession.changeUser(admin, userdata, true);
                if(CmpConfiguration.getAllowAutomaticKeyUpdate(this.confAlias)) {
                    if(LOG.isDebugEnabled()) {
                        LOG.debug("Setting the end entity status to 'NEW'. Username: " + userdata.getUsername());
                    }

                    endEntityManagementSession.setUserStatus(admin, userdata.getUsername(), EndEntityConstants.STATUS_NEW);
                }
                
                // Set the appropriate parameters in the request
                crmfreq.setUsername(userdata.getUsername());
                crmfreq.setPassword(password);
                if(crmfreq.getHeader().getProtectionAlg() != null) {
                    crmfreq.setPreferredDigestAlg(AlgorithmTools.getDigestFromSigAlg(crmfreq.getHeader().getProtectionAlg().getAlgorithm().getId()));
                }

                // Check the public key, whether it is allowed to use the old keys or not.
                if(!CmpConfiguration.getAllowUpdateWithSameKey(this.confAlias)) {
                    PublicKey certPublicKey = oldCert.getPublicKey();
                    PublicKey requestPublicKey = crmfreq.getRequestPublicKey();
                    if(LOG.isDebugEnabled()) {
                        LOG.debug("Not allowing update with same key, comparing keys.");
                        if (LOG.isTraceEnabled()) {
                            LOG.trace("OldKey: "+certPublicKey.toString());
                            LOG.trace("NewKey: "+requestPublicKey.toString());
                        }
                    }
                    if(certPublicKey.equals(requestPublicKey)) {
                        final String errMsg = "Invalid key. The public key in the KeyUpdateRequest is the same as the public key in the existing end entity certificate";
                        LOG.info(errMsg);
                        return CmpMessageHelper.createUnprotectedErrorMessage(msg, ResponseStatus.FAILURE, FailInfo.BAD_MESSAGE_CHECK, errMsg);
                    }
                }

                // Process the request
                resp = signSession.createCertificate(admin, crmfreq, org.ejbca.core.protocol.cmp.CmpResponseMessage.class, userdata);               

                if (resp == null) {
                    final String errMsg = INTRES.getLocalizedMessage("cmp.errornullresp");
                    LOG.info(errMsg);
                    resp = CmpMessageHelper.createUnprotectedErrorMessage(msg, ResponseStatus.FAILURE, FailInfo.BAD_MESSAGE_CHECK, errMsg);
                }
            } else {
                final String errMsg = INTRES.getLocalizedMessage("cmp.errornocmrfreq");
                LOG.info(errMsg);
                resp = CmpMessageHelper.createUnprotectedErrorMessage(msg, ResponseStatus.FAILURE, FailInfo.BAD_MESSAGE_CHECK, errMsg);
            }
        
        } catch (AuthorizationDeniedException e) {
            final String errMsg = INTRES.getLocalizedMessage(CMP_ERRORGENERAL, e.getMessage());
            LOG.info(errMsg, e);           
            resp = CmpMessageHelper.createUnprotectedErrorMessage(msg, ResponseStatus.FAILURE, FailInfo.BAD_REQUEST, e.getMessage());
        } catch (CADoesntExistsException e) {
            final String errMsg = INTRES.getLocalizedMessage(CMP_ERRORGENERAL, e.getMessage());
            LOG.info(errMsg, e);           
            resp = CmpMessageHelper.createUnprotectedErrorMessage(msg, ResponseStatus.FAILURE, FailInfo.BAD_REQUEST, e.getMessage());
        } catch (UserDoesntFullfillEndEntityProfile e) {
            final String errMsg = INTRES.getLocalizedMessage(CMP_ERRORGENERAL, e.getMessage());
            LOG.info(errMsg, e);           
            resp = CmpMessageHelper.createUnprotectedErrorMessage(msg, ResponseStatus.FAILURE, FailInfo.BAD_REQUEST, e.getMessage());
        } catch (WaitingForApprovalException e) {
            final String errMsg = INTRES.getLocalizedMessage(CMP_ERRORGENERAL, e.getMessage());
            LOG.info(errMsg, e);           
            resp = CmpMessageHelper.createUnprotectedErrorMessage(msg, ResponseStatus.FAILURE, FailInfo.BAD_REQUEST, e.getMessage());
        } catch (EjbcaException e) {
            final String errMsg = INTRES.getLocalizedMessage(CMP_ERRORGENERAL, e.getMessage());
            LOG.info(errMsg, e);           
            resp = CmpMessageHelper.createUnprotectedErrorMessage(msg, ResponseStatus.FAILURE, FailInfo.BAD_REQUEST, e.getMessage());
        } catch (FinderException e) {
            final String errMsg = INTRES.getLocalizedMessage(CMP_ERRORGENERAL, e.getMessage());
            LOG.info(errMsg, e);           
            resp = CmpMessageHelper.createUnprotectedErrorMessage(msg, ResponseStatus.FAILURE, FailInfo.BAD_REQUEST, e.getMessage());
        } catch (CesecoreException e) {
            final String errMsg = INTRES.getLocalizedMessage(CMP_ERRORGENERAL, e.getMessage());
            LOG.info(errMsg, e);           
            resp = CmpMessageHelper.createUnprotectedErrorMessage(msg, ResponseStatus.FAILURE, FailInfo.BAD_REQUEST, e.getMessage());
        } catch (InvalidKeyException e) {
            final String errMsg = INTRES.getLocalizedMessage(CMP_ERRORGENERAL, e.getMessage());
            LOG.info("Error while reading the public key of the extraCert attached to the CMP request");
            LOG.info(errMsg, e);           
            resp = CmpMessageHelper.createUnprotectedErrorMessage(msg, ResponseStatus.FAILURE, FailInfo.BAD_REQUEST, e.getMessage());
        } catch (NoSuchAlgorithmException e) {
            final String errMsg = INTRES.getLocalizedMessage(CMP_ERRORGENERAL, e.getMessage());
            LOG.info("Error while reading the public key of the extraCert attached to the CMP request");
            LOG.info(errMsg, e);           
            resp = CmpMessageHelper.createUnprotectedErrorMessage(msg, ResponseStatus.FAILURE, FailInfo.BAD_REQUEST, e.getMessage());
        } catch (NoSuchProviderException e) {
            final String errMsg = INTRES.getLocalizedMessage(CMP_ERRORGENERAL, e.getMessage());
            LOG.info("Error while reading the public key of the extraCert attached to the CMP request");
            LOG.info(errMsg, e);           
            resp = CmpMessageHelper.createUnprotectedErrorMessage(msg, ResponseStatus.FAILURE, FailInfo.BAD_REQUEST, e.getMessage());
        }

        if (LOG.isTraceEnabled()) {
            LOG.trace("<handleMessage");
        }
        return resp;
    }

    private String getEECCA() {

        // Read the CA from the properties file first
        String parameter = CmpConfiguration.getAuthenticationParameter(CmpConfiguration.AUTHMODULE_ENDENTITY_CERTIFICATE, this.confAlias);
        if(!StringUtils.equals("-", parameter) && (parameter != null) && StringUtils.isNotEmpty(parameter)) {
            return parameter;
        }
    	
    	// The CA is not set in the cmp.authenticationparameters. Set the CA depending on the operation mode
    	if(CmpConfiguration.getRAOperationMode(this.confAlias)) {
    	    String defaultCA = CmpConfiguration.getDefaultCA(this.confAlias);
    	    if( StringUtils.isNotEmpty(defaultCA) ) {
    	        return defaultCA;
    	    } else {
    	        return "-";
    	    }
    	} else {
    	    return "-";
    	}
    }


}
