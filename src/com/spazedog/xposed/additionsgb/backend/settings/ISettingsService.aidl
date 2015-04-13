/*
 * This file is part of the Xposed Additions Project: https://github.com/spazedog/xposed-additions
 *  
 * Copyright (c) 2015 Daniel Bergløv
 *
 * Xposed Additions is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.

 * Xposed Additions is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.

 * You should have received a copy of the GNU General Public License
 * along with Xposed Additions. If not, see <http://www.gnu.org/licenses/>
 */
 
package com.spazedog.xposed.additionsgb.backend.settings;

import com.spazedog.xposed.additionsgb.backend.settings.ISettingsListener;
 
/** {@hide} */
interface ISettingsService {

	int version();
	boolean isReady();
	
	void addSettingsListener(ISettingsListener listener);
	void removeSettingsListener(ISettingsListener listener);
	
	void sendSettingsBroadcast(String action, in Bundle data);
	
	
	/*
	 * Preferences
	 */
	 
	 boolean savePreferences();
	 
	 boolean hasPreference(String key);
	 void deletePreference(String key);
	 
	 boolean getBooleanPreference(String key, boolean defaultValue);
	 void putBooleanPreference(String key, boolean value, boolean preserve);
	 int getIntegerPreference(String key, int defaultValue);
	 void putIntegerPreference(String key, int value, boolean preserve);
	 String getStringPreference(String key, String defaultValue);
	 void putStringPreference(String key, String value, boolean preserve);
	 List<String> getStringArrayPreference(String key, in List<String> defaultValue);
	 void putStringArrayPreference(String key, in List<String> value, boolean preserve);
}
