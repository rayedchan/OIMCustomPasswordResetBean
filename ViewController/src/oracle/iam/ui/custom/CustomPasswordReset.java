package oracle.iam.ui.custom;

import java.text.MessageFormat;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Random;
import javax.faces.application.FacesMessage;
import javax.faces.context.FacesContext;
import oracle.adf.view.rich.event.DialogEvent;
import oracle.core.ojdl.logging.ODLLevel;
import oracle.core.ojdl.logging.ODLLogger;
import oracle.iam.identity.exception.NoSuchUserException;
import oracle.iam.identity.exception.UserLookupException;
import oracle.iam.identity.usermgmt.api.UserManager;
import oracle.iam.identity.usermgmt.api.UserManagerConstants;
import oracle.iam.identity.usermgmt.vo.User;
import oracle.iam.passwordmgmt.api.PasswordMgmtService;
import oracle.iam.passwordmgmt.vo.PasswordPolicyInfo;
import oracle.iam.passwordmgmt.vo.ValidationResult;
import oracle.iam.ui.platform.model.common.OIMClientFactory;

/**
 * Custom Password Reset
 * - Generates a password that conforms to password policy
 * - Displays generated password in UI
 */
public class CustomPasswordReset 
{
    // Logger
    private static final ODLLogger logger = ODLLogger.getODLLogger(CustomPasswordReset.class.getName());
    
    private static final String DEFAULT_SYMBOLS = "!@#$%";
    private static final String DEFAULT_POSSIBLE_CHARACTERS="abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789" + DEFAULT_SYMBOLS;
    private static final String USER_LOGIN_ATTRIBUTE = "userLogin";
    
    /**
     * Confimation dialog box 
     * @param evt
     */
    public void confirmReset(DialogEvent evt) 
    {
        logger.log(ODLLevel.NOTIFICATION, "Enter confirmReset with outcome: [{0}]", new Object[]{evt.getOutcome().name()});

        try 
        {
            // User selects "yes" on the dialog box
            if (evt.getOutcome().compareTo(DialogEvent.Outcome.yes) == 0) 
            {
                // Get the target User Login
                String userLogin = FacesUtils.getAttributeBindingValue(USER_LOGIN_ATTRIBUTE, String.class).trim();
                logger.log(ODLLevel.NOTIFICATION, "Target User Login: [{0}]", new Object[]{userLogin});
                
                // Generate Random Password
                String generatedPassword = generatePassword(userLogin);
                logger.log(ODLLevel.TRACE, "Generated Password: [{0}]", new Object[]{generatedPassword});
                
                // Change Target User's password
                UserManager userManager = OIMClientFactory.getUserManager();
                userManager.changePassword(userLogin, generatedPassword.toCharArray(), true, false);
               
                this.setFacesMessage(MessageFormat.format("Generated password for {0}: {1}", userLogin, generatedPassword));
                logger.log(ODLLevel.NOTIFICATION, "Reset password for user {0}", new Object[]{userLogin});
            }
        } 
        
        catch (Exception e) 
        {
            this.setFacesMessage("An internal error has occourred: " + e.getLocalizedMessage());
            logger.log(ODLLevel.ERROR, "", e);
        }
        
        logger.exiting(this.getClass().getName(), "confirmReset");
    }

    /**
     * Used to display results in the UI 
     * @param msg   Message to display in UI
     */
    private void setFacesMessage(String msg) 
    {
        FacesMessage message = new FacesMessage();
        message.setDetail(msg);
        message.setSummary(msg);
        message.setSeverity(FacesMessage.SEVERITY_INFO);

        FacesContext context = FacesContext.getCurrentInstance();
        context.addMessage(null, message);
    }
    
