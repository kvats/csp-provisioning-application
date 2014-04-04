package net.respectnetwork.csp.application.controller;

import java.io.UnsupportedEncodingException;
import java.math.BigDecimal;
import java.net.URLDecoder;
import java.util.Map;
import java.util.UUID;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.validation.Valid;

import net.respectnetwork.csp.application.dao.DAOException;
import net.respectnetwork.csp.application.dao.DAOFactory;
import net.respectnetwork.csp.application.exception.UserRegistrationException;
import net.respectnetwork.csp.application.form.SignUpForm;
import net.respectnetwork.csp.application.form.UserDetailsForm;
import net.respectnetwork.csp.application.form.ValidateForm;
import net.respectnetwork.csp.application.manager.RegistrationManager;
import net.respectnetwork.csp.application.manager.StripePaymentProcessor;
import net.respectnetwork.csp.application.model.CSPModel;
import net.respectnetwork.csp.application.model.InviteModel;
import net.respectnetwork.csp.application.session.RegistrationSession;
import net.respectnetwork.sdk.csp.validation.CSPValidationException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Required;
import org.springframework.stereotype.Controller;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.servlet.ModelAndView;

import xdi2.core.xri3.CloudNumber;

/**
 * Handles requests for the application home page.
 */
@Controller
public class RegistrationController
{

   /** Class Logger */
   private static final Logger logger = LoggerFactory
                                            .getLogger(RegistrationController.class);

   /** Registration Manager */
   private RegistrationManager theManager;

   /** Registration Session */
   private RegistrationSession regSession;

   private String              cspCloudName;
   
   public static final String URL_PARAM_NAME_REQ_CLOUDNAME     = "name"   ;

   public String getCspCloudName()
   {
      return this.cspCloudName;
   }

   @Autowired
   @Qualifier("cspCloudName")
   public void setCspCloudName(String cspCloudName)
   {
      this.cspCloudName = cspCloudName;
   }

   /**
    * 
    * @return
    */
   public RegistrationManager getTheManager()
   {
      return theManager;
   }

   /**
    * 
    * @param theManager
    */
   @Autowired
   @Qualifier("active")
   @Required
   public void setTheManager(RegistrationManager theManager)
   {
      this.theManager = theManager;
   }

   /**
    * @return the regSession
    */
   public RegistrationSession getRegSession()
   {
      return regSession;
   }

   /**
    * @param regSession
    *           the regSession to set
    */
   @Autowired
   public void setRegSession(RegistrationSession regSession)
   {
      this.regSession = regSession;
   }

   /**
    * Initial Sign-Up Page
    */
   @RequestMapping(value = "/signup", method = RequestMethod.POST)
   public ModelAndView signup(
         @Valid @ModelAttribute("signUpInfo") SignUpForm signUpForm,
         HttpServletRequest request, HttpServletResponse response,
         BindingResult result)
   {

      logger.debug("Starting the Sign Up Process");

      ModelAndView mv = null;
      boolean errors = false;

      String cloudName = signUpForm.getCloudName();
      String inviteCode = signUpForm.getInviteCode();
      String giftCode = signUpForm.getGiftCode();

      logger.debug("Invite Code = " + inviteCode);
      logger.debug("Gift Code = " + giftCode);
      logger.debug("Cloud Name : " + cloudName);

      mv = new ModelAndView("signup");

      if (cloudName != null)
      {
         // Start Check that the Cloud Number is Available.
         try
         {
            if (theManager.isRequireInviteCode()
                  && ((inviteCode == null) || (inviteCode.trim().isEmpty())))
            {
               errors = true;
               logger.debug("Invite code is required and it has not been passed in the input query parameters(inviteCode=)");
            } else if (!theManager.isCloudNameAvailable(cloudName))
            {
               String errorStr = "CloudName not Available";
               mv.addObject("cloudNameError", errorStr);
               errors = true;
            }
         } catch (UserRegistrationException e)
         {
            String errorStr = "System Error checking CloudName";
            logger.warn(errorStr + " : {}", e.getMessage());
            mv.addObject("error", errorStr);
            errors = true;
         }
         UserDetailsForm userDetailsForm = new UserDetailsForm();
         userDetailsForm.setCloudName(cloudName);
         
         /*
         InviteModel invite = null;
         // add the email address of the person who was invited in the
         // userDetailsForm object, the user shouldn't be able to change it
         DAOFactory dao = DAOFactory.getInstance();
         UserDetailsForm userDetailsForm = new UserDetailsForm();
         try
         {
            invite = dao.getInviteDAO().get(inviteCode);
            if (invite != null
                  && (invite.getInvitedEmailAddress() != null && !invite
                        .getInvitedEmailAddress().trim().isEmpty()))
            {
               userDetailsForm.setEmail(invite.getInvitedEmailAddress());
            } else
            {
               logger.error("This invite object does not have an email address or another valid identifier associated to it. Sending user to the signup page.");
               errors = true;
            }
         } catch (DAOException e)
         {
            logger.error("Could not get invite information from DB. Sending user to the signup page.");
            errors = true;
         }
         */
         if (!errors)
         {
            mv = new ModelAndView("userdetails");

            mv.addObject("userInfo", userDetailsForm);

            // Add CloudName to Session

            String sessionId = UUID.randomUUID().toString();
            regSession.setSessionId(sessionId);
            regSession.setCloudName(signUpForm.getCloudName());
            //regSession.setInviteCode(inviteCode);
            //regSession.setGiftCode(giftCode);
            //regSession.setVerifiedEmail(invite.getInvitedEmailAddress());

         } else
         {
            mv = new ModelAndView("signup");
         }

      }
      mv.addObject("signupInfo", signUpForm);

      return mv;
   }

