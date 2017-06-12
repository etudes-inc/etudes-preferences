/**********************************************************************************
 * $URL: https://source.etudes.org/svn/apps/preferences/trunk/preferences-webapp/webapp/src/java/org/etudes/preferences/cdp/PreferencesCdpHandler.java $
 * $Id: PreferencesCdpHandler.java 6580 2013-12-11 01:53:21Z ggolden $
 ***********************************************************************************
 *
 * Copyright (c) 2013 Etudes, Inc.
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 **********************************************************************************/

package org.etudes.preferences.cdp;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.etudes.api.app.jforum.JForumUserService;
import org.etudes.cdp.api.CdpHandler;
import org.etudes.cdp.api.CdpStatus;
import org.sakaiproject.authz.api.SecurityService;
import org.sakaiproject.component.api.ServerConfigurationService;
import org.sakaiproject.component.cover.ComponentManager;
import org.sakaiproject.entity.api.ResourceProperties;
import org.sakaiproject.entity.api.ResourcePropertiesEdit;
import org.sakaiproject.event.api.NotificationService;
import org.sakaiproject.exception.IdUnusedException;
import org.sakaiproject.exception.IdUsedException;
import org.sakaiproject.exception.InUseException;
import org.sakaiproject.exception.PermissionException;
import org.sakaiproject.site.api.Site;
import org.sakaiproject.site.api.SiteService;
import org.sakaiproject.time.api.TimeService;
import org.sakaiproject.user.api.Preferences;
import org.sakaiproject.user.api.PreferencesEdit;
import org.sakaiproject.user.api.PreferencesService;
import org.sakaiproject.user.api.UserDirectoryService;
import org.sakaiproject.util.StringUtil;

/**
 */
public class PreferencesCdpHandler implements CdpHandler
{
	/** Our log (commons). */
	private static Log M_log = LogFactory.getLog(PreferencesCdpHandler.class);

	public String getPrefix()
	{
		return "preferences";
	}

	public Map<String, Object> handle(HttpServletRequest req, HttpServletResponse res, Map<String, Object> parameters, String requestPath,
			String path, String authenticatedUserId) throws ServletException, IOException
	{
		// if no authenticated user, we reject all requests
		if (authenticatedUserId == null)
		{
			Map<String, Object> rv = new HashMap<String, Object>();
			rv.put(CdpStatus.CDP_STATUS, CdpStatus.notLoggedIn.getId());
			return rv;
		}

		else if (requestPath.equals("preferences"))
		{
			return dispatchPreferences(req, res, parameters, path, authenticatedUserId);
		}
		else if (requestPath.equals("setPreferences"))
		{
			return dispatchSetPreferences(req, res, parameters, path, authenticatedUserId);
		}
		else if (requestPath.equals("setFavoriteSiteOrder"))
		{
			return dispatchSetFavoriteSiteOrder(req, res, parameters, path, authenticatedUserId);
		}

		return null;
	}

	protected Map<String, Object> dispatchPreferences(HttpServletRequest req, HttpServletResponse res, Map<String, Object> parameters, String path,
			String userId) throws ServletException, IOException
	{
		Map<String, Object> rv = new HashMap<String, Object>();

		// build up a map to return - the main map has a single "preferences" object
		Map<String, String> preferencesMap = new HashMap<String, String>();
		rv.put("preferences", preferencesMap);

		preferencesMap.put("userId", userId);

		Preferences prefs = preferencesService().getPreferences(userId);
		org.etudes.api.app.jforum.User u = jForumUserService().getBySakaiUserId(userId);

		ResourceProperties props = prefs.getProperties(NotificationService.PREFS_TYPE + "sakai:announcement");
		String val = props.getProperty(Integer.toString(NotificationService.NOTI_OPTIONAL));
		if (val == null) val = "3";
		preferencesMap.put("noti_announcements", Integer.toString(Integer.parseInt(val) - 1));

		props = prefs.getProperties(NotificationService.PREFS_TYPE + "sakai:content");
		val = props.getProperty(Integer.toString(NotificationService.NOTI_OPTIONAL));
		if (val == null) val = "3";
		preferencesMap.put("noti_resources", Integer.toString(Integer.parseInt(val) - 1));

		val = null;
		if (u != null)
		{
			val = u.isNotifyOnMessagesEnabled() ? "3" : "1";
		}
		if (val == null) val = "1";
		preferencesMap.put("noti_discussion", Integer.toString(Integer.parseInt(val) - 1));

		val = null;
		if (u != null)
		{
			val = u.isNotifyPrivateMessagesEnabled() ? "3" : "1";
		}
		if (val == null) val = "1";
		preferencesMap.put("noti_pm", Integer.toString(Integer.parseInt(val) - 1));

		props = prefs.getProperties("sakai:time");
		val = props.getProperty("timezone");
		if (val == null) val = TimeZone.getDefault().getID();
		preferencesMap.put("timezone", val);

		props = prefs.getProperties("sakai:resourceloader");
		val = props.getProperty("locale");
		if (val == null) val = Locale.getDefault().toString();
		preferencesMap.put("language", val);

		// add status parameter
		rv.put(CdpStatus.CDP_STATUS, CdpStatus.success.getId());

		return rv;
	}

