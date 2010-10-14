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

package org.ejbca.core.ejb.hardtoken;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.List;

import javax.ejb.EJB;
import javax.ejb.EJBException;
import javax.ejb.Stateless;
import javax.ejb.TransactionAttribute;
import javax.ejb.TransactionAttributeType;
import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;

import org.apache.log4j.Logger;
import org.cesecore.core.ejb.log.LogSessionLocal;
import org.ejbca.core.ejb.JndiHelper;
import org.ejbca.core.ejb.ra.UserData;
import org.ejbca.core.model.InternalResources;
import org.ejbca.core.model.hardtoken.UnavailableTokenException;
import org.ejbca.core.model.log.Admin;
import org.ejbca.core.model.log.LogConstants;
import org.ejbca.core.model.ra.UserDataVO;

/**
 * Remote interface for bean used by hardtoken batchprograms to retrieve users to generate from EJBCA RA.
 *
 * @version $Id$
 */
@Stateless(mappedName = JndiHelper.APP_JNDI_PREFIX + "HardTokenBatchJobSessionRemote")
@TransactionAttribute(TransactionAttributeType.REQUIRED)
public class EjbcaHardTokenBatchJobSessionBean implements HardTokenBatchJobSessionRemote, HardTokenBatchJobSessionLocal  {

    public static final int MAX_RETURNED_QUEUE_SIZE = 300;

    private static final Logger log = Logger.getLogger(EjbcaHardTokenBatchJobSessionBean.class);
    
    /** Internal localization of logs and errors */
    private static final InternalResources intres = InternalResources.getInstance();
    
    /** Columns in the database used in select */
    private static final String USERDATA_COL = "username, subjectDN, subjectAltName, subjectEmail, status, type, clearPassword, timeCreated, timeModified, endEntityProfileId, certificateProfileId, tokenType, hardTokenIssuerId, cAId";

    @PersistenceContext(unitName="ejbca")
    private EntityManager entityManager;

    @EJB
    private HardTokenSessionLocal hardTokenSession;
    @EJB
    private LogSessionLocal logSession;

    /**
     * Returns the next user scheduled for batch generation for the given issuer.
     *
     * @param admin the administrator performing the actions
     *
     * @return The next user to generate or NULL if there are no users i queue.
     * @throws EJBException if a communication or other error occurs.
     */
    public UserDataVO getNextHardTokenToGenerate(Admin admin, String alias) throws UnavailableTokenException{
    	log.trace(">getNextHardTokenToGenerate()");
    	UserDataVO returnval = null;
    	if (log.isDebugEnabled()) {
    		log.debug("alias=" + alias);
    	}
    	int hardTokenIssuerId = hardTokenSession.getHardTokenIssuerId(admin, alias);
    	if (log.isDebugEnabled()) {
    		log.debug("hardTokenIssuerId=" + hardTokenIssuerId);
    	}
    	if (hardTokenIssuerId != LocalHardTokenSessionBean.NO_ISSUER) {
    		try {
    			List<UserData> userDataList = UserData.findNewOrKeyrecByHardTokenIssuerId(entityManager, hardTokenIssuerId, 0);
    			if (!userDataList.isEmpty()) {
    				returnval = userDataList.get(0).toUserDataVO();
    				log.debug("found user" + returnval.getUsername());
    				hardTokenSession.getIsHardTokenProfileAvailableToIssuer(admin, hardTokenIssuerId, returnval);
    				String msg = intres.getLocalizedMessage("hardtoken.userdatasent", alias);            	
    				logSession.log(admin, returnval.getCAId(), LogConstants.MODULE_HARDTOKEN, new java.util.Date(),returnval.getUsername(), null, LogConstants.EVENT_INFO_HARDTOKEN_USERDATASENT, msg);
    			}
    		} catch(Exception e) {
    			String msg = intres.getLocalizedMessage("hardtoken.errorsenduserdata", alias);            	
    			logSession.log(admin, admin.getCaId(), LogConstants.MODULE_HARDTOKEN, new java.util.Date(),null, null, LogConstants.EVENT_ERROR_HARDTOKEN_USERDATASENT, msg);
    			throw new EJBException(e);
    		}
    	}
    	log.trace("<getNextHardTokenToGenerate()");
    	return returnval;
    }

