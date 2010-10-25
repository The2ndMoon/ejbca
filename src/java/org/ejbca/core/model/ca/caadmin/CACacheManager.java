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

package org.ejbca.core.model.ca.caadmin;

import java.util.Date;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

import org.apache.log4j.Logger;


/**
 * Class managing a cache of CAs. It is not really a cache, just an object registry.
 * 
 * @version $Id$
 * 
 */
public class CACacheManager {

    /** Log4j instance for Base */
    private static final transient Logger log = Logger.getLogger(CACacheManager.class);

    /** Registry of CAs, kept so CAs are cached and don't have to be read from memory every time */
    private Hashtable caRegistry = new Hashtable();
    /** Mapping of CAId and CAName */
    private Map caNameToCaId = new HashMap();

    /** Implementing the Singleton pattern */
    private static CACacheManager instance = null;

    /** Don't allow external creation of this class, implementing the Singleton pattern. 
     */
    private CACacheManager() {}
    
    /** Get the instance of this singleton
     * 
     */
    public synchronized static CACacheManager instance() {
        if (instance == null) {
            instance = new CACacheManager();
        }
        return instance;
    }

    /**
     * Returns a previously registered (using addCA) CA, or null.
     *
     * @param caid the id of the CA whose CA object you want to fetch.
     * @return The previously added CA or null if the CA does not exist in the registry.
     */
    public CA getCA(int caid) {
        CA ret = (CA)caRegistry.get(Integer.valueOf(caid));
        if (ret != null) {
        	ret.setCAId(caid);
        }
        return ret;
    }

    /**
     * Returns a previously registered (using addCA) CA, or null.
     *
     * @param caid the id of the CA whose CA object you want to fetch.
     * @param caStatus
     * @param caExpireTime
     * @param caName
     * @param caSubjectDN
     * @return The previously added CA or null if the CA does not exist in the registry.
     */
    public CA getAndUpdateCA(int caid, int caStatus, long caExpireTime, String caName, String caSubjectDN) {
        CA ret = (CA)caRegistry.get(Integer.valueOf(caid));
        if (ret != null) {
           	populateCAObject(caStatus, caExpireTime, caName, caSubjectDN, ret);
        	ret.setCAId(caid);
        }
        return ret;
    }

    /** Returns a previously registered (using addCA) CA, or null.
     * 
     * @param caName the name of the CA whose CA object you want to fetch.
     * @return The previously added CA or null if the CA does not exist in the registry.
     */
    public CA getCA(String caName) {
    	Object o = this.caNameToCaId.get(caName);
    	CA ret = null;
    	if (o != null) {
    		final Integer caid = (Integer)o;
            ret = getCA(caid);
    	}
        return ret;    		
    }

    /** Returns a previously registered (using addCA) CA, or null.
     * 
     * @param caName the name of the CA whose CA object you want to fetch.
     * @param caStatus
     * @param caExpireTime
     * @param caSubjectDN
     * @return The previously added CA or null if the CA does not exist in the registry.
     */
    public CA getAndUpdateCA(String caName, int caStatus, long caExpireTime, String caSubjectDN) {
    	Object o = this.caNameToCaId.get(caName);
    	CA ret = null;
    	if (o != null) {
    		final Integer caid = (Integer)o;
            ret = getAndUpdateCA(caid, caStatus, caExpireTime, caName, caSubjectDN);
    	}
        return ret;    		
    }

	private void populateCAObject(int caStatus, long caExpireTime, String caName, String caSubjectDN, CA ret) {
		// We mainly cache the xml data, some of the other values may change slightly at will...
		ret.setStatus(caStatus);
		ret.setExpireTime(new Date(caExpireTime));
		ret.setName(caName);
		ret.setSubjectDN(caSubjectDN);
	}
    
    /** Adds a CA to the registry. If a CA already exists for the given CAid, 
     * the old one is removed and replaced with the new. If the CA passed is null, an existing CA is removed.
     * 
     * @param caid the id of the CA you want to fetch.
     * @param ca the CA to be added
     */
    public synchronized void addCA(int caid, CA ca) {
    	removeCA(caid);
        if (ca != null) {
            caRegistry.put(Integer.valueOf(caid), ca);
            this.caNameToCaId.put(ca.getName(), Integer.valueOf(caid));
            log.debug("Added CA to registry: "+caid);
        }
    }    
    
    /** Removes a CA from the cache to force an update the next time the CA is read
     * 
     */
    public synchronized void removeCA(int caid) {
        if (caRegistry.containsKey(new Integer(caid))) {
            caRegistry.remove(new Integer(caid));
            // Remove all possible mappings from caname to this id, we may have changed CAName
            if (this.caNameToCaId.containsValue(Integer.valueOf(caid))) {
            	Set entrySet = this.caNameToCaId.entrySet();
            	for (Iterator iterator = entrySet.iterator(); iterator.hasNext();) {
            		Map.Entry e = (Map.Entry) iterator.next();
            		if (e.getValue().equals(caid)) {
            			iterator.remove();
            		}
				}
            }
            log.debug("Removed old CA from registry: "+caid);
        }
    }
    
    public synchronized void removeAll() {
		caRegistry = new Hashtable();
		caNameToCaId = new HashMap();
	}
}
