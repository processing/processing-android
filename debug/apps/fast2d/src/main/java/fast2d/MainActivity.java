package fast2d;

import android.os.Bundle;
import android.content.Intent;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.support.v7.app.AppCompatActivity;

import processing.android.PFragment;
import processing.android.CompatUtils;
import processing.core.PApplet;

public class MainActivity extends AppCompatActivity {
//  private int TEST = 1; // Basic self-intersecting polygon
//  private int TEST = 2; // Mouse controlled polygon
  private int TEST = 3; // Textured poly
//  private int TEST = 4; // Text rendering
//  private int TEST = 5; // Shapes benchmark
//  private int TEST = 6; // Duplicated vertex
//  private int TEST = 7; // User-defined contours
//  private int TEST = 8; // Primitive types
//  private int TEST = 9; // Arc test
//  private int TEST = 10; // Arc test
//  private int TEST = 11; // Load and display SVG
//  private int TEST = 12; // Filter test
//  private int TEST = 13; // Custom shader test

  private PApplet sketch;

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    FrameLayout frame = new FrameLayout(this);
    frame.setId(CompatUtils.getUniqueViewId());
    setContentView(frame, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                                                     ViewGroup.LayoutParams.MATCH_PARENT));


    if (TEST == 1) {
      sketch = new SketchBasicPoly();
    } else if (TEST == 2) {
      sketch = new SketchMousePoly();
    } else if (TEST == 3) {
      sketch = new SketchTexturedPoly();
    } else if (TEST == 4) {
      sketch = new SketchDisplayText();
    } else if (TEST == 5) {
      sketch = new SketchShapeBenchmark();
    } else if (TEST == 6) {
      sketch = new SketchDuplicatedVert();
    } else if (TEST == 7) {
      sketch = new SketchUserDefinedContours();
    } else if (TEST == 8) {
      sketch = new SketchPrimitiveTypes();
    } else if (TEST == 9) {
      sketch = new SketchArcTest();
    } else if (TEST == 10) {
      sketch = new SketchCurveTest();
    } else if (TEST == 11) {
      sketch = new SketchLoadDisplaySVG();
    } else if (TEST == 12) {
      sketch = new SketchFilterTest();
    } else if (TEST == 13) {
      sketch = new SketchCustomShader();
    }

    PFragment fragment = new PFragment(sketch);
    fragment.setView(frame, this);
  }

  @Override
  public void onRequestPermissionsResult(int requestCode, String permissions[], int[] grantResults) {
    if (sketch != null) {
      sketch.onRequestPermissionsResult(
      requestCode, permissions, grantResults);
    }
  }

  @Override
  public void onNewIntent(Intent intent) {
    if (sketch != null) {
      sketch.onNewIntent(intent);
    }
  }

  @Override
  public void onActivityResult(int requestCode, int resultCode, Intent data) {
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
}
