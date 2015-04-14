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

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.res.Resources;
import android.os.Binder;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.os.RemoteException;

import com.spazedog.lib.reflecttools.ReflectClass;
import com.spazedog.lib.reflecttools.apache.Common;
import com.spazedog.lib.reflecttools.utils.ReflectConstants.Match;
import com.spazedog.lib.reflecttools.utils.ReflectException;
import com.spazedog.xposed.additionsgb.IServicePreferences;
import com.spazedog.xposed.additionsgb.utils.Constants;
import com.spazedog.xposed.additionsgb.utils.SettingsHelper.SettingsData;
import com.spazedog.xposed.additionsgb.utils.SettingsHelper.Type;
import com.spazedog.xposed.additionsgb.utils.Utils;
import com.spazedog.xposed.additionsgb.utils.Utils.Level;

import de.robv.android.xposed.XC_MethodHook;

/** {@hide} */
public final class SettingsService extends ISettingsService.Stub {
	public static final String TAG = SettingsService.class.getName();
	
	/*
	 * Used when communicating with the app preference service
	 */
	private enum PokeType {SAVE_SETTINGS, RESTORE_SETTINGS}
	
	private int mSystemUid = 1000;
	private int mAppUid = 0;
	private int mVersion = 0;
	private boolean mIsReady = false;
	private boolean mIsActive = false;
	private SettingsData mData = new SettingsData();
	private Context mContext;
	private Set<BinderWatcher> mListeners = new HashSet<BinderWatcher>();
	
	private List<String> mLogEntries = new ArrayList<String>();
	private static List<String> oLogEntriesStatic = new ArrayList<String>();
	
	private final class BinderWatcher implements IBinder.DeathRecipient {
		private IBinder mBinder;
		
		public BinderWatcher(IBinder binder) throws RemoteException {
			mBinder = binder;
			mBinder.linkToDeath(this, 0);
		}

		@Override
		public void binderDied() {
			synchronized (mListeners) {
				mListeners.remove(this);
				
				if (mBinder != null) {
					mBinder.unlinkToDeath(this, 0);
					mBinder = null;
				}
			}
		}
		
		public IBinder getBinder() {
			return mBinder;
		}
	}
	
	private SettingsService() {}
	
