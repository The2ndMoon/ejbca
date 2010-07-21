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
 
package org.ejbca.ui.cli.ca;

import java.beans.XMLEncoder;
import java.io.File;
import java.io.FileOutputStream;
import java.util.Collection;
import java.util.Iterator;

import javax.ejb.EJB;

import org.ejbca.core.ejb.ca.caadmin.CAAdminSessionRemote;
import org.ejbca.core.ejb.ca.store.CertificateStoreSessionRemote;
import org.ejbca.core.ejb.ra.raadmin.RaAdminSessionRemote;
import org.ejbca.core.model.SecConst;
import org.ejbca.core.model.ca.certificateprofiles.CertificateProfile;
import org.ejbca.core.model.ra.raadmin.EndEntityProfile;
import org.ejbca.ui.cli.ErrorAdminCommandException;

/**
 * Export profiles from the database to XML-files.
 *
 * @version $Id$
 */
public class CaExportProfilesCommand extends BaseCaAdminCommand {

    @EJB
    private CAAdminSessionRemote caAdminSession;
    
    @EJB
    private RaAdminSessionRemote raAdminSession;
    
    @EJB
    private CertificateStoreSessionRemote certificateStoreSession;
    
	public String getMainCommand() { return MAINCOMMAND; }
	public String getSubCommand() { return "exportprofiles"; }
	public String getDescription() { return "Export profiles from the database to XML-files."; }

    public void execute(String[] args) throws ErrorAdminCommandException {
        try {
            if (args.length < 2) {
    			getLogger().info("Description: " + getDescription());
                getLogger().info("Usage: " + getCommand() + " <outpath>");
                return;
            }
            String outpath = args[1];
            if (!new File(outpath).isDirectory()) {
            	getLogger().error("Error: '"+outpath+"' is not a directory.");
                return;
            }
            Collection certprofids = certificateStoreSession.getAuthorizedCertificateProfileIds(getAdmin(),0, caAdminSession.getAvailableCAs(getAdmin()));                                               
			Collection endentityprofids = raAdminSession.getAuthorizedEndEntityProfileIds(getAdmin());
            
			getLogger().info("Exporting non-fixed certificate profiles: ");
            Iterator iter = certprofids.iterator();
            while (iter.hasNext()) {
            	int profileid = ((Integer) iter.next()).intValue();
                if (profileid == SecConst.PROFILE_NO_PROFILE) { // Certificate profile not found i database.
                	getLogger().error("Couldn't find certificate profile '"+profileid+"' in database.");
                } else if (SecConst.isFixedCertificateProfile(profileid)) {
                    //getLogger().debug("Skipping export fixed certificate profile with id '"+profileid+"'.");
                } else {
					String profilename = certificateStoreSession.getCertificateProfileName(getAdmin(), profileid);									
                    CertificateProfile profile = certificateStoreSession.getCertificateProfile(getAdmin(),profileid);
                    if (profile == null) {
                    	getLogger().error("Couldn't find certificate profile '"+profilename+"'-"+profileid+" in database.");
                    } else {
                        String outfile = outpath+"/certprofile_"+profilename+"-"+profileid+".xml";
                        getLogger().info(outfile+".");
                        XMLEncoder encoder = new XMLEncoder(new  FileOutputStream(outfile));
                        encoder.writeObject(profile.saveData());
                        encoder.close();
                    }
                }
            }
            getLogger().info("Exporting non-fixed end entity profiles: ");
            iter = endentityprofids.iterator();
            while (iter.hasNext()){                
                int profileid = ((Integer) iter.next()).intValue();
                if (profileid == SecConst.PROFILE_NO_PROFILE) { // Entity profile not found i database.
                	getLogger().error("Error : Couldn't find entity profile '"+profileid+"' in database.");
                } else if (profileid == SecConst.EMPTY_ENDENTITYPROFILE) {
                    //getLogger().debug("Skipping export fixed end entity profile with id '"+profileid+"'.");
                } else {
                	String profilename = raAdminSession.getEndEntityProfileName(getAdmin(), profileid);
                    EndEntityProfile profile = raAdminSession.getEndEntityProfile(getAdmin(), profileid);
                    if (profile == null) {
                    	getLogger().error("Error : Couldn't find entity profile '"+profilename+"'-"+profileid+" in database.");
                    } else {
                        String outfile = outpath+"/entityprofile_"+profilename+"-"+profileid+".xml";
                        getLogger().info(outfile+".");
                        XMLEncoder encoder = new XMLEncoder(new  FileOutputStream(outfile));
                        encoder.writeObject(profile.saveData());
                        encoder.close();
                    }
                }
            }         
        } catch (Exception e) {
            throw new ErrorAdminCommandException(e);
        }
    }
}
