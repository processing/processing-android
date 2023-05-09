package arscene;

import android.Manifest;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.content.Intent;
import android.provider.Settings;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import processing.android.PFragment;
import processing.android.CompatUtils;
import processing.core.PApplet;

public class MainActivity extends AppCompatActivity {
  private static final int CAMERA_PERMISSION_CODE = 0;
  private static boolean CAMERA_PERMISSION_REQUESTED = false;
  private static final String CAMERA_PERMISSION = Manifest.permission.CAMERA;
  private static final String CAMERA_PERMISSION_MESSAGE = "Camera permission is needed to use AR";

  private PApplet sketch;

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    FrameLayout frame = new FrameLayout(this);
    frame.setId(CompatUtils.getUniqueViewId());
    setContentView(frame, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
        ViewGroup.LayoutParams.MATCH_PARENT));

    sketch = new Sketch();
    PFragment fragment = new PFragment(sketch);
    fragment.setView(frame, this);
  }

  @Override
  protected void onResume() {
    super.onResume();
    if (!hasCameraPermission()) requestCameraPermission();
  }

  @Override
  public void onRequestPermissionsResult(int requestCode, String permissions[], int[] grantResults) {
    super.onRequestPermissionsResult(requestCode, permissions, grantResults);
    if (!hasCameraPermission()) {
      Toast.makeText(this, CAMERA_PERMISSION_MESSAGE, Toast.LENGTH_LONG).show();
      if (!shouldShowRequestPermissionRationale()) {
        launchPermissionSettings();
      }
      finish();
    }

    if (sketch != null) {
      sketch.onRequestPermissionsResult(requestCode, permissions, grantResults);
    }

    CAMERA_PERMISSION_REQUESTED = false;
  }

  @Override
  public void onNewIntent(Intent intent) {
    super.onNewIntent(intent);
    if (sketch != null) {
      sketch.onNewIntent(intent);
    }
  }

  @Override
  public void onActivityResult(int requestCode, int resultCode, Intent data) {
    super.onActivityResult(requestCode, resultCode, data);
    if (sketch != null) {
      sketch.onActivityResult(requestCode, resultCode, data);
    }
  }

  @Override
  public void onBackPressed() {
    if (sketch != null) {
      sketch.onBackPressed();
    }
  }

  private boolean hasCameraPermission() {
    int res = ContextCompat.checkSelfPermission(this, CAMERA_PERMISSION);
    return res == PackageManager.PERMISSION_GRANTED;
  }

  private void requestCameraPermission() {
    if (!CAMERA_PERMISSION_REQUESTED) {
      CAMERA_PERMISSION_REQUESTED = true;
      ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA}, CAMERA_PERMISSION_CODE);
    }
  }

  private boolean shouldShowRequestPermissionRationale() {
    return ActivityCompat.shouldShowRequestPermissionRationale(this, CAMERA_PERMISSION);
  }

  private void launchPermissionSettings() {
    Intent intent = new Intent();
    intent.setAction(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
    intent.setData(Uri.fromParts("package", this.getPackageName(), null));
    this.startActivity(intent);
  }
}
