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

package org.ejbca.core.ejb.ca.auth;

import java.util.Date;

import javax.ejb.EJB;
import javax.ejb.EJBException;
import javax.ejb.FinderException;
import javax.ejb.ObjectNotFoundException;
import javax.ejb.Stateless;
import javax.ejb.TransactionAttribute;
import javax.ejb.TransactionAttributeType;
import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;

import org.apache.log4j.Logger;
import org.cesecore.core.ejb.log.LogSessionLocal;
import org.ejbca.core.ejb.JndiHelper;
import org.ejbca.core.ejb.ra.UserAdminSessionLocal;
import org.ejbca.core.ejb.ra.UserData;
import org.ejbca.core.model.InternalResources;
import org.ejbca.core.model.approval.ApprovalException;
import org.ejbca.core.model.approval.WaitingForApprovalException;
import org.ejbca.core.model.authorization.AuthorizationDeniedException;
import org.ejbca.core.model.ca.AuthLoginException;
import org.ejbca.core.model.ca.AuthStatusException;
import org.ejbca.core.model.log.Admin;
import org.ejbca.core.model.log.LogConstants;
import org.ejbca.core.model.ra.UserDataConstants;
import org.ejbca.core.model.ra.UserDataVO;

/**
 * Authenticates users towards a user database.
 *
 * @version $Id$
 *
 */
@Stateless(mappedName = JndiHelper.APP_JNDI_PREFIX + "AuthenticationSessionRemote")
@TransactionAttribute(TransactionAttributeType.REQUIRED)
public class AuthenticationSessionBean implements AuthenticationSessionLocal, AuthenticationSessionRemote {

    private static final Logger log = Logger.getLogger(AuthenticationSessionBean.class);
    
    @PersistenceContext(unitName="ejbca")
    private EntityManager entityManager;

    @EJB
    private UserAdminSessionLocal userAdminSession;
    @EJB
    private LogSessionLocal logSession;
    
    /** Internal localization of logs and errors */
    private static final InternalResources intres = InternalResources.getInstance();
    
    /**
     * Authenticates a user to the user database and returns the user DN.
     *
     * @param username unique username within the instance
     * @param password password for the user
     *
     * @return UserDataVO, never returns null
     *
     * @throws ObjectNotFoundException if the user does not exist.
     * @throws AuthStatusException If the users status is incorrect.
     * @throws AuthLoginException If the password is incorrect.
     */
    public UserDataVO authenticateUser(Admin admin, String username, String password)
        throws ObjectNotFoundException, AuthStatusException, AuthLoginException {
    	if (log.isTraceEnabled()) {
            log.trace(">authenticateUser(" + username + ", hiddenpwd)");
    	}
        try {
            // Find the user with username username, or throw FinderException
            UserData data = UserData.findByUsername(entityManager, username);
            if (data == null) {
            	throw new ObjectNotFoundException("Could not find username " + username);
            }
            // Decrease the remaining login attempts. When zero, the status is set to STATUS_GENERATED
           	userAdminSession.decRemainingLoginAttempts(admin, data.getUsername());
			
           	int status = data.getStatus();
            if ( (status == UserDataConstants.STATUS_NEW) || (status == UserDataConstants.STATUS_FAILED) || (status == UserDataConstants.STATUS_INPROCESS) || (status == UserDataConstants.STATUS_KEYRECOVERY)) {
            	if (log.isDebugEnabled()) {
            		log.debug("Trying to authenticate user: username="+data.getUsername()+", dn="+data.getSubjectDN()+", email="+data.getSubjectEmail()+", status="+data.getStatus()+", type="+data.getType());
            	}
                
                UserDataVO ret = new UserDataVO(data.getUsername(), data.getSubjectDN(), data.getCaId(), data.getSubjectAltName(), data.getSubjectEmail(), 
                		data.getStatus(), data.getType(), data.getEndEntityProfileId(), data.getCertificateProfileId(),
                		new Date(data.getTimeCreated()), new Date(data.getTimeModified()), data.getTokenType(), data.getHardTokenIssuerId(), data.getExtendedInformation());  
                ret.setPassword(data.getClearPassword());   
                ret.setCardNumber(data.getCardNumber());
                
                if (data.comparePassword(password) == false)
                {
                	String msg = intres.getLocalizedMessage("authentication.invalidpwd", username);            	
                	logSession.log(admin, data.getCaId(), LogConstants.MODULE_CA, new java.util.Date(),username, null, LogConstants.EVENT_ERROR_USERAUTHENTICATION,msg);
                	throw new AuthLoginException(msg);
                }
                
                // Resets the remaining login attempts as this was a successful login
                userAdminSession.resetRemainingLoginAttempts(admin, data.getUsername());
            	
                String msg = intres.getLocalizedMessage("authentication.authok", username);            	
                logSession.log(admin, data.getCaId(), LogConstants.MODULE_CA, new java.util.Date(),username, null, LogConstants.EVENT_INFO_USERAUTHENTICATION,msg);
            	if (log.isTraceEnabled()) {
                    log.trace("<authenticateUser("+username+", hiddenpwd)");
            	}
                return ret;
            }
        	String msg = intres.getLocalizedMessage("authentication.wrongstatus", UserDataConstants.getStatusText(status), Integer.valueOf(status), username);            	
        	logSession.log(admin, data.getCaId(), LogConstants.MODULE_CA, new java.util.Date(),username, null, LogConstants.EVENT_INFO_USERAUTHENTICATION,msg);
            throw new AuthStatusException(msg);
        } catch (ObjectNotFoundException oe) {
        	String msg = intres.getLocalizedMessage("authentication.usernotfound", username);            	
        	logSession.log(admin, admin.getCaId(), LogConstants.MODULE_CA, new java.util.Date(),username, null, LogConstants.EVENT_INFO_USERAUTHENTICATION,msg);
            throw oe;
        } catch (AuthStatusException se) {
            throw se;
        } catch (AuthLoginException le) {
            throw le;
        } catch (Exception e) {
        	String msg = intres.getLocalizedMessage("error.unknown");            	
            log.error(msg, e);
            throw new EJBException(e);
        }
    }

