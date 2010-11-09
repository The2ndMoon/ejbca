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
package org.ejbca.core.model.util;

import org.cesecore.core.ejb.ca.crl.CrlCreateSession;
import org.cesecore.core.ejb.ca.crl.CrlCreateSessionRemote;
import org.cesecore.core.ejb.ca.crl.CrlSession;
import org.cesecore.core.ejb.ca.crl.CrlSessionRemote;
import org.cesecore.core.ejb.ca.store.CertificateProfileSession;
import org.cesecore.core.ejb.ca.store.CertificateProfileSessionRemote;
import org.cesecore.core.ejb.log.LogSession;
import org.cesecore.core.ejb.log.LogSessionRemote;
import org.cesecore.core.ejb.log.OldLogSession;
import org.cesecore.core.ejb.log.OldLogSessionRemote;
import org.cesecore.core.ejb.ra.raadmin.EndEntityProfileSession;
import org.cesecore.core.ejb.ra.raadmin.EndEntityProfileSessionRemote;
import org.ejbca.core.ejb.JndiHelper;
import org.ejbca.core.ejb.approval.ApprovalSession;
import org.ejbca.core.ejb.approval.ApprovalSessionRemote;
import org.ejbca.core.ejb.authorization.AuthorizationSession;
import org.ejbca.core.ejb.authorization.AuthorizationSessionRemote;
import org.ejbca.core.ejb.ca.auth.AuthenticationSession;
import org.ejbca.core.ejb.ca.auth.AuthenticationSessionRemote;
import org.ejbca.core.ejb.ca.caadmin.CAAdminSession;
import org.ejbca.core.ejb.ca.caadmin.CAAdminSessionRemote;
import org.ejbca.core.ejb.ca.caadmin.CaSession;
import org.ejbca.core.ejb.ca.caadmin.CaSessionRemote;
import org.ejbca.core.ejb.ca.publisher.PublisherQueueSession;
import org.ejbca.core.ejb.ca.publisher.PublisherQueueSessionRemote;
import org.ejbca.core.ejb.ca.publisher.PublisherSession;
import org.ejbca.core.ejb.ca.publisher.PublisherSessionRemote;
import org.ejbca.core.ejb.ca.sign.SignSession;
import org.ejbca.core.ejb.ca.sign.SignSessionRemote;
import org.ejbca.core.ejb.ca.store.CertificateStoreOnlyDataSession;
import org.ejbca.core.ejb.ca.store.CertificateStoreOnlyDataSessionRemote;
import org.ejbca.core.ejb.ca.store.CertificateStoreSession;
import org.ejbca.core.ejb.ca.store.CertificateStoreSessionRemote;
import org.ejbca.core.ejb.hardtoken.HardTokenBatchJobSession;
import org.ejbca.core.ejb.hardtoken.HardTokenBatchJobSessionRemote;
import org.ejbca.core.ejb.hardtoken.HardTokenSession;
import org.ejbca.core.ejb.hardtoken.HardTokenSessionRemote;
import org.ejbca.core.ejb.keyrecovery.KeyRecoverySession;
import org.ejbca.core.ejb.keyrecovery.KeyRecoverySessionRemote;
import org.ejbca.core.ejb.protect.TableProtectSession;
import org.ejbca.core.ejb.protect.TableProtectSessionRemote;
import org.ejbca.core.ejb.ra.UserAdminSession;
import org.ejbca.core.ejb.ra.UserAdminSessionRemote;
import org.ejbca.core.ejb.ra.raadmin.RaAdminSession;
import org.ejbca.core.ejb.ra.raadmin.RaAdminSessionRemote;
import org.ejbca.core.ejb.ra.userdatasource.UserDataSourceSession;
import org.ejbca.core.ejb.ra.userdatasource.UserDataSourceSessionRemote;
import org.ejbca.core.ejb.services.ServiceSession;
import org.ejbca.core.ejb.services.ServiceSessionRemote;

/**
 * Helper methods to get EJB session interfaces.
 * 
 * @version $Id$
 */
public class EjbLocalHelper {
	// TODO: Find out how to do appserver agnostic local EJB lookup in JEE5
	// For now we will return the remote stub instead, just to get things working..

    private CAAdminSession caAdminSession = null;
    private CaSession caSession;
    private CertificateProfileSession certificateProfileSession;
    private CrlCreateSession crlStoreSession;
    private EndEntityProfileSession endEntityProfileSession;
    
    private SignSession signsession = null;
    public SignSession getSignSession()  {
    	if(signsession == null){
    		signsession = JndiHelper.getRemoteSession(SignSessionRemote.class);
    	}
    	return signsession;
    }
    
    public CaSession getCaSession() {
        if(caSession == null) {
            caSession = JndiHelper.getRemoteSession(CaSessionRemote.class);
        }
        return caSession;
    }
    
    public CAAdminSession getCAAdminSession()  {
    	if(caAdminSession == null){	
    		caAdminSession = JndiHelper.getRemoteSession(CAAdminSessionRemote.class);
    	}
    	return caAdminSession;
    }

	private AuthenticationSession authsession = null;
    public AuthenticationSession getAuthenticationSession()  {
    	if(authsession == null){	
    		authsession = JndiHelper.getRemoteSession(AuthenticationSessionRemote.class);
    	}
    	return authsession;
    }

	private AuthorizationSession authorizationSession = null;
    public AuthorizationSession getAuthorizationSession()  {
    	if(authorizationSession == null){	
    		authorizationSession = JndiHelper.getRemoteSession(AuthorizationSessionRemote.class);
    	}
    	return authorizationSession;
    }
    
