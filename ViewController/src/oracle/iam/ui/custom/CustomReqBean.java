package oracle.iam.ui.custom;

import java.util.logging.Logger;
import oracle.adf.view.rich.event.DialogEvent;
import java.util.logging.Level;  
import java.util.logging.Logger;  
import javax.faces.application.FacesMessage;  
import javax.faces.context.FacesContext;  
import oracle.adf.view.rich.event.DialogEvent;  
import oracle.iam.identity.usermgmt.api.UserManager;  
import oracle.iam.ui.platform.model.common.OIMClientFactory; // /home/oracle/Oracle/Middleware/Oracle_IDM1//server/modules/oracle.iam.ui.model_11.1.2/adflibPlatformModel.jar

public class CustomReqBean 
{
    public CustomReqBean() {
        super();
    }
    
    private static Logger logger = Logger.getLogger("IAM.DEMO");  
     
         
       public void confirmReset(DialogEvent evt){   
             
           logger.entering(this.getClass().getName(), "confirmReset ","confirm outcome is "+ evt.getOutcome().name());  
             
           try {  
               if (evt.getOutcome().compareTo(DialogEvent.Outcome.yes) == 0) {  
                 
                   String userLogin  = FacesUtils.getAttributeBindingValue("userLogin",String.class).trim();  
                     
                   UserManager userMgtService = OIMClientFactory.getUserManager();     
                   userMgtService.resetPassword(userLogin,true,true);  
     
                   this.setFacesMessage("Password for user "+userLogin+" has been reset successfully!");  
                     
                   logger.logp(Level.FINEST, this.getClass().getName(), "confirmReset", "Reset password for user "+userLogin);  
               }  
           }  
           catch (Exception e) {  
               this.setFacesMessage("An internal error has occourred: "+e.getLocalizedMessage());  
               logger.logp(Level.SEVERE, this.getClass().getName(), "confirmReset", "Error changing user status",e);     
           }  
           logger.exiting(this.getClass().getName(), "confirmReset");  
       }  
         
       private void setFacesMessage(String msg) {  
             
           FacesMessage message = new FacesMessage();  
           message.setDetail(msg);  
           message.setSummary(msg);  
           message.setSeverity(FacesMessage.SEVERITY_INFO);  
     
           FacesContext context = FacesContext.getCurrentInstance();  
           context.addMessage(null, message);  
       }  
}