	/**
	 * Set the status of a user to finished, called when a user has been successfully processed. If
	 * possible sets users status to UserData.STATUS_GENERATED, which means that the user cannot
	 * be authenticated anymore. NOTE: May not have any effect of user database is remote.
	 * User data may contain a counter with nr of requests before used should be set to generated. In this case
	 * this counter will be decreased, and if it reaches 0 status will be generated. 
	 *
	 * @param data
	 * @throws ObjectNotFoundException if the user does not exist.
	 */
	public void finishUser(UserDataVO data) throws ObjectNotFoundException {
		if (log.isTraceEnabled()) {
			log.trace(">finishUser(" + data.getUsername() + ", hiddenpwd)");
		}
		// This admin can be the public web user, which may not be allowed to change status,
		// this is a bit ugly, but what can a man do...
		Admin statusadmin = new Admin(Admin.TYPE_INTERNALUSER);
		try {
			
			// See if we are allowed for make more requests than this one. If not user status changed by decRequestCounter
			int counter = userAdminSession.decRequestCounter(statusadmin, data.getUsername());
			if (counter <= 0) {
				String msg = intres.getLocalizedMessage("authentication.statuschanged", data.getUsername());
				logSession.log(statusadmin, data.getCAId(), LogConstants.MODULE_CA, new java.util.Date(), data.getUsername(), null, LogConstants.EVENT_INFO_CHANGEDENDENTITY,msg);
			} 
			if (log.isTraceEnabled()) {
				log.trace("<finishUser("+data.getUsername()+", hiddenpwd)");
			}
		} catch (FinderException e) {
			String msg = intres.getLocalizedMessage("authentication.usernotfound", data.getUsername());
			logSession.log(statusadmin, statusadmin.getCaId(), LogConstants.MODULE_CA, new java.util.Date(), data.getUsername(), null, LogConstants.EVENT_ERROR_USERAUTHENTICATION,msg);
			throw new ObjectNotFoundException(e.getMessage());
		} catch (AuthorizationDeniedException e) {
			// Should never happen
			log.error("AuthorizationDeniedException: ", e);
			throw new EJBException(e);
		} catch (ApprovalException e) {
			// Should never happen
		    log.error("ApprovalException: ", e);
			throw new EJBException(e);
		} catch (WaitingForApprovalException e) {
			// Should never happen
		    log.error("ApprovalException: ", e);
			throw new EJBException(e);
		}
	}
}
