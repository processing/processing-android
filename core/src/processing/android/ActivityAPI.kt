/* -*- mode: java; c-basic-offset: 2; indent-tabs-mode: nil -*- */

/*
  Part of the Processing project - http://processing.org

  Copyright (c) 2016-17 The Processing Foundation

  This library is free software; you can redistribute it and/or
  modify it under the terms of the GNU Lesser General Public
  License version 2.1 as published by the Free Software Foundation.

  This library is distributed in the hope that it will be useful,
  but WITHOUT ANY WARRANTY; without even the implied warranty of
  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
  Lesser General Public License for more details.

  You should have received a copy of the GNU Lesser General
  Public License along with this library; if not, write to the
  Free Software Foundation, Inc., 59 Temple Place, Suite 330,
  Boston, MA  02111-1307  USA
*/

package processing.android

import android.app.FragmentManager
import android.content.Intent
import android.os.Bundle
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.Window;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo

/**
 * @author Aditya Rana
 * Methods that should be implemented in PApplet to maintain backward
 * compatibility with (some) functionality available from Activity/Fragment
 */
interface ActivityAPI {

    // Lifecycle events
    fun onCreate(savedInstanceState: Bundle?)
    fun onDestroy()
    fun onStart()
    fun onStop()
    fun onPause()
    fun onResume()

    // Activity and intent events
    fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?)
    fun onNewIntent(intent: Intent?)

    // Menu API
    fun onCreateOptionsMenu(menu: Menu?, inflater: MenuInflater?)
    fun onOptionsItemSelected(item: MenuItem?): Boolean
    fun onCreateContextMenu(menu: ContextMenu?, v: View?, menuInfo: ContextMenuInfo?)
    fun onContextItemSelected(item: MenuItem?): Boolean
    fun setHasOptionsMenu(hasMenu: Boolean)

    // IO events
    fun onBackPressed()

    // Activity management
    val fragmentManager: FragmentManager?
    val window: Window?
}