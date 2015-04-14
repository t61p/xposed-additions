/*
 * This file is part of the Xposed Additions Project: https://github.com/spazedog/xposed-additions
 *  
 * Copyright (c) 2014 Daniel Bergløv
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

import android.annotation.SuppressLint;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentTransaction;
import android.support.v4.view.GravityCompat;
import android.support.v4.widget.DrawerLayout;
import android.support.v4.widget.DrawerLayout.DrawerListener;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.TextView;
import com.spazedog.xposed.additionsgb.utils.abstracts.AbstractActivity;

public class ActivityMain extends AbstractActivity {
	
	private ViewGroup mActionBarNavigation;
	private ViewGroup mActionBarContent;
	private TextView mActionBarTitle;
	
	private DrawerLayout mDrawerLayout;
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		
		setContentView(R.layout.activity_main_layout);
		
		mActionBarNavigation = (ViewGroup) findViewById(R.id.toolbar_navigation);
		mActionBarContent = (ViewGroup) findViewById(R.id.toolbar_content);
		mActionBarTitle = (TextView) findViewById(R.id.toolbar_title);
		mDrawerLayout = (DrawerLayout) findViewById(R.id.drawer_layout);
		
		if (mDrawerLayout != null) {
			if (android.os.Build.VERSION.SDK_INT < 21) {
				mDrawerLayout.setDrawerShadow(R.drawable.drawer_shadow, GravityCompat.START);
			}
			
			mDrawerLayout.setDrawerListener(new DrawerListener(){
				@Override
				public void onDrawerClosed(View arg0) {
					sendMessage("activity.drawer_opened", false, true);
				}

				@Override
				public void onDrawerOpened(View arg0) {
					sendMessage("activity.drawer_opened", true, true);
				}

				@Override
				public void onDrawerSlide(View arg0, float arg1) {}

				@Override
				public void onDrawerStateChanged(int arg0) {}
			});
		}
	}
	
    @Override
    protected void onResume() {
    	super.onResume();
    	
    	if (mDrawerLayout != null) {
    		sendMessage("activity.drawer_opened", mDrawerLayout.isDrawerOpen(GravityCompat.START), true);
    	}
    }
    
    public Fragment getCurrentFragment() {
		return getSupportFragmentManager().findFragmentById(R.id.drawer_content_frame);
    }
	
	public void loadFragment(Fragment fragment, Boolean backStack) {
		FragmentManager manager = getSupportFragmentManager();
		String fragmentName = fragment.getClass().getSimpleName();
		
		if (!backStack || manager.findFragmentByTag(fragmentName) == null) {
			FragmentTransaction transaction = manager.beginTransaction();
			transaction.replace(R.id.drawer_content_frame, fragment, fragmentName);
			
			if (backStack) {
				transaction.addToBackStack(null);
				
			} else if (manager.getBackStackEntryCount() > 0) {
				manager.popBackStack(manager.getBackStackEntryAt(0).getId(), FragmentManager.POP_BACK_STACK_INCLUSIVE);
			}
			
			transaction.commit();
			
			if (mDrawerLayout != null) {
				mDrawerLayout.closeDrawers();
			}
		}
	}
	
	@Override
	public void setTitle(CharSequence title) {
		if (mActionBarTitle != null) {
			mActionBarTitle.setText(title);
		}
	}
	
	@Override
	public void onReceiveMessage(String message, Object data, Boolean sticky) {
		if ("internal.fragment_attachment".equals(message) || "internal.backstack_changed".equals(message)) {
			setupNavigation();
		}
	}
	
	@SuppressLint("NewApi")
	private void onNavigationClick(View view) {
		boolean activated = android.os.Build.VERSION.SDK_INT < 21 ? view.isSelected() : view.isActivated();
		
		if (!activated && mDrawerLayout != null) {
			mDrawerLayout.openDrawer(GravityCompat.START);
			
		} else if (activated) {
			FragmentManager manager = getSupportFragmentManager();
			manager.popBackStack();
		}
	}
	
	@SuppressLint("NewApi")
	private void setupNavigation() {
		FragmentManager manager = getSupportFragmentManager();
		
		if (mDrawerLayout != null || manager.getBackStackEntryCount() > 0) {
			View view = null;
			
			if (mActionBarNavigation.getChildCount() == 0) {
				LayoutInflater inflater = getLayoutInflater();
				view = inflater.inflate(R.layout.activity_main_menu_navigation, mActionBarNavigation, false);
				view.setOnClickListener(new OnClickListener(){
					@Override
					public void onClick(View v) {
						onNavigationClick(v);
					}
				});
				
				mActionBarNavigation.addView(view);
				
			} else {
				view = mActionBarNavigation.getChildAt(0);
			}

			if (android.os.Build.VERSION.SDK_INT < 21) {
				view.setSelected(manager.getBackStackEntryCount() > 0);
				
			} else {
				view.setActivated(manager.getBackStackEntryCount() > 0);
			}
			
			mActionBarNavigation.setVisibility(View.VISIBLE);
			
		} else {
			mActionBarNavigation.setVisibility(View.GONE);
		}
	}
	
	public void addMenuItem(View view) {
		mActionBarContent.addView(view);
	}
	
	public void removeMenuItem(View view) {
		mActionBarContent.removeView(view);
	}
}