    /**
     * Returns a Collection of users scheduled for batch generation for the given issuer.
     * A maximum of MAX_RETURNED_QUEUE_SIZE users will be returned by call.
     *
     * @param admin the administrator performing the actions
     *
     * @return A Collection of users to generate or NULL if there are no users i queue.
     * @throws EJBException if a communication or other error occurs.
     */
    public Collection getNextHardTokensToGenerate(Admin admin, String alias) throws UnavailableTokenException {
    	log.trace(">getNextHardTokensToGenerate()");
    	ArrayList<UserDataVO> returnval = new ArrayList<UserDataVO>();
    	int hardTokenIssuerId = hardTokenSession.getHardTokenIssuerId(admin, alias);
    	if (hardTokenIssuerId != LocalHardTokenSessionBean.NO_ISSUER) {
    		try {
    			List<UserData> userDataList = UserData.findNewOrKeyrecByHardTokenIssuerId(entityManager, hardTokenIssuerId, MAX_RETURNED_QUEUE_SIZE);
    			for (UserData userData : userDataList) {
    				UserDataVO userDataVO = userData.toUserDataVO();
    				hardTokenSession.getIsHardTokenProfileAvailableToIssuer(admin, hardTokenIssuerId, userDataVO);
    				returnval.add(userDataVO);
    				String msg = intres.getLocalizedMessage("hardtoken.userdatasent", alias);            	
    				logSession.log(admin, userDataVO.getCAId(), LogConstants.MODULE_HARDTOKEN, new Date(), userDataVO.getUsername(), null, LogConstants.EVENT_INFO_HARDTOKEN_USERDATASENT, msg);
    			}
    		} catch(Exception e) {
    			String msg = intres.getLocalizedMessage("hardtoken.errorsenduserdata", alias);            	
    			logSession.log(admin, admin.getCaId(), LogConstants.MODULE_HARDTOKEN, new java.util.Date(),null, null, LogConstants.EVENT_ERROR_HARDTOKEN_USERDATASENT, msg);
    			throw new EJBException(e);
    		}
    	}
    	if (returnval.size()==0) {
    		returnval=null;
    	}
    	log.trace("<getNextHardTokensToGenerate()");
    	return returnval;
    }

    /**
     * Returns the indexed user in queue scheduled for batch generation for the given issuer.
     * 
     * TODO: Since there is no guarantee that the database query always will return entries in the same order, this functionality might be broken!
     *
     * @param admin the administrator performing the actions
     * @param index index in queue of user to retrieve. (First position is 1 according to old JDBC implementation.)
     *
     * @return The next token to generate or NULL if the given user doesn't exist in queue.
     * @throws EJBException if a communication or other error occurs.
     */
    public UserDataVO getNextHardTokenToGenerateInQueue(Admin admin, String alias, int index) throws UnavailableTokenException {
    	log.trace(">getNextHardTokenToGenerateInQueue()");
    	UserDataVO returnval=null;
    	int hardTokenIssuerId = hardTokenSession.getHardTokenIssuerId(admin, alias);
    	if (hardTokenIssuerId != LocalHardTokenSessionBean.NO_ISSUER) {
    		try {
    			List<UserData> userDataList = UserData.findNewOrKeyrecByHardTokenIssuerId(entityManager, hardTokenIssuerId, 0);
    			if (userDataList.size()>(index-1)) {
    				returnval = userDataList.get(index-1).toUserDataVO();
    				hardTokenSession.getIsHardTokenProfileAvailableToIssuer(admin, hardTokenIssuerId, returnval);
    				String msg = intres.getLocalizedMessage("hardtoken.userdatasent", alias);            	
    				logSession.log(admin, returnval.getCAId(), LogConstants.MODULE_HARDTOKEN, new java.util.Date(),returnval.getUsername(), null, LogConstants.EVENT_INFO_HARDTOKEN_USERDATASENT, msg);
    			}
    		} catch(Exception e) {
    			String msg = intres.getLocalizedMessage("hardtoken.errorsenduserdata", alias);            	
    			logSession.log(admin, admin.getCaId(), LogConstants.MODULE_HARDTOKEN, new java.util.Date(),null, null, LogConstants.EVENT_ERROR_HARDTOKEN_USERDATASENT, msg);
    			throw new EJBException(e);
    		}
    	}
    	log.trace("<getNextHardTokenToGenerateInQueue()");
    	return returnval;
    }

    /**
     * Returns the number of users scheduled for batch generation for the given issuer.
     *
     * @param admin the administrator performing the actions
     *
     * @return the number of users to generate.
     * @throws EJBException if a communication or other error occurs.
     */
    public int getNumberOfHardTokensToGenerate(Admin admin, String alias){
    	log.trace(">getNumberOfHardTokensToGenerate()");
    	int count = 0;
    	int hardTokenIssuerId = hardTokenSession.getHardTokenIssuerId(admin, alias);
    	if (hardTokenIssuerId != LocalHardTokenSessionBean.NO_ISSUER) {
    		count = Long.valueOf(UserData.countNewOrKeyrecByHardTokenIssuerId(entityManager, hardTokenIssuerId)).intValue();
    	}
    	log.trace("<getNumberOfHardTokensToGenerate()");
    	return count;
    }

    /**
     * Methods that checks if a user exists in the database having the given hard token issuer id. This function is mainly for avoiding
     * desyncronisation when a hard token issuer is deleted.
     *
     * @param hardtokenissuerid the id of hard token issuer to look for.
     * @return true if hardtokenissuerid exists in userdatabase.
     */
    public boolean checkForHardTokenIssuerId(Admin admin, int hardtokenissuerid){
    	if (log.isTraceEnabled()) {
            log.trace(">checkForHardTokenIssuerId(id: " + hardtokenissuerid + ")");
    	}
    	return UserData.countByHardTokenIssuerId(entityManager, hardtokenissuerid) > 0;
    }
}
