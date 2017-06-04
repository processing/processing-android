package processing.android;

import java.util.concurrent.atomic.AtomicInteger;
import android.annotation.SuppressLint;
import android.os.Build;
import android.view.View;

// An utility class to generate unique View ID's. Needed until minimum API level
// in raised to 17, where we can just use View.generateViewId().
// Largely based on fantouch code at http://stackoverflow.com/a/21000252
public class ViewIdGenerator {
  // Start at 15,000,000, taking into account the comment from Singed
  // http://stackoverflow.com/a/39307421
  private static final AtomicInteger nextId = new AtomicInteger(15000000);

  @SuppressLint("NewApi")
  public static int getUniqueId() {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.JELLY_BEAN_MR1) {
      for (;;) {
        final int result = nextId.get();
        // aapt-generated IDs have the high byte nonzero; clamp to the range under that.
        int newValue = result + 1;
        if (newValue > 0x00FFFFFF) newValue = 1; // Roll over to 1, not 0.
        if (nextId.compareAndSet(result, newValue)) {
          return result;
        }
      }
    } else {
      return View.generateViewId();
    }
  }
}
