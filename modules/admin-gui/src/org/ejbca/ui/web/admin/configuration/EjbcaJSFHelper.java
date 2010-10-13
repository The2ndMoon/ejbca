package org.ejbca.ui.web.admin.configuration;

import java.util.Map;

import javax.ejb.EJB;
import javax.faces.application.Application;
import javax.faces.context.FacesContext;
import javax.faces.el.ValueBinding;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;

import org.apache.log4j.Logger;
import org.cesecore.core.ejb.ra.raadmin.EndEntityProfileSessionLocal;
import org.ejbca.config.WebConfiguration;
import org.ejbca.core.ejb.approval.ApprovalSessionLocal;
import org.ejbca.core.ejb.authorization.AuthorizationSessionLocal;
import org.ejbca.core.ejb.ca.caadmin.CAAdminSessionLocal;
import org.ejbca.core.ejb.ca.publisher.PublisherSessionLocal;
import org.ejbca.core.ejb.ra.raadmin.RaAdminSessionLocal;
import org.ejbca.core.ejb.ra.userdatasource.UserDataSourceSessionLocal;
import org.ejbca.core.ejb.services.ServiceSessionLocal;
import org.ejbca.core.model.authorization.AccessRulesConstants;
import org.ejbca.core.model.authorization.AuthorizationDeniedException;
import org.ejbca.core.model.log.Admin;

import com.sun.corba.se.spi.legacy.connection.GetEndPointInfoAgainException;

/**
 * Class used to integrate the old jsp framework with the new JSF one.
 * Contains methods for such things as language, themes ext
 * 
 * @author Philip Vendil
 * $Id$
 */
public class EjbcaJSFHelper {
	private static final Logger log = Logger.getLogger(EjbcaJSFHelper.class);
		
	private EjbcaJSFLanguageResource text = null;
	private EjbcaJSFImageResource image = null;
	private EjbcaWebBean ejbcawebbean;
	
	private boolean initialized = false;
	
	@EJB
	private RaAdminSessionLocal raAdminSession;
	@EJB
	private CAAdminSessionLocal caAdminSession;
	@EJB
	private EndEntityProfileSessionLocal endEntityProfileSession;
	@EJB
	private ApprovalSessionLocal approvalSession;
	@EJB
	private ServiceSessionLocal serviceSession;
	@EJB
	private UserDataSourceSessionLocal userDataSourceSession;
	@EJB
	private PublisherSessionLocal publisherSession;
	@EJB
	private AuthorizationSessionLocal authorizationSession;
	
	public EjbcaJSFHelper(){}
	
    public void setEjbcaWebBean(EjbcaWebBean ejbcawebbean){
    	if(!initialized){
    		this.ejbcawebbean = ejbcawebbean;
    		text = new EjbcaJSFLanguageResource(ejbcawebbean);
    		image = new EjbcaJSFImageResource(ejbcawebbean);
    		initialized = true;
    	}
    }
    
    /**
     * Returns the EJBCA title
     */
    public String getEjbcaTitle(){
    	return getEjbcaWebBean().getGlobalConfiguration().getEjbcaTitle();
    }
    
    /**
     * Returns the EJBCA theme
     */
    public String getTheme(){
    	return getEjbcaWebBean().getCssFile();
    }
    
    /**
     * Returns the EJBCA base url
     */
    public String getEjbcaBaseURL(){
    	return getEjbcaWebBean().getBaseUrl();
    }   
    
    /**
     * Returns the EJBCA content string
     */
    public String getContent(){
    	return "text/html; charset=" + WebConfiguration.getWebContentEncoding();
    } 
    
   /**
    * Used for language resources.
    */
    public Map getText(){
    	setEjbcaWebBean(getEjbcaWebBean());
    	return text;
    }
    
    /**
     * Used for image resources.
     */
     public Map getImage(){
        setEjbcaWebBean(getEjbcaWebBean());
     	return image;
     }
    
     /**
      * Special function for approval pages since it has two different accessrules
     * @throws AuthorizationDeniedException 
      *
      */
     public void authorizedToApprovalPages() throws AuthorizationDeniedException{
		  // Check Authorization
 		boolean approveendentity = false;
 		boolean approvecaaction = false;
 		try{
 			approveendentity = getEjbcaWebBean().isAuthorizedNoLog(AccessRulesConstants.REGULAR_APPROVEENDENTITY);
 		}catch(AuthorizationDeniedException e){}
 		try{
 			approvecaaction = getEjbcaWebBean().isAuthorizedNoLog(AccessRulesConstants.REGULAR_APPROVECAACTION);
 		}catch(AuthorizationDeniedException e){}		
 		if(!approveendentity && !approvecaaction){
 			throw new AuthorizationDeniedException("Not authorized to view approval pages");
 		}
     }
     
     /**
      * Only superadmins are authorized to services pages
     * @throws AuthorizationDeniedException 
      *
      */
     public void authorizedToServicesPages() throws AuthorizationDeniedException{
		getEjbcaWebBean().isAuthorizedNoLog(AccessRulesConstants.ROLE_SUPERADMINISTRATOR);
     }
     
    public int getEntriesPerPage(){
        return getEjbcaWebBean().getEntriesPerPage();
    }
    
    public EjbcaWebBean getEjbcaWebBean(){
    	
    	if(ejbcawebbean == null){
    		FacesContext ctx = FacesContext.getCurrentInstance();    		    	

    		HttpSession session = (HttpSession) ctx.getExternalContext().getSession(true);
 
    		    		
    		synchronized (session) {
    			ejbcawebbean = (org.ejbca.ui.web.admin.configuration.EjbcaWebBean) session.getAttribute("ejbcawebbean");
    			if (ejbcawebbean == null){
    				ejbcawebbean = new org.ejbca.ui.web.admin.configuration.EjbcaWebBean();
    				session.setAttribute("ejbcawebbean", ejbcawebbean);
    			}
    		}
    		
    		try {
				ejbcawebbean.initialize((HttpServletRequest) ctx.getExternalContext().getRequest(), "/administrator");
			} catch (Exception e) {
				log.error(e);
			}
    	}
    	
    	return ejbcawebbean;
    }
 
	public Admin getAdmin() {
		return getEjbcaWebBean().getAdminObject();
	  }
    
    public static EjbcaJSFHelper getBean(){    
    	FacesContext context = FacesContext.getCurrentInstance();    
    	Application app = context.getApplication();    
    	ValueBinding binding = app.createValueBinding("#{web}");    
    	Object value = binding.getValue(context);    
    	return (EjbcaJSFHelper) value;
    }
    
    public RaAdminSessionLocal getRaAdminSession(){
    	return raAdminSession;
    }

    public CAAdminSessionLocal getCAAdminSession(){
    	return caAdminSession;
    }
    
    public ApprovalSessionLocal getApprovalSession(){
    	return approvalSession;
    }    
	
    
    public ServiceSessionLocal getServiceSession(){
    	return serviceSession;
    }
    
    public UserDataSourceSessionLocal getUserDataSourceSession(){
    	return userDataSourceSession;
    }

    public PublisherSessionLocal getPublisherSession(){
    	return publisherSession;
    }

    public AuthorizationSessionLocal getAuthorizationSession(){
    	return authorizationSession;
    }
    
    public EndEntityProfileSessionLocal getEndEntityProfileSession() {
        return endEntityProfileSession;
    }
}
