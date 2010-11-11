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
 
package org.ejbca.ui.web.pub;

import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Iterator;
import java.util.Set;

import javax.ejb.EJB;
import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.lang.StringUtils;
import org.apache.log4j.Logger;
import org.cesecore.core.ejb.ca.store.CertificateProfileSessionLocal;
import org.cesecore.core.ejb.log.LogSessionLocal;
import org.cesecore.core.ejb.ra.raadmin.EndEntityProfileSessionLocal;
import org.ejbca.core.ejb.authorization.AuthorizationSessionLocal;
import org.ejbca.core.ejb.ca.caadmin.CaSessionLocal;
import org.ejbca.core.ejb.ra.raadmin.RaAdminSessionLocal;
import org.ejbca.core.model.log.Admin;
import org.ejbca.core.model.ra.raadmin.GlobalConfiguration;

/**
 * Servlet used to clear all caches (Global Configuration Cache, End Entity Profile Cache, 
 * Certificate Profile Cache, Log Configuration Cache, Authorization Cache and CA Cache).
 *
 * @author Aveen Ismail
 * 
 * @version $Id$
 */
public class ClearCacheServlet extends HttpServlet {

	private static final long serialVersionUID = -8563174167843989458L;
	private static final Logger log = Logger.getLogger(ClearCacheServlet.class);
	
	@EJB
	private RaAdminSessionLocal raadminsession;
	@EJB
	private EndEntityProfileSessionLocal endentitysession;
	@EJB
	private CertificateProfileSessionLocal certificateprofilesession;
	@EJB
	private AuthorizationSessionLocal authorizationsession;
	@EJB
	private LogSessionLocal logsession;
	@EJB
	private CaSessionLocal casession;
	
    public void init(ServletConfig config) throws ServletException {
        super.init(config);
    }
  
    public void doPost(HttpServletRequest req, HttpServletResponse res)	throws IOException, ServletException {
    	doGet(req,res);
    }


	public void doGet(HttpServletRequest req, HttpServletResponse res)
        throws IOException, ServletException {
		if (log.isTraceEnabled()) {
			log.trace(">doGet()");
		}
        
        if (StringUtils.equals(req.getParameter("command"), "clearcaches")) {
            if(!acceptedHost(req.getRemoteHost())) {
        		if (log.isDebugEnabled()) {
        			log.debug("Clear cache request denied from host "+req.getRemoteHost());
        		}
        		res.sendError(HttpServletResponse.SC_UNAUTHORIZED, "The remote host "+req.getRemoteHost()+" is unknown");
        	} else {       
        		raadminsession.flushGlobalConfigurationCache();
        		if(log.isDebugEnabled()){
        			log.debug("Global Configuration cache cleared");
        		}
        			
        		endentitysession.flushProfileCache();
        		if(log.isDebugEnabled()) {
        			log.debug("RA Profile cache cleared");
        		}
        		
        		certificateprofilesession.flushProfileCache();
        		if(log.isDebugEnabled()) {
        			log.debug("Cert Profile cache cleared");
        		}
        	       			
        		authorizationsession.flushAuthorizationRuleCache();
        		if(log.isDebugEnabled()) {
        			log.debug("Authorization Rule cache cleared");
        		}
			
        		logsession.flushConfigurationCache();
        		if(log.isDebugEnabled()) {
        			log.debug("Log Configuration cache cleared");
        		}
        	
        		casession.flushCACache();
        		if(log.isDebugEnabled()) {
        			log.debug("CA cache cleared");
        		}
        	}
        } else {
    		if (log.isDebugEnabled()) {
    			log.debug("No clearcaches command (?command=clearcaches) received, returning bad request.");
    		}
			res.sendError(HttpServletResponse.SC_BAD_REQUEST, "No command.");
        }
		if (log.isTraceEnabled()) {
			log.trace("<doGet()");
		}
    }

	private boolean acceptedHost(String remotehost) {
		if (log.isTraceEnabled()) {
			log.trace(">acceptedHost: "+remotehost);
		}    	
		boolean ret = false;
		GlobalConfiguration gc = raadminsession.getCachedGlobalConfiguration(new Admin(Admin.TYPE_INTERNALUSER));
		Set<String> nodes = gc.getNodesInCluster();
		Iterator<String> itr = nodes.iterator();
		String nodename = null;
		while (itr.hasNext()) {
			nodename = itr.next();
			try {
				if (StringUtils.equals(remotehost, InetAddress.getByName(nodename).getHostAddress())) {
					ret = true;
				}
			} catch (UnknownHostException e) {
				if (log.isDebugEnabled()) {
					log.debug("Unknown host '"+nodename+"': "+e.getMessage());
				}
			}
		}
		if (log.isTraceEnabled()) {
			log.trace("<acceptedHost: "+ret);
		}
		return ret;
	}
}
