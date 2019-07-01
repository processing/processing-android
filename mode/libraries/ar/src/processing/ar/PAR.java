package processing.ar;

public interface PAR {
  static final int UNKNOWN       = -1;

  static final int PLANE_FLOOR   = 0;
  static final int PLANE_CEILING = 1;
  static final int PLANE_WALL    = 2;
  static final int POINT         = 3;

  static final int TRACKING  = 0;
  static final int PAUSED    = 1;
  static final int STOPPED   = 2;
}
