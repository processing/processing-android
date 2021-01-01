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

package processing.android;

import android.content.Intent;
import android.os.Bundle;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.Window;
import android.app.FragmentManager;


// Methods that should be implemented in PApplet to maintain backward
// compatibility with (some) functionality available from Activity/Fragment
public interface ActivityAPI {
  // Lifecycle events
  public void onCreate(Bundle savedInstanceState);
  public void onDestroy();
  public void onStart();
  public void onStop();
  public void onPause();
  public void onResume();

  // Activity and intent events
  public void onActivityResult(int requestCode, int resultCode, Intent data);
  public void onNewIntent(Intent intent);

  // Menu API
  public void onCreateOptionsMenu(Menu menu, MenuInflater inflater);
  public boolean onOptionsItemSelected(MenuItem item);
  public void onCreateContextMenu(ContextMenu menu, View v, ContextMenuInfo menuInfo);
  public boolean onContextItemSelected(MenuItem item);
  public void setHasOptionsMenu(boolean hasMenu);

  // IO events
  public void onBackPressed();

  // Activity management
  public FragmentManager getFragmentManager();
  public Window getWindow();
}
