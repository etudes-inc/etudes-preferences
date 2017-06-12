tool_obj =
{
	title: "PREFERENCES",

	preferences: null,

	start: function(obj, data)
	{
		setTitle(obj.title);

		setupDialog("prefs_notification_dialog", "Done", function(){return obj.saveNotification(obj);});
		$("#prefs_notification_link").unbind("click").click(function(){obj.editNotification(obj);return false;});
		setupDialog("prefs_zone_dialog", "Done", function(){return obj.saveZone(obj);});
		$("#prefs_zone_link").unbind("click").click(function(){obj.editZone(obj);return false;});
		setupDialog("prefs_lang_dialog", "Done", function(){return obj.saveLang(obj);});
		$("#prefs_lang_link").unbind("click").click(function(){obj.editLang(obj);return false;});
		setupDialog("prefs_tabs_dialog", "Done", function(){return obj.saveTabs(obj);});
		$("#prefs_tabs_link").unbind("click").click(function(){obj.editTabs(obj);return false;});

		$("#prefs_tabs_removeAllTabs").unbind("click").click(function(){obj.removeAllTabs(obj);return false;});
		$("#prefs_tabs_addAllTabs").unbind("click").click(function(){obj.addAllTabs(obj);return false;});
		$("#prefs_tabs_removeSelectedTabs").unbind("click").click(function(){obj.removeSelectedTabs(obj);return false;});
		$("#prefs_tabs_addSelectedTabs").unbind("click").click(function(){obj.addSelectedTabs(obj);return false;});
		$("#prefs_tabs_moveTabsStart").unbind("click").click(function(){obj.moveTabsStart(obj);return false;});
		$("#prefs_tabs_moveTabsUp").unbind("click").click(function(){obj.moveTabsUp(obj);return false;});
		$("#prefs_tabs_moveTabsDown").unbind("click").click(function(){obj.moveTabsDown(obj);return false;});
		$("#prefs_tabs_moveTabsEnd").unbind("click").click(function(){obj.moveTabsEnd(obj);return false;});

		obj.loadPreferences(obj);
		
		startHeartbeat();
	},

	stop: function(obj, save)
	{
		stopHeartbeat();
	},

	loadPreferences: function(obj)
	{
		requestCdp("preferences_preferences", null, function(data)
		{
			obj.preferences = data.preferences;
			userSites.load(true, function()
			{
				obj.loadPreferencesView(obj);
			});
		});
	},

	loadPreferencesView: function(obj)
	{
		$("#prefs_noti_annc_setting").empty().html(obj.notiString(obj.preferences.noti_announcements));
		$("#prefs_noti_disc_setting").empty().html(obj.notiString(obj.preferences.noti_discussion));
		$("#prefs_noti_pm_setting").empty().html(obj.notiString(obj.preferences.noti_pm));
		$("#prefs_noti_rsrc_setting").empty().html(obj.notiString(obj.preferences.noti_resources));
		
		$("#prefs_zone_setting").empty().html(obj.preferences.timezone);
		$("#prefs_lang_setting").empty().html(obj.langString(obj.preferences.language));
		obj.populateTabsLists(obj);
		adjustForNewHeight();
	},

	notiString: function(setting)
	{
		if (setting == 0) return "Do Not Send";
		if (setting == 1) return "Send Daily Summary";
		if (setting == 2) return "Send Each Separately";
		return "?";
	},
	
	langString: function(setting)
	{
		if (setting == "ca_ES") return "Catalan (Spain)";
		if (setting == "zh_CN") return "Chinese (China)";
		if (setting == "nl_NL") return "Dutch (Netherlands)";
		if (setting == "en") return "English";
		if (setting == "en_US") return "English (United States)";
		if (setting == "fr_CA") return "French (Canada)";
		if (setting == "ja_JP") return "Japanese (Japan)";
		if (setting == "ko_KR") return "Korean (South Korea)";
		if (setting == "es_ES") return "Spanish (Spain)";
		return "?";
	},

	editNotification: function(obj)
	{
		$("#prefs_notification_annc_option_" + obj.preferences.noti_announcements).attr('checked', true);
		$("#prefs_notification_disc_option_" + obj.preferences.noti_discussion).attr('checked', true);
		$("#prefs_notification_pm_option_" + obj.preferences.noti_pm).attr('checked', true);
		$("#prefs_notification_rsrc_option_" + obj.preferences.noti_resources).attr('checked', true);

		$("#prefs_notification_dialog").dialog('open');
	},

	saveNotification: function(obj)
	{
		var data = new Object();
		data.noti_announcements = $('input[name=prefs_notification_annc_option]:checked').val();
		data.noti_discussion = $('input[name=prefs_notification_disc_option]:checked').val();
		data.noti_pm = $('input[name=prefs_notification_pm_option]:checked').val();
		data.noti_resources = $('input[name=prefs_notification_rsrc_option]:checked').val();
		requestCdp("preferences_setPreferences", data, function(data)
		{
			obj.loadPreferences(obj);
		});

		return true;
	},

	editZone: function(obj)
	{
		$("#prefs_zone_option").val(obj.preferences.timezone);

		$("#prefs_zone_dialog").dialog('open');
	},
	
	saveZone: function(obj)
	{
		var data = new Object();
		data.timezone = $("#prefs_zone_option").val();
		requestCdp("preferences_setPreferences", data, function(data)
		{
			obj.loadPreferences(obj);
		});

		return true;
	},

	editLang: function(obj)
	{
		$("#prefs_lang_option").val(obj.preferences.language);

		$("#prefs_lang_dialog").dialog('open');
	},
	
	saveLang: function(obj)
	{
		var data = new Object();
		data.language = $("#prefs_lang_option").val();
		requestCdp("preferences_setPreferences", data, function(data)
		{
			obj.loadPreferences(obj);
		});

		return true;
	},

	editTabs: function(obj)
	{
		obj.populateDialogTabsLists(obj);
		$("#prefs_tabs_dialog").dialog('open');
	},
	
	saveTabs: function(obj)
	{
		var data = new Object();
		data.order = "";
		$("#prefs_tabs_tabs option").each(function()
		{
			data.order += $(this).val() + "\t";
		});

		requestCdp("preferences_setFavoriteSiteOrder", data, function(data)
		{
			resetPortal();
			// obj.loadPreferences(obj);
		});

		return true;
	},

	populateDialogTabsLists: function(obj)
	{
		$("#prefs_tabs_tabs").empty();
		var inOrder = userSites.inOrder();
		if (inOrder != null)
		{
			$.each(inOrder, function(index, value)
			{
				if (value.visible == 1)
				{
					var option = $('<option />').attr('value', value.siteId).html(value.title);
					$("#prefs_tabs_tabs").append(option);
				}				
			});
		}

		$("#prefs_tabs_others").empty();
		var byTerm = userSites.byTerm();
		if (byTerm != null)
		{
			$.each(byTerm, function(index, value)
			{
				if (value.visible == 0)
				{
					var option = $('<option />').attr('value', value.siteId).html(value.title);
					$("#prefs_tabs_others").append(option);
				}				
			});
		}
	},

	populateTabsLists: function(obj)
	{
		$("#prefs_tab_sites").empty();
		var inOrder = userSites.inOrder();
		if (inOrder != null)
		{
			$.each(inOrder, function(index, value)
			{
				if (value.visible == 1)
				{
					$("#prefs_tab_sites").append(value.title);
					$("#prefs_tab_sites").append("<br />");
				}				
			});
		}

		$("#prefs_other_sites").empty();
		var byTerm = userSites.byTerm();
		if (byTerm != null)
		{
			$.each(byTerm, function(index, value)
			{
				if (value.visible == 0)
				{
					$("#prefs_other_sites").append(value.title);
					$("#prefs_other_sites").append("<br />");
				}				
			});
		}
	},
	
	removeAllTabs: function(obj)
	{
		$("#prefs_tabs_tabs option").appendTo("#prefs_tabs_others");
	},

	addAllTabs: function(obj)
	{
		$("#prefs_tabs_others option").appendTo("#prefs_tabs_tabs");
	},

	removeSelectedTabs: function(obj)
	{
		$("#prefs_tabs_tabs option:selected").appendTo("#prefs_tabs_others");
	},

	addSelectedTabs: function(obj)
	{
		$("#prefs_tabs_others option:selected").appendTo("#prefs_tabs_tabs");
	},

	moveTabsStart: function(obj)
	{
		$("#prefs_tabs_tabs option:selected").prependTo("#prefs_tabs_tabs");
	},

	moveTabsUp: function(obj)
	{
		$('#prefs_tabs_tabs option:selected').each(function()
		{
			$(this).insertBefore($(this).prev());
		});
	},

	moveTabsDown: function(obj)
	{
		$('#prefs_tabs_tabs option:selected').reverse().each(function()
		{
			$(this).insertAfter($(this).next());
		});
	},

	moveTabsEnd: function(obj)
	{
		$("#prefs_tabs_tabs option:selected").appendTo("#prefs_tabs_tabs");
	}
};

completeToolLoad();