	@SuppressWarnings("unchecked")
	protected Map<String, Object> dispatchSetFavoriteSiteOrder(HttpServletRequest req, HttpServletResponse res, Map<String, Object> parameters,
			String path, String userId) throws ServletException, IOException
	{
		Map<String, Object> rv = new HashMap<String, Object>();

		// get the ordered favorite site ids
		String orderTsv = (String) parameters.get("order");
		if (orderTsv == null)
		{
			M_log.warn("dispatchSetFavoriteSiteOrder - no order parameter");

			// add status parameter
			rv.put(CdpStatus.CDP_STATUS, CdpStatus.badRequest.getId());
			return rv;
		}
		String[] order = StringUtil.split(orderTsv, "\t");

		// sites in order should be the order property, and not in exclude
		// all other user sites should be in exclude

		// collect the user's sites
		List<Site> mySites = siteService().getSites(org.sakaiproject.site.api.SiteService.SelectionType.MYSITES, null, null, null,
				org.sakaiproject.site.api.SiteService.SortType.TITLE_ASC, null);

		try
		{
			PreferencesEdit prefsEdit = null;
			try
			{
				prefsEdit = preferencesService().edit(userId);
			}
			catch (IdUnusedException e)
			{
				// add a new one if this is the first time preferences will be set for the user
				prefsEdit = preferencesService().add(userId);
			}

			ResourcePropertiesEdit propsEdit = prefsEdit.getPropertiesEdit("sakai:portal:sitenav");

			propsEdit.removeProperty("order");
			propsEdit.removeProperty("exclude");

			for (String siteId : order)
			{
				// ignore if not in the user's sites
				Site found = null;
				for (Site site : mySites)
				{
					if (site.getId().equals(siteId))
					{
						found = site;
						break;
					}
				}
				if (found != null)
				{
					propsEdit.addPropertyToList("order", siteId);

					// remove from the sites list
					mySites.remove(found);
				}
			}

			// the remaining sites in the sites list are not favorites
			for (Site site : mySites)
			{
				propsEdit.addPropertyToList("exclude", site.getId());
			}

			preferencesService().commit(prefsEdit);
		}
		catch (PermissionException e)
		{
			M_log.warn("dispatchSetFavoriteSiteOrder: " + e);

			// add status parameter
			rv.put(CdpStatus.CDP_STATUS, CdpStatus.accessDenied.getId());
			return rv;
		}
		catch (InUseException e)
		{
			M_log.warn("dispatchSetFavoriteSiteOrder: " + e);

			// add status parameter
			rv.put(CdpStatus.CDP_STATUS, CdpStatus.badRequest.getId());
			return rv;
		}
		catch (IdUsedException e)
		{
			M_log.warn("dispatchSetFavoriteSiteOrder: " + e);

			// add status parameter
			rv.put(CdpStatus.CDP_STATUS, CdpStatus.badRequest.getId());
			return rv;
		}

		// add status parameter
		rv.put(CdpStatus.CDP_STATUS, CdpStatus.success.getId());

		return rv;
	}
	protected Map<String, Object> dispatchSetPreferences(HttpServletRequest req, HttpServletResponse res, Map<String, Object> parameters,
			String path, String userId) throws ServletException, IOException
	{
		Map<String, Object> rv = new HashMap<String, Object>();

		// Note: fields are optional
		String notiAnnc = (String) parameters.get("noti_announcements");
		String notiDisc = (String) parameters.get("noti_discussion");
		String notiPm = (String) parameters.get("noti_pm");
		String notiRsrc = (String) parameters.get("noti_resources");
		String timezone = (String) parameters.get("timezone");
		String language = (String) parameters.get("language");

		PreferencesEdit edit = null;
		try
		{
			try
			{
				edit = preferencesService().edit(userId);
			}
			catch (IdUnusedException e)
			{
				// add a new one if this is the first time preferences will be set for the user
				edit = preferencesService().add(userId);
			}

			if (notiAnnc != null)
			{
				int val = Integer.parseInt(notiAnnc) + 1;
				ResourcePropertiesEdit props = edit.getPropertiesEdit(NotificationService.PREFS_TYPE + "sakai:announcement");
				props.addProperty(Integer.toString(NotificationService.NOTI_OPTIONAL), Integer.toString(val));
			}
			if (notiRsrc != null)
			{
				int val = Integer.parseInt(notiRsrc) + 1;
				ResourcePropertiesEdit props = edit.getPropertiesEdit(NotificationService.PREFS_TYPE + "sakai:content");
				props.addProperty(Integer.toString(NotificationService.NOTI_OPTIONAL), Integer.toString(val));
			}

			if (timezone != null)
			{
				ResourcePropertiesEdit props = edit.getPropertiesEdit("sakai:time");
				props.addProperty("timezone", timezone);
			}

			if (language != null)
			{
				ResourcePropertiesEdit props = edit.getPropertiesEdit("sakai:resourceloader");
				props.addProperty("locale", language);
			}

			preferencesService().commit(edit);
			
			// clear the user's cached timezone
			timeService().clearLocalTimeZone(userId);
		}
		catch (PermissionException e)
		{
			M_log.warn("dispatchSetPreferences: " + e);

			// add status parameter
			rv.put(CdpStatus.CDP_STATUS, CdpStatus.accessDenied.getId());
			return rv;
		}
		catch (InUseException e)
		{
			M_log.warn("dispatchSetPreferences: " + e);

			// add status parameter
			rv.put(CdpStatus.CDP_STATUS, CdpStatus.badRequest.getId());
			return rv;
		}
		catch (IdUsedException e)
		{
			M_log.warn("dispatchSetPreferences: " + e);

			// add status parameter
			rv.put(CdpStatus.CDP_STATUS, CdpStatus.badRequest.getId());
			return rv;
		}

		// the two jforum settings
		org.etudes.api.app.jforum.User u = jForumUserService().getBySakaiUserId(userId);

		if (notiDisc != null)
		{
			u.setNotifyOnMessagesEnabled(notiDisc.equals("2"));
		}

		if (notiPm != null)
		{
			u.setNotifyPrivateMessagesEnabled(notiPm.equals("2"));
		}

		jForumUserService().modifyUser(u);

		// add status parameter
		rv.put(CdpStatus.CDP_STATUS, CdpStatus.success.getId());

		return rv;
	}

