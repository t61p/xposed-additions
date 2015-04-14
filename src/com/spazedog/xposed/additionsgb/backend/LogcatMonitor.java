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

package com.spazedog.xposed.additionsgb.backend;

import java.util.HashMap;
import java.util.Map;

import android.annotation.SuppressLint;
import android.os.RemoteException;

import com.spazedog.lib.reflecttools.ReflectClass;
import com.spazedog.lib.reflecttools.utils.ReflectException;
import com.spazedog.xposed.additionsgb.backend.settings.ISettingsService;
import com.spazedog.xposed.additionsgb.backend.settings.SettingsService;
import com.spazedog.xposed.additionsgb.utils.Constants;
import com.spazedog.xposed.additionsgb.utils.Utils;
import com.spazedog.xposed.additionsgb.utils.Utils.Level;

import de.robv.android.xposed.XC_MethodHook;

@SuppressLint("UseSparseArrays")
public class LogcatMonitor {
	public static final String TAG = LogcatMonitor.class.getName();
	
	public static boolean oIsReady = false;

	private final static int DEBUG = 3;
	private final static int INFO = 4;
	private final static int ERROR = 6;
	
	private final static Map<Integer, String> LEVELS = new HashMap<Integer, String>();
	static {
		LEVELS.put(DEBUG, "D");
		LEVELS.put(INFO, "I");
		LEVELS.put(ERROR, "E");
	}
	
	public final Object mLock = new Object();
	public volatile boolean mBusy = false;
	private volatile ISettingsService mService;
	
	public static void init() {
		Utils.log(Level.INFO, TAG, "Instantiating Logcat Monitor");
		
		try {
			ReflectClass.forName("android.util.Log")
				.inject("println_native", new LogcatMonitor().hook_println_native);
			
		} catch (ReflectException e) {
			Utils.log(Level.ERROR, TAG, e.getMessage(), e);
		}
	}
	
	public static void onLoadApplication() {
		oIsReady = true;
	}
	
	/**
	 * The restrictions in Lollipop+ makes it almost impossible to write logs to a file. 
	 * Since we need logs as early as possible, using bound services is not an option. 
	 * Also adding entries to the module service is much better than files. 
	 * It is faster and it makes truncating much more optimized. 
	 */
	protected XC_MethodHook hook_println_native = new XC_MethodHook() {
		@Override
		protected final void beforeHookedMethod(final MethodHookParam param) {
			synchronized (mLock) {
				String message = (String) param.args[3];
				String tag = (String) param.args[2];
				int priority = (Integer) param.args[1];
				boolean tagHasName = tag != null && tag.contains(Constants.PACKAGE_NAME);
				
				if (tagHasName || (priority == ERROR && message.contains(Constants.PACKAGE_NAME))) {
					if (!mBusy) {
						mBusy = true;
						
						String entry = "";
						entry += LEVELS.get(priority);
						entry += "/";
						entry += tag;
						entry += "\r\n\t";
						entry += message.replace("\n", "\r\n\t\t");
						entry += "\r\n";
						
						ISettingsService service = getService();
						
						if (service != null) {
							try {
								service.addLogEntry(entry);
								
							} catch (RemoteException e) {
								service = null;
							}
							
						} else {
							SettingsService.addLogEntryStatic(entry);
						}
						
						mBusy = false;
					}
				}
			}
		}
	};
	
	/**
	 * We do not use the SettingsManager for two reasons
	 * 
	 * 		1: The SettingsManager writes logs itself, we don't want that.
	 * 		2: The ServiceManager creates listeners used to keep track of preferences and broadcasts none of which we need here. 
	 */
	private ISettingsService getService() {
		if (oIsReady) {
			if (mService == null) {
				try {
					ReflectClass service = ReflectClass.forClass(ISettingsService.class).bindInterface(Constants.SERVICE_MODULE_SETTINGS);
					
					if (service != null) {
						mService = (ISettingsService) service.getReceiver();
					}
					
				} catch (ReflectException e) {}	
			}
			
			try {
				if (mService != null && !mService.isActive()) {
					return null;
				}
				
			} catch (RemoteException e) {
				mService = null;
			}
		}
		
		return mService;
	}
}
