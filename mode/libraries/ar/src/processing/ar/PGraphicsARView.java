package processing.ar;

import android.view.SurfaceHolder;
import processing.android.AppComponent;
import processing.core.PGraphics;
import processing.core.PSurface;

public class PGraphicsARView extends PGraphicsAR {
    @Override
    public PSurface createSurface(AppComponent appComponent, SurfaceHolder surfaceHolder, boolean b) {
        if (b) pgl.resetFBOLayer();
        PGraphics.showWarning("Reached - 1");
        return new PSurfaceAR(this, appComponent, surfaceHolder);
    }
}