	/**
	 * @return The JForumUserService, via the component manager.
	 */
	protected JForumUserService jForumUserService()
	{
		return (JForumUserService) ComponentManager.get(JForumUserService.class);
	}

	/**
	 * @return The AuthenticationManager, via the component manager.
	 */
	protected PreferencesService preferencesService()
	{
		return (PreferencesService) ComponentManager.get(PreferencesService.class);
	}

	/**
	 * @return The SecurityService, via the component manager.
	 */
	protected SecurityService securityService()
	{
		return (SecurityService) ComponentManager.get(SecurityService.class);
	}

	/**
	 * @return The ServerConfigurationService, via the component manager.
	 */
	protected ServerConfigurationService serverConfigurationService()
	{
		return (ServerConfigurationService) ComponentManager.get(ServerConfigurationService.class);
	}

	/**
	 * @return The SiteService, via the component manager.
	 */
	protected SiteService siteService()
	{
		return (SiteService) ComponentManager.get(SiteService.class);
	}

	/**
	 * @return The TimeService, via the component manager.
	 */
	protected TimeService timeService()
	{
		return (TimeService) ComponentManager.get(TimeService.class);
	}

	/**
	 * @return The UserDirectoryService, via the component manager.
	 */
	protected UserDirectoryService userDirectoryService()
	{
		return (UserDirectoryService) ComponentManager.get(UserDirectoryService.class);
	}
}
