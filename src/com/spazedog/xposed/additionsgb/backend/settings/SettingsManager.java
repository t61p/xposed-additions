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

import java.lang.ref.WeakReference;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import android.os.Bundle;
import android.os.RemoteException;

import com.spazedog.lib.reflecttools.ReflectClass;
import com.spazedog.lib.reflecttools.utils.ReflectException;
import com.spazedog.xposed.additionsgb.utils.Constants;
import com.spazedog.xposed.additionsgb.utils.SettingsHelper.Type;
import com.spazedog.xposed.additionsgb.utils.Utils;
import com.spazedog.xposed.additionsgb.utils.Utils.Level;


public final class SettingsManager {
	public static final String TAG = SettingsManager.class.getName();
	
	private static WeakReference<SettingsManager> oInstance = new WeakReference<SettingsManager>(null);
	
	private volatile ISettingsService mService;
	private volatile boolean mIsActive = false;
	private volatile boolean mIsReady = false;
	private final Map<String, Object> mData = new HashMap<String, Object>();
	private Set<SettingsBroadcastListener> mListeners = new HashSet<SettingsBroadcastListener>();
	
	public static interface SettingsBroadcastListener {
		public void onSettingsBroadcast(String action, Bundle data);
	}
	
	private ISettingsListener mListener = new ISettingsListener.Stub() {
		@Override
		public void onSettingsBroadcast(String action, Bundle data) throws RemoteException {
			synchronized (mListeners) {
				Utils.log(Level.DEBUG, TAG, "Invoking all Settings Broadcast listeners with action '" + action + "'");
				
				for (SettingsBroadcastListener listener : mListeners) {
					if (listener != null) {
						listener.onSettingsBroadcast(action, data);
					}
				}
			}
		}
		
		@Override
		public void onPreferenceRemoved(String key) throws RemoteException {
			synchronized (mData) {
				Utils.log(Level.DEBUG, TAG, "Removing data value with key '" + key + "'");
				
				mData.remove(key);
			}
		}
		
		@Override
		public void onPreferenceChanged(String key, int type) throws RemoteException {
			synchronized (mData) {
				/*
				 * If this key is not used by this instance then there is no need to store it here
				 */
				if (mData.containsKey(key)) {
					Utils.log(Level.DEBUG, TAG, "Updating data value with key '" + key + "'");
					
					try {
						switch(type) {
							case Type.STRING: mData.put(key, mService.getStringPreference(key, null)); break;
							case Type.INTEGER: mData.put(key, mService.getIntegerPreference(key, -1)); break;
							case Type.BOOLEAN: mData.put(key, mService.getBooleanPreference(key, false)); break;
							case Type.LIST: mData.put(key, mService.getStringArrayPreference(key, null));
						}
						
					} catch (RemoteException e) { 
						handleRemoteException(e); 
						
					} catch (NullPointerException e) {}
				}
			}
		}
	};
	
	/*
	 * This should not be used. Use getInstance()
	 */
	private SettingsManager() throws Exception {
		try {
			Utils.log(Level.DEBUG, TAG, "Creating a new SettingsManager instance");
			
			ReflectClass service = ReflectClass.forClass(ISettingsService.class).bindInterface(Constants.SERVICE_MODULE_SETTINGS);
			
			if (service != null) {
				mService = (ISettingsService) service.getReceiver();
				
				if (mService != null) {
					mService.addSettingsListener(mListener);
					
				} else {
					throw new Exception("Could not bind to Settings Service");
				}
				
			} else {
				throw new Exception("Could not bind to Settings Service");
			}
			
		} catch (ReflectException e) {
			throw new Exception(e);
		}
	}
	
	public static SettingsManager getInstance() {
		synchronized(oInstance) {
			SettingsManager instance = oInstance.get();
			
			if (instance == null) {
				try {
					oInstance = new WeakReference<SettingsManager>( (instance = new SettingsManager()) );
					
				} catch (Exception e) {
					Utils.log(Level.ERROR, TAG, e.getMessage(), e);
				}
			}
			
			return instance;
		}
	}
	
	private void handleRemoteException(RemoteException e) {
		synchronized(oInstance) {
			Utils.log(Level.DEBUG, TAG, "Service connection died. Establishing a new connection");
			
			try {
				try {
					ReflectClass service = ReflectClass.forClass(ISettingsService.class).bindInterface(Constants.SERVICE_MODULE_SETTINGS);
					
					if (service != null) {
						mService = (ISettingsService) service.getReceiver();
						
					} else {
						Utils.log(Level.ERROR, TAG, e.getMessage(), e);
					}
					
				} catch (ReflectException ei) {
					Utils.log(Level.ERROR, TAG, e.getMessage(), e);
				}
				
			} catch (Exception ei) {
				Utils.log(Level.ERROR, TAG, e.getMessage(), e);
			}
		}
	}
	