    /**
    * Generates a temporary password that conforms to:  
    *  - OIM password policy that is applicable to target user
    *  - Custom password policy rules
    *      At least one number or symbol
    *      The email address of the user [IGNORE CASE]
    *      The user's display name [IGNORE CASE]; Remove spaces
    * @param userLogin    OIM.User Login (USR_LOGIN)
    * @return Generated password
    */
   private String generatePassword(String userLogin) throws NoSuchUserException, UserLookupException, Exception 
   {
       logger.log(ODLLevel.NOTIFICATION, "Enter generatePassword with parameter: [User Login: {0}]", new Object[]{userLogin});
       String passwordVal = "";
       ValidationResult vr = null;
       boolean matchCustomPasswordPolicy = false;
               
       // Get OIM Services
       PasswordMgmtService pwdMgmtService = OIMClientFactory.getPasswordMgmtService();
       UserManager userManager = OIMClientFactory.getUserManager();
               
       try 
       {
           // Fetch target user attributes
           HashSet<String> retUserAttrs = new HashSet<String>();
           retUserAttrs.add(UserManagerConstants.AttributeName.USER_KEY.getId()); // usr_key
           retUserAttrs.add(UserManagerConstants.AttributeName.FIRSTNAME.getId());
           retUserAttrs.add(UserManagerConstants.AttributeName.LASTNAME.getId());
           retUserAttrs.add(UserManagerConstants.AttributeName.DISPLAYNAME.getId());
           retUserAttrs.add(UserManagerConstants.AttributeName.EMAIL.getId());
           User user = userManager.getDetails(userLogin, retUserAttrs, true);
           logger.log(ODLLevel.NOTIFICATION, "User: {0}", new Object[]{user});
           
           // Make dummy call in order to obtain the password policy applicable to current user
           vr = pwdMgmtService.validatePasswordAgainstPolicy(passwordVal.toCharArray(), user.getEntityId(), Locale.getDefault(), false);
           PasswordPolicyInfo userPwdPolicy = vr.getPasswordPolicyInfo();
           
           // Validation check on password policy
           /*if (userPwdPolicy.getMaxLength() == null) 
           {
               throw new Exception("Password Policy must have a value for max length since the max property is used as the length of the generated password.");
           }
           
           if (userPwdPolicy.getAllowedChars().size() == 0) 
           {
               throw new Exception("Password Policy must have some value for allow character property");
           }*/
           
           // Fetch Password Policy data to use for generateing the password
           int maxPwdLength = (userPwdPolicy.getMaxLength() == null) ? 32 : userPwdPolicy.getMaxLength(); // Use length 32 if password policy does not specify a value for max length
           LinkedHashSet<Character> allowChars = userPwdPolicy.getAllowedChars(); 
           logger.log(ODLLevel.NOTIFICATION, "Allow Characters: [{0}] Size: [{1}] ", new Object[]{allowChars, allowChars.size()});
           logger.log(ODLLevel.NOTIFICATION, "Max Password Length: {0}", new Object[]{maxPwdLength});
           
           // Generator object
           Random rand = new Random();
           
           // Possible characters for generated password
           char[] possibleCharsForPwd = (userPwdPolicy.getAllowedChars().size() == 0) ? DEFAULT_POSSIBLE_CHARACTERS.toCharArray() : new char[allowChars.size()];
           String symbols = "";
           int count = 0;
           
           // Put allow characters into an array
           for (Character c : allowChars) 
           {
               possibleCharsForPwd[count] = c.charValue();
               count++;
               
               // Check if character is a symbol; assume non alpha-numeric characters are symbols
               if (!(String.valueOf(c).matches("^[a-zA-Z0-9]*$")))
               {
                   symbols = symbols + String.valueOf(c); // add character to string
               }
           }
           
           symbols = symbols.equals("") ? DEFAULT_SYMBOLS : symbols; // default symbols
           logger.log(ODLLevel.NOTIFICATION, "Possible characters for generated password: {0}", new Object[]{possibleCharsForPwd});
           logger.log(ODLLevel.NOTIFICATION, "Symbols: {0}", new Object[]{symbols});
           
           logger.info("Begin random password generation iteration");
           do 
           {
               char ch;
               passwordVal = "";
                   
               // Construct random password; Use max length as the size of the genereated password
               for (int i = 0; i < maxPwdLength; i++) 
               {
                   ch = possibleCharsForPwd[rand.nextInt(possibleCharsForPwd.length - 1)];
                   passwordVal = passwordVal + Character.toString(ch); 
               }

               // Check against OIM password policy that is applicable to user
               vr = pwdMgmtService.validatePasswordAgainstPolicy(passwordVal.toCharArray(), user.getEntityId(), Locale.getDefault(), false);
               logger.log(ODLLevel.NOTIFICATION, "Password Policy: {0}", new Object[]{vr.getPasswordPolicyInfo()});
               logger.log(ODLLevel.NOTIFICATION, "Does password conform to OIM password policy? {0}", new Object[]{vr.isPasswordValid()});
               
               // Check against custom password policy rules
               // At least one number or symbol
               // The email address of the user [IGNORE CASE]
               // The user's display name [IGNORE CASE]; Remove spaces
               String customPasswordPolicyRules = "^((?=.*[0-9])|(?=.*["+ symbols +"]))(?!.*(?i)(" + user.getEmail() + "))(?!.*(?i)(" + user.getDisplayName().replaceAll("\\s+","") + ")).*$";
               logger.log(ODLLevel.NOTIFICATION, "Custom Password Policy Rule: {0}", new Object[]{customPasswordPolicyRules});
               matchCustomPasswordPolicy = passwordVal.matches(customPasswordPolicyRules);
               logger.log(ODLLevel.NOTIFICATION, "Does password conform to custom password policy? {0}", new Object[]{matchCustomPasswordPolicy});
               logger.log(ODLLevel.TRACE, "Generated Password: {0}", new Object[]{passwordVal}); // TODO: Remove
               
           } while (vr.isPasswordValid() == false || matchCustomPasswordPolicy == false);

           logger.log(ODLLevel.NOTIFICATION, "Generated password validated.");
           return passwordVal;            
       } 
       
       catch (NoSuchUserException e) 
       {
           logger.log(ODLLevel.SEVERE, "Failed in generatePassword()", e);
           throw e;
       } 
       
       catch (UserLookupException e) 
       {
           logger.log(ODLLevel.SEVERE, "Failed in generatePassword()", e);
           throw e;
       }
   }
}