   /**
    * Get User Details
    */
   @RequestMapping(value = "/processuserdetails", method = RequestMethod.POST)
   public ModelAndView getDetails(
         @Valid @ModelAttribute("userInfo") UserDetailsForm userDetailsForm,
         HttpServletRequest request, HttpServletResponse response,
         BindingResult result)
   {

      logger.debug("Get User Details");

      ModelAndView mv = null;
      boolean errors = false;
      mv = new ModelAndView("userdetails");
      mv.addObject("userInfo", userDetailsForm);

      String cn = regSession.getCloudName();
      String sessionId = regSession.getSessionId();

      // Session Check
      if (sessionId == null || cn == null)
      {
         errors = true;
         mv.addObject("error", "Invalid Session");
      }

      if (!errors)
      {
         try
         {
            CloudNumber[] existingUsers = theManager
                  .checkEmailAndMobilePhoneUniqueness(
                        userDetailsForm.getMobilePhone(),
                        userDetailsForm.getEmail());
            if (existingUsers[0] != null)
            {
               // Communicate back to Form phone is already taken
               String errorStr = "Phone Number not Unique";
               mv.addObject("phoneError", errorStr);
               logger.debug("Phone {} already used by {}",
                     userDetailsForm.getMobilePhone(), existingUsers[0]);
               errors = true;
            }
            if (existingUsers[1] != null)
            {
               String errorStr = "Email not Unique";
               mv.addObject("emailError", errorStr);
               logger.debug("Phone {} already used by {}",
                     userDetailsForm.getEmail(), existingUsers[1]);
               errors = true;
            }
         } catch (UserRegistrationException e)
         {
            String errorStr = "System Error checking Email/Phone Number Uniqueness";
            logger.warn(errorStr + " : {}", e.getMessage());
            mv.addObject("error", errorStr);
            errors = true;
         }
      }

      if (!errors)
      {

         // If all is okay send out the validation messages.
         try
         {
            theManager.sendValidationCodes(sessionId,
                  userDetailsForm.getEmail(), userDetailsForm.getMobilePhone());
         } catch (CSPValidationException e)
         {
            String errorStr = "System Error sending validation messages";
            logger.warn(errorStr + " : {}", e.getMessage());
            mv.addObject("error", errorStr);
            errors = true;
         }
      }

      if (!errors)
      {
         mv = new ModelAndView("validate");
         ValidateForm validateForm = new ValidateForm();
         mv.addObject("validateInfo", validateForm);

         // Add CloudName/ Email / Password and Phone to Session

         logger.debug("Setting verified email " + regSession.getVerifiedEmail());
         regSession.setVerifiedEmail(userDetailsForm.getEmail());
         regSession.setVerifiedMobilePhone(userDetailsForm.getMobilePhone());
         regSession.setPassword(userDetailsForm.getPassword());
      }

      mv.addObject("userInfo", userDetailsForm);

      return mv;
   }