	@SuppressWarnings("unchecked")
	private Object getPreferenceValue(String key, Object defaultValue, int type) {
		if (isServiceReady()) {
			if (!mData.containsKey(key)) {
				Utils.log(Level.DEBUG, TAG, "Making IPC call to collect data value with key '" + key + "'");
				
				try {
					switch (type) {
						case Type.STRING: 
							mData.put(key, mService.getStringPreference(key, (String) defaultValue)); break;
							
						case Type.LIST:
							mData.put(key, mService.getStringArrayPreference(key, (List<String>) defaultValue)); break;
			
						case Type.BOOLEAN:
							mData.put(key, mService.getBooleanPreference(key, (Boolean) defaultValue)); break;
			
						case Type.INTEGER:
							mData.put(key, mService.getIntegerPreference(key, (Integer) defaultValue));
					}
					
				} catch (RemoteException e) {
					handleRemoteException(e);
					
				} catch (NullPointerException e) {}
			}
			
			Object data = mData.get(key);
			
			switch (type) {
				case Type.STRING: 
					return data == null || data instanceof String ? data : defaultValue;
					
				case Type.LIST:
					return data == null || data instanceof List ? data : defaultValue;
	
				case Type.BOOLEAN:
					return data != null && data instanceof Boolean ? data : defaultValue;
	
				case Type.INTEGER:
					return data != null && data instanceof Integer ? data : defaultValue;
			}
		}
		
		return defaultValue;
	}
	
	@SuppressWarnings("unchecked")
	private void setPreferenceValue(String key, Object value, int type, boolean preserve) {
		Utils.log(Level.DEBUG, TAG, "Making IPC call to update data value with key '" + key + "'");
		
		try {
			switch (type) {
				case Type.STRING: 
					mService.putStringPreference(key, (String) value, preserve); break;
					
				case Type.LIST:
					mService.putStringArrayPreference(key, (List<String>) value, preserve); break;
	
				case Type.BOOLEAN:
					mService.putBooleanPreference(key, (Boolean) value, preserve); break;
	
				case Type.INTEGER:
					mService.putIntegerPreference(key, (Integer) value, preserve);
			}
			
		} catch (RemoteException e) {
			handleRemoteException(e);
			
		} catch (NullPointerException e) {}
	}
	
	public void addSettingsBroadcastListener(SettingsBroadcastListener listener) {
		synchronized (mListeners) {
			if (!mListeners.contains(listener)) {
				mListeners.add(listener);
			}
		}
	}
	
	public void removeSettingsBroadcastListener(SettingsBroadcastListener listener) {
		synchronized (mListeners) {
			if (mListeners.contains(listener)) {
				mListeners.remove(listener);
			}
		}
	}
	
	public boolean isServiceActive() {
		if (!mIsActive) {
			try {
				if (mService.isActive()) {
					mIsActive = true;
				}
			
			} catch (RemoteException e) {
				handleRemoteException(e);
				
			} catch (NullPointerException e) {}
		}
		
		return mIsReady;
	}
	
	public boolean isServiceReady() {
		if (!mIsReady) {
			try {
				if (mService.isReady()) {
					mIsReady = true;
				}
			
			} catch (RemoteException e) {
				handleRemoteException(e);
				
			} catch (NullPointerException e) {}
		}
		
		return mIsReady;
	}
	
	public int getServiceVersion() {
		if (isServiceReady()) {
			try {
				return mService.version();
			
			} catch (RemoteException e) {
				handleRemoteException(e);
				
			} catch (NullPointerException e) {}
		}
		
		return 0;
	}
	
	public boolean isDebugEnabled() {
		return (Boolean) getPreferenceValue("global.enable_debug", Constants.FORCE_DEBUG, Type.BOOLEAN);
	}
	
	public void setDebugEnabled(boolean enabled) {
		setPreferenceValue("global.enable_debug", enabled, Type.BOOLEAN, true);
	}
	
	public List<String> getLogEntries() {
		try {
			return mService.getLogEntries();
		
		} catch (RemoteException e) {
			handleRemoteException(e);
			
		} catch (NullPointerException e) {}
		
		return null;
	}
}
