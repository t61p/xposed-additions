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

import com.spazedog.xposed.additionsgb.backend.settings.SettingsService;
import com.spazedog.xposed.additionsgb.utils.Utils;
import com.spazedog.xposed.additionsgb.utils.Utils.Level;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.IXposedHookZygoteInit;
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam;

public final class XposedInjector implements IXposedHookZygoteInit, IXposedHookLoadPackage {
	public static final String TAG = XposedInjector.class.getName();
	
	@Override
	public void initZygote(IXposedHookZygoteInit.StartupParam startupParam) throws Throwable {
		Utils.log(Level.INFO, TAG, "Instantiating Xposed Additions");
		
		LogcatMonitor.init();
		SettingsService.init();
	}

	@Override
	public void handleLoadPackage(LoadPackageParam lpparam) throws Throwable {
		/*
		 * Hack to avoid calling ServiceManager while it is not ready. 
		 * Doing so will crash ServiceFlinger and the device will not boot. 
		 * 
		 * TODO: 
		 * 			1. Find a way to remove this hack
		 */
		LogcatMonitor.onLoadApplication();
		
		if (lpparam.packageName.equals("android")) {
			
		}
	}
}