   /**
    * Validate Confirmation Codes,
    * 
    * 
    * @param userForm
    *           Form with User's details
    * @param result
    *           Binding Result for Validation or errors
    * @return ModelandView of next travel location
    */
   @RequestMapping(value = "/validatecodes", method = RequestMethod.POST)
   public ModelAndView validateCodes(
         @Valid @ModelAttribute("validateInfo") ValidateForm validateForm,
         HttpServletRequest request, BindingResult result)
   {

      logger.debug("Starting Validation Process");
      logger.debug("Processing Validation Data: {}", validateForm.toString());

      ModelAndView mv = new ModelAndView("validate");
      String sessionIdentifier = regSession.getSessionId();

      boolean errors = false;

      // Validate Codes
      if (!theManager.validateCodes(sessionIdentifier,
            validateForm.getEmailCode(), validateForm.getSmsCode()))
      {
         String errorStr = "Code(s) Validation Failed";
         logger.debug(errorStr);
         mv.addObject("codeValidationError", errorStr);
         errors = true;
      }

      if (!errors)
      {

         mv = new ModelAndView("payment");

      }

      return mv;

   }

   /**
    * This is the endpoint where the user lands in the CSP website from an
    * invite
    * 
    * @param request
    *           : some query parameters that come from RN which are to be echoed
    *           back and a query parameter called "name" which has the cloudname
    * @param response
    * @return
    */
   @RequestMapping(value = "/register", method = RequestMethod.GET)
   public ModelAndView registerCloudName(HttpServletRequest request,
         HttpServletResponse response,
         @Valid @ModelAttribute("signUpInfo") SignUpForm signUpForm,
         BindingResult result)
   {
      
      String remoteIPAddr = request.getHeader("X-FORWARDED-FOR");
      
      logger.debug("User agent " + request.getHeader("User-Agent"));

      if(remoteIPAddr == null)
      {
         remoteIPAddr = request.getRemoteAddr();
         
      }
      logger.debug("Client IP " + remoteIPAddr);
      logger.debug("Referer URL " + request.getHeader("referer"));
      //TOTO : check for referer URL and if it does not match with the configured one in the 
      //properties, then bail out
      String cloudName = null;
      if (signUpForm != null && signUpForm.getCloudName() != null)
      {
         cloudName = signUpForm.getCloudName();
         try
         {
            cloudName = URLDecoder.decode(cloudName,"UTF-8");
         } catch (UnsupportedEncodingException e)
         {
            // TODO Auto-generated catch block
            e.printStackTrace();
         }
      } else
      {
         cloudName = request.getParameter(URL_PARAM_NAME_REQ_CLOUDNAME);
      }
      @SuppressWarnings("unchecked")
      Map<String,String[]> rNParamMap = (Map<String,String[]>)request.getParameterMap();
      
      logger.info("registerCloudName : registration request for cloudname " + cloudName);
      ModelAndView mv = new ModelAndView("signup");
      if (cloudName != null)
      {
         try
         {
            if (theManager.isCloudNameAvailable(cloudName))
            {
               logger.info(cloudName + " is available, so going to show the validation screen");
               mv = new ModelAndView("userdetails");
               UserDetailsForm userDetailsForm = new UserDetailsForm();
               userDetailsForm.setCloudName(cloudName);
               mv.addObject("userInfo", userDetailsForm);
               // Add CloudName to Session
               String sessionId = UUID.randomUUID().toString();
               regSession.setSessionId(sessionId);
               regSession.setCloudName(cloudName);
               regSession.setRNParamMap(rNParamMap);
            }
         } catch (UserRegistrationException e)
         {

            logger.info("Exception in registerCloudName " + e.getMessage());
            // e.printStackTrace();
         }

      }
      mv.addObject("signupInfo", signUpForm);
      return mv;
   }

}
