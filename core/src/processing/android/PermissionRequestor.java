package processing.android;

import android.app.Activity;
import android.os.Bundle;
import android.support.v4.app.ActivityCompat;
import android.support.v4.os.ResultReceiver;

public class PermissionRequestor extends Activity {
  public static final String KEY_RESULT_RECEIVER = "resultReceiver";
  public static final String KEY_PERMISSIONS = "permissions";
  public static final String KEY_GRANT_RESULTS = "grantResults";
  public static final String KEY_REQUEST_CODE = "requestCode";

  ResultReceiver resultReceiver;
  String[] permissions;
  int requestCode;

  @Override
  protected void onStart() {
    super.onStart();
    resultReceiver = this.getIntent().getParcelableExtra(KEY_RESULT_RECEIVER);
    permissions = this.getIntent().getStringArrayExtra(KEY_PERMISSIONS);
    requestCode = this.getIntent().getIntExtra(KEY_REQUEST_CODE, 0);
    ActivityCompat.requestPermissions(this, permissions, requestCode);
  }

  @Override
  public void onRequestPermissionsResult(int requestCode, String permissions[], int[] grantResults) {
    Bundle resultData = new Bundle();
    resultData.putStringArray(KEY_PERMISSIONS, permissions);
    resultData.putIntArray(KEY_GRANT_RESULTS, grantResults);
    resultReceiver.send(requestCode, resultData);
    finish();
  }
}