	/**
	 * Instantiates this class. 
	 * This should be called from an XposedBridge IXposedHookZygoteInit hook
	 */
	public static void init() {
		Utils.log(Level.INFO, TAG, "Instantiating Settings Service");
		
		if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
			init_hooks(ClassLoader.getSystemClassLoader());
			
		} else {
			Utils.log(Level.INFO, TAG, "API Lollipop or higher detected. Redirecting through ActivityThread to obtain valid ClassLoader");
			
			/*
			 * On Lollipop we cannot access anything in com.android.server with a regular boot ClassLoader.
			 * We need to get another loader for this a little deeper in the system.
			 */
			
			try {
				ReflectClass.forName("android.app.ActivityThread")
					.inject("systemMain", new XC_MethodHook() {
						@Override
						protected final void afterHookedMethod(final MethodHookParam param) {
							init_hooks(Thread.currentThread().getContextClassLoader());
						}
					});
				
			} catch (ReflectException e) {
				Utils.log(Level.ERROR, TAG, e.getMessage(), e);
			}
		}
	}
	
	/**
	 * Add all the required hooks to make this service work.
	 * This needs a class loader that has access to internal service classes. 
	 * 
	 * In API 21+ this is no longer the case with the boot class loader.
	 * 
	 * @param loader
	 * 		- Proper ClassLoader
	 */
	private static void init_hooks(ClassLoader loader) {
		Utils.log(Level.INFO, TAG, "Attaching service hook to ActivityManagerService");
		
		/*
		 * Force ReflectTools to use this class loader
		 */
		ReflectClass.setClassLoader(loader);
		
		SettingsService serviceInstance = new SettingsService();
		
		try {
			ReflectClass activityManager = ReflectClass.forName("com.android.server.am.ActivityManagerService");
			
			activityManager.inject("systemReady", serviceInstance.hook_onReady);
			activityManager.inject("shutdown", serviceInstance.hook_onShutdown);
			
			/*
			 * This does not exists in newer API's
			 */
			if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
				activityManager.inject("main", serviceInstance.hook_onStartup);
				
			} else {
				/*
				 * We use this as startup in Lollipop and above
				 */
				try {
					ReflectClass.forName("com.android.server.SystemServer").inject("startBootstrapServices", serviceInstance.hook_onStartup);
					
				} catch (ReflectException ei) {
					Utils.log(Level.ERROR, TAG, ei.getMessage(), ei);
					
					/*
					 * If we can't hook main, we can't use the service. 
					 */
					activityManager.removeInjections();
				}
			}
			
		} catch (ReflectException e) {
			Utils.log(Level.ERROR, TAG, e.getMessage(), e);
		}
	}
	
	/**
	 * Hook that is called when the ServiceManager is ready
	 */
	private XC_MethodHook hook_onStartup = new XC_MethodHook() {
		@Override
		protected final void afterHookedMethod(final MethodHookParam param) {
			Utils.log(Level.INFO, TAG, "Starting Settings Service");
			
			try {
				if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
					/*
					 * The original com.android.server.am.ActivityManagerService.main() method
					 * will return the system context, which XposedBridge will have stored in param.getResult().
					 * This is why we inject this as an After Hook.
					 */
					mContext = (Context) param.getResult();
					
					/*
					 * Add this service to the service manager
					 */
					ReflectClass.forName("android.os.ServiceManager")
						.findMethod("addService", Match.BEST, String.class, IBinder.class)
						.invoke(Constants.SERVICE_MODULE_SETTINGS, SettingsService.this);
					
				} else {
					/*
					 * The original android.app.ActivityThread method will return a new instance of itself. 
					 * This instance contains the system context.
					 */
					mContext = (Context) ReflectClass.forReceiver(param.thisObject).findField("mSystemContext").getValue();
					
					/*
					 * Add this service to the service manager
					 */
					ReflectClass.forName("android.os.ServiceManager")
						.findMethod("addService", Match.BEST, String.class, IBinder.class, Boolean.TYPE)
						.invoke(Constants.SERVICE_MODULE_SETTINGS, SettingsService.this, true);
				}

				synchronized (oLogEntriesStatic) {
					for (String entry : oLogEntriesStatic) {
						mLogEntries.add(entry);
					}
				}
				
				oLogEntriesStatic.clear();
				oLogEntriesStatic = null;
				
				/*
				 * The service is now accessible
				 */
				mIsActive = true;
				
			} catch (ReflectException e) {
				Utils.log(Level.ERROR, TAG, e.getMessage(), e);
			}
		}
	};
	
	/**
	 * Hook that is called when the system is about to shut down
	 */
	private XC_MethodHook hook_onShutdown = new XC_MethodHook() {
		@Override
		protected final void beforeHookedMethod(final MethodHookParam param) {
			Utils.log(Level.INFO, TAG, "Stopping Settings Service");
			
			/*
			 * Save any changes to the settings data
			 */
			pokeAppPreferenceService(PokeType.SAVE_SETTINGS);
		}
	};
	
	/**
	 * Hook that is called when the system is done booting
	 */
	private XC_MethodHook hook_onReady = new XC_MethodHook() {
		@Override
		protected final void afterHookedMethod(final MethodHookParam param) {
			Utils.log(Level.INFO, TAG, "Finalizig Settings Service");

			try {
				/*
				 * Get the app uid for access check usage
				 */
				PackageInfo info = mContext.getPackageManager().getPackageInfo(Constants.PACKAGE_NAME, 0);
				
				mAppUid = info.applicationInfo.uid;
				mVersion = info.versionCode;
				
			} catch (NameNotFoundException e1) {}
			
			/*
			 * Restore the settings data
			 */
			pokeAppPreferenceService(PokeType.RESTORE_SETTINGS);
		}
	};
	
	/**
	 * Poke the main application preference service to handle preference file operations. 
	 * This service is needed to bypass SELinux restrictions in Lollipop+
	 * 
	 * @param poke
	 * 		 - Defines the type of operation to perform
	 */
	private boolean pokeAppPreferenceService(final PokeType poke) {
		/*
		 * Make sure that our application is the one being called.
		 */
		Intent intent = new Intent(Constants.SERVICE_APP_PREFERENCES);
		intent.setPackage(Constants.PACKAGE_NAME);
		
		ServiceConnection connection = new ServiceConnection() {
			@Override
			public void onServiceConnected(ComponentName name, IBinder binder) {
				Utils.log(Level.DEBUG, TAG, "Poking the Application preference service using type '" + (poke == PokeType.SAVE_SETTINGS ? "SAVE_SETTINGS" : "RESTORE_SETTINGS") + "'");
				
				IServicePreferences service = IServicePreferences.Stub.asInterface(binder);
				
				try {
					if (poke == PokeType.SAVE_SETTINGS) {
						service.writeSettingsData(mData);
						
					} else {
						mData = service.readSettingsData();
					}
					
					mIsReady = true;
					
				} catch (RemoteException e) {} finally {
					mContext.unbindService(this);
				}
			}

			@Override
			public void onServiceDisconnected(ComponentName name) {}
		};
		
		return mContext.bindService(intent, connection, Context.BIND_AUTO_CREATE);
	}
	
	/**
	 * Method to check the caller access to this service. 
	 * Everyone can get the information, but only those with access can change it.
	 */
	private boolean accessGranted() {
		/*
		 * By default we allow access to Android and our own module. Others will need to include our permission
		 */
		return Binder.getCallingUid() == mSystemUid || Binder.getCallingUid() == mAppUid || 
				mContext.checkCallingPermission(Constants.PERMISSION_SETTINGS_RW) == PackageManager.PERMISSION_GRANTED;
	}
	
	
	/**
	 * Internal method that is used to get content from the SettingsData container. 
	 * If the key does not exist, it will search for a similiar name and type in the 
	 * apps resources. 
	 * @return 
	 */
	private Object getDataValue(String key, Object defaultValue, int type) {
		if (mData.contains(key)) {
			Utils.log(Level.DEBUG, TAG, "Getting data value from key '" + key + "'");
			
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
					
				default:
					return defaultValue;
			}
		}
		
		try {
			Utils.log(Level.DEBUG, TAG, "Looking up data value with key '" + key + "' from Application resources");
			
			PackageManager manager = mContext.getPackageManager();
			Resources resources = manager.getResourcesForApplication(Constants.PACKAGE_NAME);
			Integer resourceId = resources.getIdentifier(key, Type.getIdentifier(type), Constants.PACKAGE_NAME);
			
			if (resourceId > 0) {
				switch (type) {
					case Type.STRING: 
						return resources.getString(resourceId);
						
					case Type.LIST:
						String[] array = resources.getStringArray(resourceId);
						List<String> list = new ArrayList<String>();
						
						for (int i=0; i < array.length; i++) {
							list.add(array[i]);
						}
						
						return list;
		
					case Type.BOOLEAN:
						return resources.getBoolean(resourceId);
		
					case Type.INTEGER:
						return resources.getInteger(resourceId);
				}
			} 
			
		} catch (NameNotFoundException e) { 
			Utils.log(Level.ERROR, TAG, e.getMessage(), e);
		}
		
		return defaultValue;
	}
	
	/**
	 * Internal method used to store values in the SettingsData container. 
	 * This also checks if the caller has RW permission to the preferences.
	 */
	private void setDataValue(String key, Object value, boolean preserve) {
		synchronized (mData) {
			Utils.log(Level.DEBUG, TAG, "Storing data value with key '" + key + "'");
			
			if (accessGranted()) {
				mData.put(key, value, preserve);
				
				/*
				 * Inform all managers that this key has changed
				 */
				broadcastSettingsChange(key);
			}
		}
	}
	
	/**
	 * Sends out a broadcast to all active settings handlers and informs 
	 * them about changes in the settings data collection.
	 */
	private void broadcastSettingsChange(String key) {
		synchronized(mListeners) {
			Utils.log(Level.DEBUG, TAG, "Broadcasting settings change on key '" + key + "'");
			
			Integer type = key != null && mData.contains(key) ? mData.type(key) : Type.UNKNOWN;
			
			for (BinderWatcher watchers : mListeners) {
				IBinder listener = watchers.getBinder();
				
				if (listener != null && listener.pingBinder()) {
					try {
						if (type == Type.UNKNOWN) {
							ISettingsListener.Stub.asInterface(listener).onPreferenceRemoved(key);
							
						} else {
							ISettingsListener.Stub.asInterface(listener).onPreferenceChanged(key, type);
						}
						
					} catch (RemoteException e) {}
				}
			}
		}
	}
	
	/**
	 * AIDL required method that returns the current service version. 
	 * If the app has just been updated this value will not match up with the new app version. 
	 */
	@Override
	public int version() {
		return mVersion;
	}
	
	/**
	 * AIDL required method that will return true once the service has been created and stored in the service manager. 
	 * This means that the service can be accessed, but not everything is ready yet. Preferences for an example has still not been loaded.
	 */
	@Override
	public boolean isActive() {
		return mIsActive;
	}
	
	/**
	 * AIDL required method that will return true once the service is fully operational
	 */
	@Override
	public boolean isReady() {
		return mIsReady;
	}
	
	/**
	 * This is used internally between this service and it's manager.
	 * It is used to update the information in the manager instances to reduce 
	 * IPC calls. 
	 */
	@Override
	public void addSettingsListener(ISettingsListener listener) throws RemoteException {
		synchronized(mListeners) {
			IBinder binder = listener.asBinder();
			
			for (BinderWatcher binderListener : mListeners) {
				if (binderListener.getBinder() == binder) {
					return;
				}
			}
			
			mListeners.add(new BinderWatcher(binder));
		}
	}
	
	/**
	 * Remove listeners from the list
	 */
	@Override
	public void removeSettingsListener(ISettingsListener listener) {
		synchronized(mListeners) {
			IBinder binder = listener.asBinder();
			BinderWatcher watcher = null;
			
			for (BinderWatcher binderListener : mListeners) {
				if (binderListener.getBinder() == binder) {
					watcher = binderListener; break;
				}
			}
			
			if (watcher != null) {
				mListeners.remove(watcher);
			}
		}
	}
	
	/**
	 * Redirects a broadcast to all active settings handlers.
	 * This allows direct communication between processes. 
	 */
	@Override
	public void sendSettingsBroadcast(String action, Bundle data) {
		synchronized(mListeners) {
			Utils.log(Level.DEBUG, TAG, "Sending settings broadcast using action '" + action + "'");
			
			for (BinderWatcher watchers : mListeners) {
				IBinder listener = watchers.getBinder();
				
				if (listener != null && listener.pingBinder()) {
					try {
						ISettingsListener.Stub.asInterface(listener).onSettingsBroadcast(action, data);
						
					} catch (RemoteException e) {}
				}
			}
		}
	}
	
	@Override
	public void addLogEntry(String entry) {
		synchronized (mLogEntries) {
			if (mLogEntries.size() > 150) {
				/*
				 * Truncate the list. We remove 15% of Constants.LOG_ENTRY_SIZE entries at a time to avoid having to do this each time this is called
				 */
				int truncate = (int) (Constants.LOG_ENTRY_SIZE * 0.15);
				
				for (int i=0; i < truncate; i++) {
					mLogEntries.remove(0);
				}
			}
			
			mLogEntries.add(entry);
		}
	}
	
	public static void addLogEntryStatic(String entry) {
		if (oLogEntriesStatic != null) {
			synchronized (oLogEntriesStatic) {
				/*
				 * This will run in two processes if everything goes as it should. 
				 * If not it might run in more, and deleting from one process will not delete anything in another. 
				 * So if this get's above 150 in a process, we don't add any more as this is not meant to be used permanently. 
				 * This is a temp container used by the LogcatMonitor until the service is active. It only works 
				 * because it starts running as root and then switches to run as system. The later will inherit from the first. 
				 */
				if (oLogEntriesStatic.size() < Constants.LOG_ENTRY_SIZE) {
					oLogEntriesStatic.add(entry);
				}
			}
		}
	}
	
	@Override
	public List<String> getLogEntries() {
		return mLogEntries;
	}
	
	/**
	 * Can be used by the log UI to get startup errors if the service was never started. 
	 */
	public static List<String> getLogEntriesStatic() {
		return oLogEntriesStatic;
	}

	
	/**
	 * Below are all of the methods used to work with the SettingsData container
	 */
	
	@Override
	public boolean savePreferences() {
		if (accessGranted()) {
			/*
			 * Save any changes to the settings data
			 */
			return pokeAppPreferenceService(PokeType.SAVE_SETTINGS);
		}
		
		return false;
	}
	
	@Override
	public boolean hasPreference(String key) {
		return mData.contains(key);
	}
	
	@Override
	public void deletePreference(String key) {
		synchronized (mData) {
			if (accessGranted()) {
				Utils.log(Level.DEBUG, TAG, "Deleting data value with key '" + key + "'");
				
				mData.remove(key);
				
				/*
				 * Inform all managers that this key has been removed
				 */
				broadcastSettingsChange(key);
			}
		}
	}
	
	@Override
	public boolean getBooleanPreference(String key, boolean defaultValue) {
		return (Boolean) getDataValue(key, defaultValue, Type.BOOLEAN);
	}
	
	@Override
	public void putBooleanPreference(String key, boolean value, boolean preserve) {
		setDataValue(key, value, preserve);
	}
	
	@Override
	public int getIntegerPreference(String key, int defaultValue) {
		return (Integer) getDataValue(key, defaultValue, Type.INTEGER);
	}
	
	@Override
	public void putIntegerPreference(String key, int value, boolean preserve) {
		setDataValue(key, value, preserve);
	}
	
	@Override
	public String getStringPreference(String key, String defaultValue) {
		return (String) getDataValue(key, defaultValue, Type.STRING);
	}
	
	@Override
	public void putStringPreference(String key, String value, boolean preserve) {
		setDataValue(key, value, preserve);
	}
	
	@SuppressWarnings("unchecked")
	@Override
	public List<String> getStringArrayPreference(String key, List<String> defaultValue) {
		return (List<String>) getDataValue(key, defaultValue, Type.LIST);
	}
	
	@Override
	public void putStringArrayPreference(String key, List<String> value, boolean preserve) {
		setDataValue(key, value, preserve);
	}
}