    public CrlCreateSession getCrlCreateSession() {
        if(crlStoreSession == null) {
            crlStoreSession = JndiHelper.getRemoteSession(CrlCreateSessionRemote.class);
        }
        return crlStoreSession;
    }

    private KeyRecoverySession keyrecoverysession = null;
    public KeyRecoverySession getKeyRecoverySession()  {
    	if(keyrecoverysession == null){	
    		keyrecoverysession = JndiHelper.getRemoteSession(KeyRecoverySessionRemote.class);
    	}
    	return keyrecoverysession;
    }
    
    public CertificateProfileSession getCertificateProfileSession() {
        if(certificateProfileSession == null) {
            certificateProfileSession = JndiHelper.getRemoteSession(CertificateProfileSessionRemote.class);
        }
        return certificateProfileSession;
    }

	private CertificateStoreSession certificatestoresession = null;
	public CertificateStoreSession getCertStoreSession()  {
		if(certificatestoresession == null){
			certificatestoresession = JndiHelper.getRemoteSession(CertificateStoreSessionRemote.class);
		}
		return certificatestoresession;
	}
	
	private CertificateStoreOnlyDataSession certificateStoreOnlyDataSession = null;
	public CertificateStoreOnlyDataSession getCertificateStoreOnlyDataSession()  {
		if(certificateStoreOnlyDataSession == null){
			certificateStoreOnlyDataSession = JndiHelper.getRemoteSession(CertificateStoreOnlyDataSessionRemote.class);
		}
		return certificateStoreOnlyDataSession;
	}
	
	public EndEntityProfileSession getEndEntityProfileSession() {
	    if(endEntityProfileSession == null) {
	        endEntityProfileSession = JndiHelper.getRemoteSession(EndEntityProfileSessionRemote.class);
	    }
	    return endEntityProfileSession;
	}
	
	private UserAdminSession usersession = null;
	public UserAdminSession getUserAdminSession()  {
		if(usersession == null){
			usersession = JndiHelper.getRemoteSession(UserAdminSessionRemote.class);
		}
		return usersession;
	}
	
	private RaAdminSession rasession = null;
	public RaAdminSession getRAAdminSession()  {
		if(rasession == null){
			rasession = JndiHelper.getRemoteSession(RaAdminSessionRemote.class);
		}
		return rasession;
	}

	private ApprovalSession approvalsession = null;	
	public ApprovalSession getApprovalSession()  {
		if(approvalsession == null){
			approvalsession = JndiHelper.getRemoteSession(ApprovalSessionRemote.class);
		}
		return approvalsession;
	}

	private HardTokenSession hardtokensession = null;
	public HardTokenSession getHardTokenSession()  {
		if(hardtokensession == null){
			hardtokensession = JndiHelper.getRemoteSession(HardTokenSessionRemote.class);
		}
		return hardtokensession;
	}

	private LogSession logSession = null;
	public LogSession getLogSession()  {
		if(logSession == null){
			logSession = JndiHelper.getRemoteSession(LogSessionRemote.class);
		}
		return logSession;
	}
	
	private OldLogSession oldLogSession = null;
	public OldLogSession getOldLogSession()  {
		if(oldLogSession == null){
			oldLogSession = JndiHelper.getRemoteSession(OldLogSessionRemote.class);
		}
		return oldLogSession;
	}
	
	private TableProtectSession tableProtectSession = null;
	public TableProtectSession getTableProtectSession()  {
		if(tableProtectSession == null){
			tableProtectSession = JndiHelper.getRemoteSession(TableProtectSessionRemote.class);
		}
		return tableProtectSession;
	}

	private PublisherQueueSession publisherQueueSession = null;
	public PublisherQueueSession getPublisherQueueSession()  {
		if(publisherQueueSession == null){
			publisherQueueSession = JndiHelper.getRemoteSession(PublisherQueueSessionRemote.class);
		}
		return publisherQueueSession;
	}

	private UserDataSourceSession userDataSourceSession = null;
	public UserDataSourceSession getUserDataSourceSession() {
		if (userDataSourceSession == null) {
			userDataSourceSession = JndiHelper.getRemoteSession(UserDataSourceSessionRemote.class);
		}
		return userDataSourceSession;
	}

	private CrlSession createCRLSession = null;
	public CrlSession getCreateCrlSession() {
		if (createCRLSession == null) {
			createCRLSession = JndiHelper.getRemoteSession(CrlSessionRemote.class);
		}
		return createCRLSession;
	}

	private PublisherSession publisherSession = null;
	public PublisherSession getPublisherSession() {
		if (publisherSession == null) {
			publisherSession = JndiHelper.getRemoteSession(PublisherSessionRemote.class);
		}
		return publisherSession;
	}
	
	private ServiceSession serviceSession = null;
	public ServiceSession getServiceSession() {
		if (serviceSession == null) {
			serviceSession = JndiHelper.getRemoteSession(ServiceSessionRemote.class);
		}
		return serviceSession;
	}

	private HardTokenBatchJobSession hardTokenBatchJobSession;
	public HardTokenBatchJobSession getHardTokenBatchSession() {
		if (hardTokenBatchJobSession == null) {
			hardTokenBatchJobSession = JndiHelper.getRemoteSession(HardTokenBatchJobSessionRemote.class);
		}
		return hardTokenBatchJobSession;
	}
}