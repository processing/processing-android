package processing.android;

import android.content.Intent;
import android.os.Bundle;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;

public interface ActivityAPI {
  public void onCreate(Bundle savedInstanceState);
  public void onDestroy();

  public void onActivityResult(int requestCode, int resultCode, Intent data);
  public void onNewIntent(Intent intent);

  void onCreateOptionsMenu(Menu menu, MenuInflater inflater);
  boolean onOptionsItemSelected(MenuItem item);
  public void onCreateContextMenu(ContextMenu menu, View v, ContextMenuInfo menuInfo);
  public boolean onContextItemSelected(MenuItem item);
  public boolean onMenuItemClick(MenuItem item);
}
