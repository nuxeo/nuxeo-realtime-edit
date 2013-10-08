package org.nuxeo.ecm.platform.realtime.edit;

import static org.jboss.seam.ScopeType.EVENT;

import java.io.Serializable;

import javax.faces.context.ExternalContext;
import javax.faces.context.FacesContext;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.jboss.seam.annotations.In;
import org.jboss.seam.annotations.Name;
import org.jboss.seam.annotations.Scope;
import org.nuxeo.ecm.core.api.ClientException;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.api.security.SecurityConstants;
import org.nuxeo.ecm.core.schema.FacetNames;
import org.nuxeo.ecm.platform.mimetype.interfaces.MimetypeRegistry;
import org.nuxeo.runtime.api.Framework;

@Scope(EVENT)
@Name("realtimeEditHelper")
public class RealtimeEditHelper implements Serializable {

    private static final long serialVersionUID = 1L;

    private static final Log log = LogFactory.getLog(RealtimeEditHelper.class);

    @In(create = true, required = false)
    protected transient CoreSession documentManager;
    protected MimetypeRegistry mimetypeRegistry;
    protected RealtimeEditServiceManager manager;

    public String edit(DocumentModel document) throws Exception{
        return edit(document, "etherpad");
    }

    public String edit(DocumentModel document, String serviceName) throws Exception{

        String curService = getCurrentEditingService(document);

        if (curService != null && !curService.equals(serviceName)) {
            return null; // TODO
        }

        documentManager.saveDocument(document);
        documentManager.save();

        RealtimeEditService service = getRealtimeEditServiceManager().getService(serviceName);
  
        RealtimeEditSession lastSession = getRealtimeEditServiceManager().getOrCreateEditingSession(serviceName, document);
        RealtimeEditSession newSession  = service.joinSession(lastSession, documentManager.getPrincipal().getName());
        
        if(newSession.hasRealtimeEditCookies()){
        	
        	 FacesContext context = FacesContext.getCurrentInstance();
             ExternalContext eContext = context.getExternalContext();
             HttpServletResponse response = (HttpServletResponse) eContext.getResponse();
            
             Cookie[] cookies = newSession.getRealtimeEditSessionCookies();
             for (int i = 0; i < cookies.length; i++) {
				response.addCookie(cookies[i]);
			}
        }
        
        return "realtime_edit_embedded";
    }

    public String getEmbeddedURL(DocumentModel document) throws Exception {
    	
        String serviceName = getCurrentEditingService(document);
        RealtimeEditService service = getRealtimeEditServiceManager().getService(serviceName);
        
        RealtimeEditSession session = getCurrentEditingSession(document);
        RealtimeEditSession newSession = service.joinSession(session, documentManager.getPrincipal().getName());
        
        return newSession.getRealtimeEditSessionURL();
    }

    public String save(DocumentModel document) throws Exception {
        // save the session
        getRealtimeEditServiceManager().saveCurrentEditingSession(document);

        // close the session
        getRealtimeEditServiceManager().cancelCurrentEditingSession(document);

        documentManager.saveDocument(document);
        documentManager.save();

        return "view_documents";
    }

    public String cancel(DocumentModel document) throws Exception {
        getRealtimeEditServiceManager().cancelCurrentEditingSession(document);
        return "view_documents";
    }

    public RealtimeEditSession getCurrentEditingSession(DocumentModel document) throws Exception {
        String serviceName = getCurrentEditingService(document);
        return getRealtimeEditServiceManager().getCurrentEditingSession(serviceName, document);
    }

    public String getCurrentEditingService(DocumentModel document) throws Exception {
        return getRealtimeEditServiceManager().getCurrentEditingService(document);
    }

    public boolean isRealtimeEditable(DocumentModel documentModel) {

        boolean isEditable = false;

        try {

            if (documentModel.hasFacet(FacetNames.IMMUTABLE)) {
                return false;
            }

            try {
                if (!documentManager.hasPermission(documentModel.getRef(),
                        SecurityConstants.WRITE_PROPERTIES)) {
                    return false;
                }
            } catch (ClientException e) {
                // the document no longer exist in the core
                log.warn(String.format(
                        "document '%s' with reference '%s' no longer exists in the database, "
                                + "please ensure the indexes are up to date",
                        documentModel.getTitle(), documentModel.getRef()));
                return false;
            }
            // Check if there is any realtime editing service available
            if (!getRealtimeEditServiceManager().isRealtimeEditServiceAvailable()) {
                return false;
            }

            return getRealtimeEditServiceManager().isRealtimeEditable(documentModel);

        } catch (Exception e) {
            log.error(e.getMessage());
        }

        return isEditable;
    }



    protected MimetypeRegistry getMimetypeRegistry() throws Exception {
        if (mimetypeRegistry == null) {
            mimetypeRegistry = Framework.getService(MimetypeRegistry.class);
        }
        return mimetypeRegistry;
    }

    protected RealtimeEditServiceManager getRealtimeEditServiceManager() throws Exception {
        if (manager == null) {
            manager = Framework.getService(RealtimeEditServiceManager.class);
        }
        return manager;
    }
}

