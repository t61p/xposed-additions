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
 
package com.spazedog.xposed.additionsgb;

import java.util.Map;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.os.Binder;
import android.os.IBinder;
import android.os.RemoteException;

import com.spazedog.xposed.additionsgb.utils.Constants;
import com.spazedog.xposed.additionsgb.utils.SettingsHelper.SettingsData;
import com.spazedog.xposed.additionsgb.utils.Utils;
import com.spazedog.xposed.additionsgb.utils.Utils.Level;

public class ServicePreferences extends Service {
	public static final String TAG = ServicePreferences.class.getName();
	
	/*
	 * SELinux in Lollipop+ has restricted the access to applications shared preference files. 
	 * So much that the system process itself no longer has access, regardless of the file permissions. 
	 * To get around this we use this service that the module service can connect to when it needs 
	 * to read/write preferences. 
	 * 
	 * This service is only meant to be used by the SettingsService and it will reject 
	 * any other caller. 
	 */
	
	private IBinder mBinder = new IServicePreferences.Stub() {
		@Override
		public void writeSettingsData(SettingsData data) throws RemoteException {
			if (Binder.getCallingUid() == 1000) {
				Utils.log(Level.INFO, TAG, "Writing preferences to shared preference file");
				
				if (data.changed()) {
					SharedPreferences preferences = getSharedPreferences(Constants.FILE_APP_PREFERENCES, Context.MODE_PRIVATE);
					Editor editor = preferences.edit();
					Map<String, ?> packedData = data.getPreferenceMap();
					
					editor.clear();
					
					for (String key : packedData.keySet()) {
						Object value = packedData.get(key);
						
						if (value != null && value instanceof String) {
							editor.putString(key, (String) value);
						}
					}
					
					editor.commit();
				}
				
			} else {
				Utils.log(Level.INFO, TAG, "Invalid caller '" + Binder.getCallingUid() + "' tried to access preferences from outside the SettingsService");
			}
		}

		@Override
		public SettingsData readSettingsData() throws RemoteException {
			if (Binder.getCallingUid() == 1000) {
				Utils.log(Level.INFO, TAG, "Reading preferences from shared preference file");
				
				SharedPreferences preferences = getSharedPreferences(Constants.FILE_APP_PREFERENCES, Context.MODE_PRIVATE);
				Map<String, ?> packedData = null;
				
				try {
					packedData = preferences.getAll();
					
				} catch (NullPointerException e) {}
				
				return packedData != null ? new SettingsData(packedData) : new SettingsData();
				
			} else {
				Utils.log(Level.INFO, TAG, "Invalid caller '" + Binder.getCallingUid() + "' tried to access preferences from outside the SettingsService");
			}
			
			return null;
		}
	};

	@Override
	public IBinder onBind(Intent intent) {
		return mBinder;
	}
}
