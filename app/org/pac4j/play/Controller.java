/*
  Copyright 2012 - 2013 Jerome Leleu

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License.
 */
package org.pac4j.play;

import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.pac4j.core.client.BaseClient;
import org.pac4j.core.client.Clients;
import org.pac4j.core.context.HttpConstants;
import org.pac4j.core.credentials.Credentials;
import org.pac4j.core.exception.RequiresHttpAction;
import org.pac4j.core.exception.TechnicalException;
import org.pac4j.core.profile.CommonProfile;
import org.pac4j.play.java.JavaWebContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import play.cache.Cache;
import play.mvc.Result;

/**
 * This controller is the class to finish the authentication process and logout the user.
 * <p />
 * Public methods : {@link #callback()}, {@link #logoutAndOk()} and {@link #logoutAndRedirect()} must be used in the routes file.
 * 
 * @author Jerome Leleu
 * @since 1.0.0
 */
public class Controller extends play.mvc.Controller {
    
    private static final Logger logger = LoggerFactory.getLogger(Controller.class);
    
    /**
     * This method handles the callback call from the provider to finish the authentication process. The credentials and then the profile of
     * the authenticated user is retrieved and the originally request url (or the specific saved url) is restored.
     * 
     * @return the redirection to the saved request
     * @throws TechnicalException
     */
    @SuppressWarnings({
        "rawtypes", "unchecked"
    })
    public static Result callback() throws TechnicalException {
        // clients group from config
        final Clients clientsGroup = Config.getClients();
        
        // web context
        final JavaWebContext context = new JavaWebContext(request(), session());
        
        // get the client from its type
        final BaseClient client = (BaseClient) clientsGroup.findClient(context);
        logger.debug("client : {}", client);
        
        // get credentials
        Credentials credentials = null;
        try {
            credentials = client.getCredentials(context);
        } catch (final RequiresHttpAction e) {
            if (context.getResponseStatus() == HttpConstants.TEMP_REDIRECT) {
                final String redirectionUrl = context.getResponseHeaders().get(HttpConstants.LOCATION_HEADER);
                logger.debug("HTTP action 302 to : {}", redirectionUrl);
                return redirect(redirectionUrl);
            } else if (context.getResponseStatus() == HttpConstants.UNAUTHORIZED) {
                final String header = context.getResponseHeaders().get(HttpConstants.AUTHENTICATE_HEADER);
                logger.debug("HTTP action 401 : {}", header);
                response().getHeaders().put("WWW-Authenticate", header);
                return unauthorized();
            }
            logger.debug("ok");
            return ok();
        }
        logger.debug("credentials : {}", credentials);
        
        // get user profile
        final CommonProfile profile = (CommonProfile) client.getUserProfile(credentials);
        logger.debug("profile : {}", profile);
        
        // get current sessionId
        String sessionId = session(Constants.SESSION_ID);
        logger.debug("retrieved sessionId : {}", sessionId);
        // if null, generate a new one
        if (sessionId == null) {
            // generate id for session
            sessionId = java.util.UUID.randomUUID().toString();
            logger.debug("generated sessionId : {}", sessionId);
            // and save session
            session(Constants.SESSION_ID, sessionId);
        }
        // save userProfile in cache
        Cache.set(sessionId, profile, Config.getCacheTimeout());
        
        // retrieve saved request and redirect
        return redirect(getRedirectUrl(session(Constants.REQUESTED_URL), Config.getDefaultSuccessUrl()));
    }
    
    /**
     * This method logouts the authenticated user.
     */
    private static void logout() {
        // get the session id
        final String sessionId = session(Constants.SESSION_ID);
        logger.debug("sessionId for logout : {}", sessionId);
        if (StringUtils.isNotBlank(sessionId)) {
            // remove user profile from cache
            Cache.set(sessionId, null, 0);
            logger.debug("remove user profile and sessionId : {}", sessionId);
        }
        session().remove(Constants.SESSION_ID);
    }
    
    /**
     * This method logouts the authenticated user and send him to a blank page.
     * 
     * @return the redirection to the blank page
     */
    public static Result logoutAndOk() {
        logout();
        return ok();
    }
    
    /**
     * This method logouts the authenticated user and send him to the url defined in the
     * {@link Constants#REDIRECT_URL_LOGOUT_PARAMETER_NAME} parameter name or to the <code>defaultLogoutUrl</code>.
     * 
     * @return the redirection to the "logout url"
     */
    public static Result logoutAndRedirect() {
        logout();
        // parameters in url
        final Map<String, String[]> parameters = request().queryString();
        final String[] values = parameters.get(Constants.REDIRECT_URL_LOGOUT_PARAMETER_NAME);
        String value = null;
        if (values != null && values.length == 1) {
            value = values[0];
        }
        return redirect(getRedirectUrl(value, Config.getDefaultLogoutUrl()));
    }
    
    /**
     * This method returns the redirect url from a specified url with a default url.
     * 
     * @param url
     * @param defaultUrl
     * @return the redirect url
     */
    public static String getRedirectUrl(final String url, final String defaultUrl) {
        String redirectUrl = defaultUrl;
        if (StringUtils.isNotBlank(url)) {
            redirectUrl = url;
        }
        logger.debug("compute redirectUrl : {}", redirectUrl);
        return redirectUrl;
    }
}
